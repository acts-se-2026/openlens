from pathlib import Path
import textwrap

import matplotlib.pyplot as plt
import requests
from matplotlib.patches import Rectangle
from PIL import Image


IMAGE_PATH = Path("test_images/cat.jpg")
SERVER_URL = "http://127.0.0.1:8000/image_to_model"


with IMAGE_PATH.open("rb") as image_file:
    response = requests.post(
        SERVER_URL,
        files={
            "file": (
                IMAGE_PATH.name,
                image_file,
                "image/jpeg",
            )
        },
        data={"model": "free"},
        timeout=120,
    )

response.raise_for_status()
result = response.json()

image = Image.open(IMAGE_PATH).convert("RGB")
image_width, image_height = image.size

corners = result["BoundingBox"]

minimum_x = corners["top_left"]["x"] * image_width
minimum_y = corners["top_left"]["y"] * image_height
maximum_x = corners["bottom_right"]["x"] * image_width
maximum_y = corners["bottom_right"]["y"] * image_height

box_width = maximum_x - minimum_x
box_height = maximum_y - minimum_y

figure, axis = plt.subplots(figsize=(12, 9))

axis.imshow(image)

bounding_box = Rectangle(
    (minimum_x, minimum_y),
    box_width,
    box_height,
    linewidth=6,
    edgecolor="red",
    facecolor="none",
)

axis.add_patch(bounding_box)

corner_x = [
    minimum_x,
    maximum_x,
    maximum_x,
    minimum_x,
]

corner_y = [
    minimum_y,
    minimum_y,
    maximum_y,
    maximum_y,
]

axis.scatter(
    corner_x,
    corner_y,
    color="yellow",
    edgecolors="black",
    s=100,
    zorder=3,
)

axis.set_title(
    result["Heading"],
    fontsize=20,
    fontweight="bold",
)

objects = ", ".join(result.get("DetectedObjects", []))

if objects:
    axis.text(
        minimum_x,
        max(0, minimum_y - 15),
        objects,
        color="white",
        fontsize=12,
        fontweight="bold",
        bbox={
            "facecolor": "red",
            "alpha": 0.8,
            "edgecolor": "none",
        },
    )

axis.axis("off")

description = textwrap.fill(
    result["Body"],
    width=100,
)

figure.text(
    0.5,
    0.02,
    description,
    ha="center",
    fontsize=11,
)

plt.subplots_adjust(bottom=0.15)
plt.show()