import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import asyncio
import httpx
from fastapi.testclient import TestClient

from server import app

client = TestClient(app)

def test_cat_image():
    with open("test_images/cat.jpg", "rb") as image:
        response = client.post(
            "/image_to_model",
            files={
                "file": (
                    "cat.jpg",
                    image,
                    "image/jpeg"
                )
            }
        )

    assert response.status_code == 200

    result = response.json()["result"]

    assert "kitten" in result.lower()

async def send_image(path):
    print("image_sent")
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://test"
    ) as client:
        with open(path, "rb") as image:
            response = await client.post(
                "/image_to_model",
                files={
                    "file": (
                        "cat.jpg",
                        image,
                        "image/jpeg"
                    )
                }
            )

    return response

def test_two_requests():
    async def run():
        responses = await asyncio.gather(
            send_image("test_images/cat.jpg"),
            send_image("test_images/cat.jpg")
        )
        assert responses[0].status_code == 200
        assert responses[1].status_code == 200

    asyncio.run(run())