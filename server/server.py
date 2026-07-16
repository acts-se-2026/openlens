from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, File, UploadFile

from .auth import router as auth_router
from .client import analyze_image
from .db import init_db
from .models import User
from .security import get_current_user


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()  # create tables on startup
    yield


app = FastAPI(lifespan=lifespan)
app.include_router(auth_router)


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),  # gated: requires a valid access token
):
    image_bytes = await file.read()
    result = analyze_image(image_bytes)

    return {
    "Heading": result["heading"],
    "Body": result["body"]
    }
