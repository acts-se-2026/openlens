import asyncio
import os

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool

from client import analyze_image
from database import create_user, init_db, refund_tokens, try_decrement_tokens, user_exists
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


@app.on_event("startup")
def _startup():
    init_db()


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    # Kratos owns identity; the wallet is keyed on the identity id.
    user_id = identity["id"]
    image_bytes = await file.read()  # read before charging so a bad upload never costs tokens

    # Auto-provision: first authenticated scan creates the wallet with a starting balance.
    if not await run_in_threadpool(user_exists, user_id):
        await run_in_threadpool(create_user, user_id, STARTING_TOKENS)

    cost = MODEL_COSTS.get(model, DEFAULT_COST)
    if cost > 0 and not await run_in_threadpool(try_decrement_tokens, user_id, cost):
        raise HTTPException(status_code=402, detail="Not enough tokens")

    try:
        if detect_main_area is None:
            model_result = await run_in_threadpool(analyze_image, image_bytes, model)
            bounding_box_result = {"corners": None, "detected_objects": []}
        else:
            model_result, bounding_box_result = await asyncio.gather(
                run_in_threadpool(analyze_image, image_bytes, model),
                run_in_threadpool(detect_main_area, image_bytes),
            )
    except Exception:
        # A failed scan must not cost the user — hand the tokens back.
        if cost > 0:
            await run_in_threadpool(refund_tokens, user_id, cost)
        raise

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
    }
