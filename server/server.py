import asyncio
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
from kratos import get_current_identity

try:
    from bounding_box import detect_main_area
except Exception:
    detect_main_area = None


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
    user_id = identity["id"]
    await _ensure_provisioned(user_id)
    return {"balance": await run_in_threadpool(get_token_balance, user_id)}


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    language: str = Form(DEFAULT_LANGUAGE),  # target language for the translated result
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    # Kratos owns identity; the wallet is keyed on the identity id.
    user_id = identity["id"]
    image_bytes = await file.read()  # read before charging so a bad upload never costs tokens

    # Auto-provision: first authenticated scan creates the wallet with a starting balance.
    await _ensure_provisioned(user_id)

    cost = MODEL_COSTS.get(model, DEFAULT_COST)
    if cost > 0 and not await run_in_threadpool(try_decrement_tokens, user_id, cost):
        raise HTTPException(status_code=402, detail="Not enough tokens")

    try:
        if detect_main_area is None:
            model_result = await run_in_threadpool(analyze_image, image_bytes, model, language)
            bounding_box_result = {"corners": None, "detected_objects": []}
        else:
            model_result, bounding_box_result = await asyncio.gather(
                run_in_threadpool(analyze_image, image_bytes, model, language),
                run_in_threadpool(detect_main_area, image_bytes),
            )
    except Exception:
        # A failed scan must not cost the user — hand the tokens back.
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