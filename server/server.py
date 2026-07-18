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


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    model: str = Form("free"),
    identity: dict = Depends(get_current_identity),
):
    request_start = time.perf_counter()
    image_bytes = await file.read()

    model_task = asyncio.create_task(
        run_in_threadpool(
            analyze_image,
            image_bytes,
            model,
        )
    )

    search_task = None

    if detect_main_area is None:
        bounding_box_result = {
            "corners": None,
            "detected_objects": [],
        }

    else:
        bounding_box_task = asyncio.create_task(
            run_in_threadpool(
                detect_main_area,
                image_bytes,
            )
        )

        bounding_box_result = await bounding_box_task

        detected_objects = list(
            dict.fromkeys(
                bounding_box_result["detected_objects"]
            )
        )

        if detected_objects:
            search_query = " ".join(
                detected_objects[:4]
            )

            search_task = asyncio.create_task(
                run_in_threadpool(
                    search_related_content,
                    search_query,
                    4,
                )
            )

    model_result = await model_task

    if search_task is None:
        search_query = model_result["heading"]

        search_task = asyncio.create_task(
            run_in_threadpool(
                search_related_content,
                search_query,
                4,
            )
        )

    try:
        related_content = await search_task

    except Exception as error:
        print(f"Web search error: {error}")

        related_content = {
            "images": [],
            "links": [],
        }

    total_time = time.perf_counter() - request_start

    print(
        f"Image analysis and web search completed "
        f"in {total_time:.2f} seconds."
    )

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
        "SearchQuery": search_query,
        "RelatedImages": related_content["images"],
        "RelatedLinks": related_content["links"],
        "ProcessingTime": round(total_time, 2),
    }