import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import asyncio
import httpx
from fastapi.testclient import TestClient

from server import app

client = TestClient(app)

def test_cat_image():
    start = time.perf_counter()

    with open("test_images/cat.jpg", "rb") as image:
        response = client.post(
            "/image_to_model",
            files={
                "file": (
                    "cat.jpg",
                    image,
                    "image/jpeg"
                )
            },
            data={"model": "free"}
        )

    end = time.perf_counter()
    print(f"\nOne image time: {end - start:.2f} seconds")
    assert response.status_code == 200
    result = response.json()

    text = result["Heading"] + " " + result["Body"]

    assert "kitten" in text.lower()
    assert "BoundingBox" in result


async def send_image(path):
    print("image sent!")

    transport = httpx.ASGITransport(app=app)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://test"
    ) as client:
        with open(path, "rb") as image:
            # print("START RQ")
            start = time.perf_counter()

            response = await client.post(
                "/image_to_model",
                files={
                    "file": (
                        "cat.jpg",
                        image,
                        "image/jpeg"
                    )
                },
                data={"model": "free"}
            )
            
            end = time.perf_counter()
            # print("END RQ")

    return response, end - start

def test_ten_requests():
    async def run():
        start = time.perf_counter()

        responses = await asyncio.gather(
            *[
                send_image("test_images/cat.jpg")
                for _ in range(5)
            ]
        )

        end = time.perf_counter()

        total_time = end - start

        print(f"\nMultiple images total time: {total_time:.2f} seconds")

        for i, (_, request_time) in enumerate(responses):
            print(f"Image {i+1} time: {request_time:.2f} seconds")

        for response, _ in responses:
            assert response.status_code == 200

    asyncio.run(run())
