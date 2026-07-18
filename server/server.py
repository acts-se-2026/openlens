import asyncio
import json
import os

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool

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
from image_prep import crop_to_region
from kratos import get_current_identity
from search_client import search_related_content

try:
    from bounding_box import detect_objects
except Exception:
    detect_objects = None


def _parse_region(region):
    """Parse the optional `region` form field — a JSON array [x1, y1, x2, y2] of normalized 0..1
    coordinates — into a tuple, or None if absent or malformed. A malformed region degrades to a
    whole-image scan rather than failing the request."""
    if not region:
        return None
    try:
        values = json.loads(region)
    except (ValueError, TypeError):
        return None
    if not isinstance(values, (list, tuple)) or len(values) != 4:
        return None
    try:
        return tuple(float(v) for v in values)
    except (ValueError, TypeError):
        return None


app = FastAPI()

STARTING_TOKENS = int(os.getenv("STARTING_TOKENS", "100"))

MODEL_COSTS = {
    "free": 0,
    "fast": 1,
    "deep": 3,
}

DEFAULT_COST = 1


@app.on_event("startup")
def _startup():
    init_db()

    if config.DEV_TEST_TOKEN:
        print(
            "WARNING: DEV_TEST_TOKEN is set — "
            "Kratos auth bypass ENABLED. "
            "Do not use in production."
        )


async def _ensure_provisioned(user_id: str) -> None:
    if not await run_in_threadpool(user_exists, user_id):
        await run_in_threadpool(
            create_user,
            user_id,
            STARTING_TOKENS,
        )


@app.get("/balance")
async def balance(
    identity: dict = Depends(get_current_identity),
):
    user_id = identity["id"]

    await _ensure_provisioned(user_id)

    return {
        "balance": await run_in_threadpool(
            get_token_balance,
            user_id,
        )
    }


def safe_related_search(image_bytes):
    try:
        return search_related_content(
            image_bytes,
            count=4,
        )
    except Exception as error:
        print(f"Related-content search failed: {error}")

        return {
            "query": "",
            "images": [],
            "links": [],
        }


@app.post("/detect")
async def detect(
    file: UploadFile = File(...),
    identity: dict = Depends(get_current_identity),
):
    image_bytes = await file.read()

    if detect_objects is None:
        return {"objects": []}

    objects = await run_in_threadpool(
        detect_objects,
        image_bytes,
    )

    return {"objects": objects}


@app.post("/detect")
async def detect(
    file: UploadFile = File(...),
    identity: dict = Depends(get_current_identity),  # gated, but no token charge — detection is free
):
    """Fast, free object detection: the per-object boxes the capture screen draws as tappable
    targets. No LLM and no token charge. Returns an empty list when the detector isn't available
    (YOLO not loaded) or finds nothing, so the app can fall back to a whole-image scan."""
    image_bytes = await file.read()
    if detect_objects is None:
        return {"objects": []}
    objects = await run_in_threadpool(detect_objects, image_bytes)
    return {"objects": objects}


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    # Optional normalized [x1, y1, x2, y2] region. When present, the image is cropped to that
    # object (plus context padding) and only the crop is analyzed — the "focus on this box" re-scan.
    region: str = Form(None),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    user_id = identity["id"]
    image_bytes = await file.read()
    parsed_region = _parse_region(region)

    await _ensure_provisioned(user_id)

    cost = MODEL_COSTS.get(model, DEFAULT_COST)

    if cost > 0 and not await run_in_threadpool(
        try_decrement_tokens,
        user_id,
        cost,
    ):
        raise HTTPException(
            status_code=402,
            detail="Not enough tokens",
        )

    try:
        if parsed_region is not None:
            image_bytes = await run_in_threadpool(
                crop_to_region,
                image_bytes,
                parsed_region,
            )

        model_result, related_content = await asyncio.gather(
            run_in_threadpool(
                analyze_image,
                image_bytes,
                model,
            ),
            run_in_threadpool(
                safe_related_search,
                image_bytes,
            ),
        )

    except Exception:
        if cost > 0:
            await run_in_threadpool(
                refund_tokens,
                user_id,
                cost,
            )

        raise

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "SearchQuery": related_content["query"],
        "RelatedImages": related_content["images"],
        "RelatedLinks": related_content["links"],
        "Balance": await run_in_threadpool(
            get_token_balance,
            user_id,
        ),
    }
