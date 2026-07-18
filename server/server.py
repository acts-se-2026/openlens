import asyncio
import os
import time

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
from search_client import search_related_content

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


def safe_related_search(image_bytes):
    try:
        return search_related_content(image_bytes, count=4)
    except Exception as error:
        print(f"Related-content search failed: {error}")
        return {"query": "", "images": [], "links": []}


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    request_start = time.perf_counter()

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
            model_result, related_content = await asyncio.gather(
                run_in_threadpool(analyze_image, image_bytes, model),
                run_in_threadpool(safe_related_search, image_bytes),
            )
            bounding_box_result = {"corners": None, "detected_objects": []}
        else:
            model_result, bounding_box_result, related_content = await asyncio.gather(
                run_in_threadpool(analyze_image, image_bytes, model),
                run_in_threadpool(detect_main_area, image_bytes),
                run_in_threadpool(safe_related_search, image_bytes),
            )
    except Exception:
        # A failed scan must not cost the user — hand the tokens back.
        if cost > 0:
            await run_in_threadpool(refund_tokens, user_id, cost)
        raise

    processing_time = time.perf_counter() - request_start

    # The post-charge balance rides along in the response, so the app never needs a separate
    # /balance call after a scan — it just reads this field.
    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
        "SearchQuery": related_content["query"],
        "RelatedImages": related_content["images"],
        "RelatedLinks": related_content["links"],
        "ProcessingTime": round(processing_time, 2),
        "Balance": await run_in_threadpool(get_token_balance, user_id),
    }
