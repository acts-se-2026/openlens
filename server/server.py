import asyncio

from fastapi import Depends, FastAPI, File, Form, UploadFile
from fastapi.concurrency import run_in_threadpool

from bounding_box import detect_main_area
from client import analyze_image
from kratos import get_current_identity


app = FastAPI()


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    image_bytes = await file.read()

    model_result, bounding_box_result = await asyncio.gather(
        run_in_threadpool(analyze_image, image_bytes, model),
        run_in_threadpool(detect_main_area, image_bytes),
    )

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
    }
