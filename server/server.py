import asyncio
import inspect
import io
import json
import os
import time

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from PIL import Image, ImageOps

import config
from client import analyze_image
from database import (
    create_user,
    get_token_balance,
    init_db,
    refund_tokens,
    try_decrement_tokens,
    user_exists,
)
from kratos import get_current_identity
from search_client import search_related_content

try:
    from client import follow_up_question
except ImportError:
    follow_up_question = None

try:
    from bounding_box import detect_main_area
except Exception as error:
    print(f"Main-area detection disabled: {error}")
    detect_main_area = None

try:
    from bounding_box import detect_objects
except (ImportError, AttributeError):
    detect_objects = None

try:
    from image_prep import crop_to_region as imported_crop_to_region
except ImportError:
    imported_crop_to_region = None


app = FastAPI()

STARTING_TOKENS = int(os.getenv("STARTING_TOKENS", "100"))
MODEL_COSTS = {
    "free": 0,
    "fast": 1,
    "balanced": 1,
    "deep": 3,
}
DEFAULT_COST = 1
DEFAULT_LANGUAGE = "english"
MAX_FOLLOW_UP_QUESTIONS = 3


@app.on_event("startup")
def _startup():
    init_db()

    if config.DEV_TEST_TOKEN:
        print(
            "WARNING: DEV_TEST_TOKEN is set - "
            "Kratos auth bypass ENABLED. "
            "Do not use in production."
        )


async def _ensure_provisioned(user_id: str) -> None:
    if not await run_in_threadpool(user_exists, user_id):
        await run_in_threadpool(create_user, user_id, STARTING_TOKENS)


def _parse_region(region):
    if not region:
        return None

    try:
        values = json.loads(region)
    except (ValueError, TypeError):
        return None

    if not isinstance(values, (list, tuple)) or len(values) != 4:
        return None

    try:
        x1, y1, x2, y2 = (float(value) for value in values)
    except (ValueError, TypeError):
        return None

    if not all(0.0 <= value <= 1.0 for value in (x1, y1, x2, y2)):
        return None

    if x2 <= x1 or y2 <= y1:
        return None

    return x1, y1, x2, y2


def _fallback_crop_to_region(image_bytes, region):
    image = Image.open(io.BytesIO(image_bytes))
    image = ImageOps.exif_transpose(image).convert("RGB")
    width, height = image.size
    x1, y1, x2, y2 = region

    cropped = image.crop(
        (
            round(x1 * width),
            round(y1 * height),
            round(x2 * width),
            round(y2 * height),
        )
    )

    output = io.BytesIO()
    cropped.save(output, format="JPEG", quality=92, optimize=True)
    return output.getvalue()


def _crop_to_region(image_bytes, region):
    if imported_crop_to_region is not None:
        return imported_crop_to_region(image_bytes, region)

    return _fallback_crop_to_region(image_bytes, region)


def _analyze_image(image_bytes, model, language):
    parameters = inspect.signature(analyze_image).parameters

    if "language" in parameters:
        return analyze_image(image_bytes, model, language)

    return analyze_image(image_bytes, model)


def _safe_related_search(image_bytes):
    try:
        return search_related_content(image_bytes, count=4)
    except Exception as error:
        print(f"Related-content search failed: {error}")
        return {
            "query": "",
            "images": [],
            "links": [],
        }


def _empty_bounding_box():
    return {
        "corners": None,
        "detected_objects": [],
    }


def _safe_detect_main_area(image_bytes):
    if detect_main_area is None:
        return _empty_bounding_box()

    try:
        return detect_main_area(image_bytes)
    except Exception as error:
        print(f"Bounding-box detection failed: {error}")
        return _empty_bounding_box()


def _map_corners_to_original(corners, region):
    if not corners or region is None:
        return corners

    x1, y1, x2, y2 = region
    region_width = x2 - x1
    region_height = y2 - y1

    mapped = {}

    for name, point in corners.items():
        mapped[name] = {
            "x": round(x1 + float(point["x"]) * region_width, 6),
            "y": round(y1 + float(point["y"]) * region_height, 6),
        }

    return mapped


@app.get("/balance")
async def balance(identity: dict = Depends(get_current_identity)):
    user_id = identity["id"]
    await _ensure_provisioned(user_id)

    return {
        "balance": await run_in_threadpool(get_token_balance, user_id)
    }


@app.post("/detect")
async def detect(
    file: UploadFile = File(...),
    identity: dict = Depends(get_current_identity),
):
    image_bytes = await file.read()

    if not image_bytes:
        raise HTTPException(status_code=400, detail="The uploaded image is empty")

    if detect_objects is not None:
        objects = await run_in_threadpool(detect_objects, image_bytes)
        return {"objects": objects}

    main_area = await run_in_threadpool(
        _safe_detect_main_area,
        image_bytes,
    )

    if main_area["corners"] is None:
        return {"objects": []}

    labels = main_area.get("detected_objects") or []

    return {
        "objects": [
            {
                "label": ", ".join(labels) or "main area",
                "confidence": None,
                "corners": main_area["corners"],
            }
        ]
    }


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    language: str = Form(DEFAULT_LANGUAGE),
    region: str = Form(None),
    identity: dict = Depends(get_current_identity),
):
    request_start = time.perf_counter()
    user_id = identity["id"]
    original_image_bytes = await file.read()

    if not original_image_bytes:
        raise HTTPException(status_code=400, detail="The uploaded image is empty")

    parsed_region = _parse_region(region)
    await _ensure_provisioned(user_id)

    cost = MODEL_COSTS.get(model, DEFAULT_COST)

    if cost > 0 and not await run_in_threadpool(
        try_decrement_tokens,
        user_id,
        cost,
    ):
        raise HTTPException(status_code=402, detail="Not enough tokens")

    try:
        analysis_image_bytes = original_image_bytes

        if parsed_region is not None:
            analysis_image_bytes = await run_in_threadpool(
                _crop_to_region,
                original_image_bytes,
                parsed_region,
            )

        model_result, bounding_box_result = await asyncio.gather(
            run_in_threadpool(
                _analyze_image,
                analysis_image_bytes,
                model,
                language,
            ),
            run_in_threadpool(
                _safe_detect_main_area,
                analysis_image_bytes,
            ),
        )
    except Exception:
        if cost > 0:
            await run_in_threadpool(refund_tokens, user_id, cost)
        raise

    bounding_box_result["corners"] = _map_corners_to_original(
        bounding_box_result.get("corners"),
        parsed_region,
    )

    processing_time = time.perf_counter() - request_start

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result.get("detected_objects", []),
        "ProcessingTime": round(processing_time, 2),
        "Balance": await run_in_threadpool(
            get_token_balance,
            user_id,
        ),
    }


@app.post("/related")
async def related(
    file: UploadFile = File(...),
    identity: dict = Depends(get_current_identity),
):
    # Related image/link search, split out of /image_to_model so it never blocks the scan result.
    # It's the slow part (two model calls + page scraping); the app fetches it separately/lazily.
    image_bytes = await file.read()

    if not image_bytes:
        raise HTTPException(status_code=400, detail="The uploaded image is empty")

    result = await run_in_threadpool(_safe_related_search, image_bytes)

    return {
        "SearchQuery": result["query"],
        "RelatedImages": result["images"],
        "RelatedLinks": result["links"],
    }


@app.post("/follow_up")
async def follow_up(
    question: str = Form(...),
    history: str = Form(""),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),
):
    if follow_up_question is None:
        raise HTTPException(
            status_code=503,
            detail="follow_up_question is not available in client.py",
        )

    user_id = identity["id"]
    await _ensure_provisioned(user_id)

    try:
        messages = json.loads(history) if history else []
    except json.JSONDecodeError:
        raise HTTPException(
            status_code=400,
            detail="history must be a valid JSON array",
        )

    if not isinstance(messages, list):
        raise HTTPException(
            status_code=400,
            detail="history must be a JSON array",
        )

    user_questions = [
        message
        for message in messages
        if isinstance(message, dict) and message.get("role") == "user"
    ]

    if len(user_questions) >= MAX_FOLLOW_UP_QUESTIONS:
        raise HTTPException(
            status_code=429,
            detail="Maximum number of questions reached",
        )

    answer = await run_in_threadpool(
        follow_up_question,
        history,
        question,
        model,
    )

    return {
        "answer": answer,
        "remaining_questions": (
            MAX_FOLLOW_UP_QUESTIONS - len(user_questions) - 1
        ),
    }
