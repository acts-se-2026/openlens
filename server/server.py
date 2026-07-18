import asyncio
import time

from fastapi import Depends, FastAPI, File, Form, UploadFile
from fastapi.concurrency import run_in_threadpool

from client import analyze_image
from kratos import get_current_identity
from search_client import search_related_content

try:
    from bounding_box import detect_main_area
except Exception:
    detect_main_area = None


app = FastAPI()


def safe_related_search(image_bytes):
    try:
        return search_related_content(
            image_bytes,
            4,
        )

    except Exception as error:
        print(f"Web search error: {error}")

        return {
            "images": [],
            "links": [],
        }


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),
):
    request_start = time.perf_counter()
    image_bytes = await file.read()

    if detect_main_area is None:
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

        bounding_box_result = {
            "corners": None,
            "detected_objects": [],
        }

    else:
        (
            model_result,
            bounding_box_result,
            related_content,
        ) = await asyncio.gather(
            run_in_threadpool(
                analyze_image,
                image_bytes,
                model,
            ),
            run_in_threadpool(
                detect_main_area,
                image_bytes,
            ),
            run_in_threadpool(
                safe_related_search,
                image_bytes,
            ),
        )

    processing_time = (
        time.perf_counter() - request_start
    )

    print(
        "Image analysis, bounding box and web search "
        f"completed in {processing_time:.2f} seconds."
    )

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
        "SearchQuery": (
            "Generated directly from the uploaded image"
        ),
        "RelatedImages": related_content["images"],
        "RelatedLinks": related_content["links"],
        "ProcessingTime": round(
            processing_time,
            2,
        ),
    }