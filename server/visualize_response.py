"""Manual visualizer for the box flow: draws every per-object box from /detect (label + confidence)
over the image, titled with the /image_to_model heading and captioned with its body.

Run the server first, then from server/: `python visualize_response.py`.
"""
from pathlib import Path
import textwrap

import matplotlib.pyplot as plt
import requests
from matplotlib.patches import Rectangle
from PIL import Image

import config


IMAGE_PATH = Path("test_images/cat.jpg")
BASE_URL = "http://127.0.0.1:8000"
# Every route is gated on a bearer token; the dev static token authenticates without Kratos.
HEADERS = {"Authorization": f"Bearer {config.DEV_TEST_TOKEN}"}


def post_image(path, data=None):
    with IMAGE_PATH.open("rb") as image_file:
        response = requests.post(
            f"{BASE_URL}{path}",
            files={"file": (IMAGE_PATH.name, image_file, "image/jpeg")},
            data=data or {},
            headers=HEADERS,
            timeout=120,
        )
    response.raise_for_status()
    return response.json()


objects = post_image("/detect").get("objects", [])
result = post_image("/image_to_model", {"model": "free"})

image = Image.open(IMAGE_PATH).convert("RGB")
image_width, image_height = image.size

figure, axis = plt.subplots(figsize=(12, 9))
axis.imshow(image)

# One rectangle + label per detected object, in the same normalized frame the app draws them in.
for obj in objects:
    corners = obj["corners"]
    left = corners["top_left"]["x"] * image_width
    top = corners["top_left"]["y"] * image_height
    right = corners["bottom_right"]["x"] * image_width
    bottom = corners["bottom_right"]["y"] * image_height

    axis.add_patch(
        Rectangle(
            (left, top),
            right - left,
            bottom - top,
            linewidth=3,
            edgecolor="red",
            facecolor="none",
        )
    )
    axis.text(
        left,
        max(0, top - 8),
        f"{obj['label']} {obj['confidence']:.2f}",
        color="white",
        fontsize=11,
        fontweight="bold",
        bbox={"facecolor": "red", "alpha": 0.8, "edgecolor": "none"},
    )

axis.set_title(result["Heading"], fontsize=20, fontweight="bold")
axis.axis("off")

description = textwrap.fill(result["Body"], width=100)
figure.text(0.5, 0.02, description, ha="center", fontsize=11)

plt.subplots_adjust(bottom=0.15)
plt.show()
