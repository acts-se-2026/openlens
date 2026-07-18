import asyncio

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
    image_bytes = await file.read()

    if detect_main_area is None:
        model_result = await run_in_threadpool(
            analyze_image,
            image_bytes,
            model,
        )

        bounding_box_result = {
            "corners": None,
            "detected_objects": [],
        }

    else:
        model_result, bounding_box_result = await asyncio.gather(
            run_in_threadpool(
                analyze_image,
                image_bytes,
                model,
            ),
            run_in_threadpool(
                detect_main_area,
                image_bytes,
            ),
        )

    detected_objects = list(
        dict.fromkeys(
            bounding_box_result["detected_objects"]
        )
    )

    query_parts = [
        model_result["heading"],
        *detected_objects[:3],
    ]

    search_query = " ".join(
        part for part in query_parts if part
    ).strip()

    try:
        related_content = await run_in_threadpool(
            search_related_content,
            search_query,
        )

    except Exception as error:
        print(f"Web search error: {error}")

        related_content = {
            "images": [],
            "links": [],
        }

    return {
        "Heading": model_result["heading"],
        "Body": model_result["body"],
        "BoundingBox": bounding_box_result["corners"],
        "DetectedObjects": bounding_box_result["detected_objects"],
        "SearchQuery": search_query,
        "RelatedImages": related_content["images"],
        "RelatedLinks": related_content["links"],
    }