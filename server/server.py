import asyncio
import os

import httpx
from fastapi import FastAPI, File, Form, UploadFile, HTTPException, Security
from fastapi.concurrency import run_in_threadpool
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from bounding_box import detect_main_area
from client import analyze_image
from database import init_db, try_decrement_tokens, refund_tokens, user_exists


app = FastAPI()
bearer_scheme = HTTPBearer()

# price of the tokens for models
MODEL_COSTS = {
    "free": 1,
    "pro": 3,
}

# change with a real path
APP_SERVER_VERIFY_URL = os.getenv("APP_SERVER_VERIFY_URL")
#have to fill out with real command instead of id
USER_ID_FIELD = os.getenv("APP_SERVER_USER_ID_FIELD", "id")

if not APP_SERVER_VERIFY_URL:
    raise RuntimeError("APP_SERVER_VERIFY_URL is not in .env")
async def verify_jwt(token: str) -> str:
    async with httpx.AsyncClient(timeout=20) as client:
        try:
            response = await client.post(
                APP_SERVER_VERIFY_URL,
                headers={"Authorization": f"Bearer {token}"},
            )
        except httpx.RequestError as e:
            raise HTTPException(status_code=503, detail=f"Auth server unreachable: {e}")

    if response.status_code == 401:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    if response.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"Unexpected response from auth server: {response.status_code}",
        )

    username = response.json().get(USER_ID_FIELD)
    if not username:
        raise HTTPException(
            status_code=502,
            detail=f"Auth server response missing '{USER_ID_FIELD}' field",
        )

    return username
@app.on_event("startup")
def startup():
    init_db()
    #OLD SERVER

@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    credentials: HTTPAuthorizationCredentials = Security(bearer_scheme),
):
    token = credentials.credentials

    # Calling server to verify the token
    user_id = await verify_jwt(token)

    # checking wheter user exist
    if not await run_in_threadpool(user_exists, user_id):
        raise HTTPException(status_code=404, detail="User not found in token database")

    cost = MODEL_COSTS.get(model, 1)
    has_enough = await run_in_threadpool(try_decrement_tokens, user_id, cost)
    if not has_enough:
        raise HTTPException(status_code=402, detail="Not enough tokens")

    image_bytes = await file.read()

    try:
        model_result, bounding_box_result = await asyncio.gather(
            run_in_threadpool(analyze_image, image_bytes, model),
            run_in_threadpool(detect_main_area, image_bytes),
        )
    except Exception:
        # if call does not work it returns tokens to the model
        await run_in_threadpool(refund_tokens, user_id, cost)
        raise

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
    }
