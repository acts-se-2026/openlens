import asyncio
import time

from fastapi import Depends, FastAPI, File, Form, UploadFile
from fastapi.concurrency import run_in_threadpool

from client import analyze_image
from kratos import get_current_identity
from search_client import search_related_content

try:
    from bounding_box import detect_main_area
except Exception as import_error:
    print(f"Bounding box disabled: {import_error}")
    detect_main_area = None


app = FastAPI()


def empty_bounding_box():
    return {
        "corners": None,
        "detected_objects": [],
    }


def safe_bounding_box(image_bytes):
    if detect_main_area is None:
        return empty_bounding_box()

    try:
        return detect_main_area(image_bytes)
    except Exception as error:
        print(f"Bounding box error: {error}")
        return empty_bounding_box()


def safe_related_search(image_bytes):
    try:
        return search_related_content(image_bytes, limit=4)
    except Exception as error:
        print(f"Similar image search disabled for this request: {error}")
        return {
            "images": [],
            "links": [],
            "engine": "pd12m-siglip2",
            "status": "unavailable",
            "error": str(error),
        }


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),
):
    request_start = time.perf_counter()
    image_bytes = await file.read()

    if not image_bytes:
        return {
            "Heading": "Empty image",
            "Body": "No image data was uploaded.",
            "BoundingBox": None,
            "DetectedObjects": [],
            "SimilarImages": [],
            "RelatedImages": [],
            "RelatedLinks": [],
            "SearchEngine": "pd12m-siglip2",
            "SearchStatus": "unavailable",
            "SearchError": "The uploaded file was empty.",
            "ProcessingTime": 0,
        }

    model_result, bounding_box_result, related_result = await asyncio.gather(
        run_in_threadpool(analyze_image, image_bytes, model),
        run_in_threadpool(safe_bounding_box, image_bytes),
        run_in_threadpool(safe_related_search, image_bytes),
    )

    similar_images = related_result["images"]
    processing_time = round(time.perf_counter() - request_start, 2)

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
        "SimilarImages": similar_images,
        "RelatedImages": similar_images,
        "RelatedLinks": related_result["links"],
        "SearchEngine": related_result["engine"],
        "SearchStatus": related_result["status"],
        "SearchError": related_result["error"],
        "ProcessingTime": processing_time,
    }
