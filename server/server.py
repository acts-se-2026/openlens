import asyncio
import json
import os

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool

import config
from client import analyze_image, follow_up_question
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

try:
    from bounding_box import detect_main_area, detect_objects
except Exception:
    detect_main_area = None
    detect_objects = None

# A conversation can only go this many follow-up questions deep before we cut it off.
MAX_FOLLOW_UP_QUESTIONS = 3


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

# Tokens granted to a wallet the first time we see a Kratos identity.
STARTING_TOKENS = int(os.getenv("STARTING_TOKENS", "100"))

# Wallet cost per scan tier. `free` is free; paid tiers are metered.
# Unknown tiers fall back to DEFAULT_COST so a new tier can't accidentally be free.
MODEL_COSTS = {"free": 0, "fast": 1, "deep": 3}
DEFAULT_COST = 1

# Default language if the client doesn't specify one.
DEFAULT_LANGUAGE = "english"


@app.on_event("startup")
def _startup():
    init_db()
    if config.DEV_TEST_TOKEN:
        # Loud on purpose: this means the Kratos auth bypass is live. Fine locally, never in prod.
        print("WARNING: DEV_TEST_TOKEN is set — Kratos auth bypass ENABLED. Do not use in production.")


async def _ensure_provisioned(user_id: str) -> None:
    """Create the wallet with a starting balance the first time we see this identity."""
    if not await run_in_threadpool(user_exists, user_id):
        await run_in_threadpool(create_user, user_id, STARTING_TOKENS)


@app.get("/balance")
async def balance(identity: dict = Depends(get_current_identity)):
    """Current wallet balance for the caller. Seeds a wallet on first touch so a freshly
    registered user reads their starting balance instead of a 404. The app calls this once to
    seed its counter; after that it reads the balance riding along on each scan response."""
    user_id = identity["id"]
    await _ensure_provisioned(user_id)
    return {"balance": await run_in_threadpool(get_token_balance, user_id)}


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
    language: str = Form(DEFAULT_LANGUAGE),  # target language for the translated result
    # Optional normalized [x1, y1, x2, y2] region. When present, the image is cropped to that
    # object (plus context padding) and only the crop is analyzed — the "focus on this box" re-scan.
    region: str = Form(None),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    # Kratos owns identity; the wallet is keyed on the identity id.
    user_id = identity["id"]
    image_bytes = await file.read()  # read before charging so a bad upload never costs tokens
    parsed_region = _parse_region(region)

    # Auto-provision: first authenticated scan creates the wallet with a starting balance.
    await _ensure_provisioned(user_id)

    cost = MODEL_COSTS.get(model, DEFAULT_COST)
    if cost > 0 and not await run_in_threadpool(try_decrement_tokens, user_id, cost):
        raise HTTPException(status_code=402, detail="Not enough tokens")

    try:
        if parsed_region is not None:
            # Focused re-scan: analyze just the selected region (plus padding). The app already
            # holds the per-object boxes from /detect, so there's nothing to detect again here.
            image_bytes = await run_in_threadpool(crop_to_region, image_bytes, parsed_region)
            model_result = await run_in_threadpool(analyze_image, image_bytes, model, language)
            bounding_box_result = {"corners": None, "detected_objects": []}
        elif detect_main_area is None:
            # Whole-image scan, no bounding-box detector available.
            model_result = await run_in_threadpool(analyze_image, image_bytes, model, language)
            bounding_box_result = {"corners": None, "detected_objects": []}
        else:
            # Whole-image scan: analyze and locate the main area concurrently.
            model_result, bounding_box_result = await asyncio.gather(
                run_in_threadpool(analyze_image, image_bytes, model, language),
                run_in_threadpool(detect_main_area, image_bytes),
            )
    except Exception:

        if cost > 0:
            await run_in_threadpool(refund_tokens, user_id, cost)
        raise

    # The post-charge balance rides along in the response, so the app never needs a separate
    # /balance call after a scan — it just reads this field.
    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
        "Balance": await run_in_threadpool(get_token_balance, user_id),
    }


@app.post("/follow_up")
async def follow_up(
    question: str = Form(...),
    history: str = Form(""),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):

    messages = json.loads(history) if history else []

    user_questions = [
        message for message in messages
        if message["role"] == "user"
    ]

    if len(user_questions) >= MAX_FOLLOW_UP_QUESTIONS:
        return {
            "error": "Maximum number of questions reached."  # offer paying for more?
        }

    answer = await run_in_threadpool(follow_up_question, history, question, model)

    return {
        "answer": answer
    }
