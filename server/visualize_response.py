import io
import json
import mimetypes
import os
import textwrap
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import matplotlib.pyplot as plt
import requests
from dotenv import load_dotenv
from matplotlib.gridspec import GridSpec
from matplotlib.patches import Rectangle
from PIL import Image, ImageOps


SCRIPT_FOLDER = Path(__file__).resolve().parent
PROJECT_FOLDER = SCRIPT_FOLDER.parent

load_dotenv(PROJECT_FOLDER / ".env")

IMAGE_PATH = Path(
    os.getenv(
        "OPENLENS_TEST_IMAGE",
        SCRIPT_FOLDER / "test_images" / "cat.jpg",
    )
)
SERVER_URL = os.getenv(
    "OPENLENS_SERVER_URL",
    "http://127.0.0.1:8000/image_to_model",
)
MODEL = os.getenv("OPENLENS_TEST_MODEL", "free")
SESSION_TOKEN = os.getenv("KRATOS_SESSION_TOKEN")

MAX_SIMILAR_IMAGES = 4
OUTPUT_IMAGE = SCRIPT_FOLDER / "server_response_preview.png"
OUTPUT_JSON = SCRIPT_FOLDER / "last_server_response.json"


def send_image_to_server():
    if not IMAGE_PATH.exists():
        raise FileNotFoundError(f"Image does not exist: {IMAGE_PATH}")

    mime_type = mimetypes.guess_type(IMAGE_PATH.name)[0] or "image/jpeg"
    headers = {}

    if SESSION_TOKEN:
        headers["Authorization"] = f"Bearer {SESSION_TOKEN}"

    request_start = time.perf_counter()

    with IMAGE_PATH.open("rb") as image_file:
        response = requests.post(
            SERVER_URL,
            headers=headers,
            files={
                "file": (
                    IMAGE_PATH.name,
                    image_file,
                    mime_type,
                )
            },
            data={"model": MODEL},
            timeout=240,
        )

    request_time = time.perf_counter() - request_start

    if not response.ok:
        extra_help = ""
        if response.status_code in {401, 403} and not SESSION_TOKEN:
            extra_help = (
                "\nThe endpoint is protected by Kratos. Add "
                "KRATOS_SESSION_TOKEN to the root .env file."
            )

        raise RuntimeError(
            f"Server returned HTTP {response.status_code}:\n"
            f"{response.text[:2000]}{extra_help}"
        )

    return response.json(), request_time


def point_coordinates(point):
    if isinstance(point, dict):
        return float(point["x"]), float(point["y"])

    if isinstance(point, (list, tuple)) and len(point) >= 2:
        return float(point[0]), float(point[1])

    raise ValueError(f"Invalid corner format: {point}")


def corner_to_pixels(point, image_width, image_height):
    x, y = point_coordinates(point)

    if 0 <= x <= 1 and 0 <= y <= 1:
        return x * image_width, y * image_height

    return x, y


def draw_bounding_box(axis, image, result):
    corners = result.get("BoundingBox")

    if not corners:
        axis.text(
            0.5,
            0.05,
            "No bounding box returned",
            transform=axis.transAxes,
            horizontalalignment="center",
            color="white",
            fontsize=11,
            bbox={
                "facecolor": "#cc2020",
                "alpha": 0.85,
                "edgecolor": "none",
            },
        )
        return

    image_width, image_height = image.size

    top_left = corner_to_pixels(
        corners["top_left"],
        image_width,
        image_height,
    )
    bottom_right = corner_to_pixels(
        corners["bottom_right"],
        image_width,
        image_height,
    )

    minimum_x, minimum_y = top_left
    maximum_x, maximum_y = bottom_right

    axis.add_patch(
        Rectangle(
            (minimum_x, minimum_y),
            maximum_x - minimum_x,
            maximum_y - minimum_y,
            linewidth=5,
            edgecolor="#ff2525",
            facecolor="none",
        )
    )

    objects = result.get("DetectedObjects", [])
    if objects:
        axis.text(
            minimum_x,
            max(15, minimum_y - 15),
            ", ".join(objects),
            color="white",
            fontsize=11,
            fontweight="bold",
            bbox={
                "facecolor": "#cc2020",
                "alpha": 0.9,
                "edgecolor": "none",
            },
        )


def download_similar_image(image_data):
    image_url = (
        image_data.get("image_url")
        or image_data.get("thumbnail_url")
        or image_data.get("url")
    )

    if not image_url:
        return None

    try:
        response = requests.get(
            image_url,
            headers={"User-Agent": "Mozilla/5.0 OpenLens/1.0"},
            timeout=12,
        )
        response.raise_for_status()

        with Image.open(io.BytesIO(response.content)) as downloaded:
            return ImageOps.exif_transpose(downloaded).convert("RGB")
    except Exception as error:
        print(f"Could not download similar image: {error}")
        return None


def download_similar_images(image_results):
    selected = image_results[:MAX_SIMILAR_IMAGES]

    if not selected:
        return []

    with ThreadPoolExecutor(max_workers=len(selected)) as executor:
        images = list(executor.map(download_similar_image, selected))

    return list(zip(selected, images))


def build_information_text(result, request_time):
    objects = result.get("DetectedObjects", [])
    search_status = result.get("SearchStatus", "unknown")
    search_error = result.get("SearchError")
    related_links = result.get("RelatedLinks", [])

    lines = [
        "DESCRIPTION",
        textwrap.fill(result.get("Body", "No description"), width=48),
        "",
        "DETECTED OBJECTS",
        ", ".join(objects) if objects else "None",
        "",
        "SIMILAR IMAGE SEARCH",
        f"Engine: {result.get('SearchEngine', 'unknown')}",
        f"Status: {search_status}",
        f"Matches: {len(result.get('SimilarImages', []))}",
    ]

    if search_error:
        lines.extend(
            [
                "",
                "SEARCH MESSAGE",
                textwrap.fill(str(search_error), width=48),
            ]
        )

    lines.extend(
        [
            "",
            "TIMING",
            f"HTTP request: {request_time:.2f}s",
            f"Server processing: {result.get('ProcessingTime', 'N/A')}s",
            "",
            f"RELATED LINKS ({len(related_links)})",
        ]
    )

    if not related_links:
        lines.append("No related links returned.")

    for index, link in enumerate(related_links, start=1):
        lines.extend(
            [
                "",
                textwrap.fill(
                    f"{index}. {link.get('title', 'Similar image')}",
                    width=48,
                ),
                textwrap.shorten(
                    link.get("url", ""),
                    width=70,
                    placeholder="...",
                ),
            ]
        )

    return "\n".join(lines)


def display_result(result, request_time):
    with Image.open(IMAGE_PATH) as opened_image:
        original_image = ImageOps.exif_transpose(opened_image).convert("RGB")

    image_results = (
        result.get("SimilarImages")
        or result.get("RelatedImages")
        or []
    )
    similar_images = download_similar_images(image_results)

    figure = plt.figure(figsize=(18, 11), facecolor="#081321")
    grid = GridSpec(
        2,
        4,
        figure=figure,
        height_ratios=[2.5, 1],
        width_ratios=[1, 1, 1, 1.2],
        hspace=0.25,
        wspace=0.12,
    )

    main_axis = figure.add_subplot(grid[0, :3])
    main_axis.imshow(original_image)
    draw_bounding_box(main_axis, original_image, result)
    main_axis.set_title(
        result.get("Heading", "OpenLens result"),
        color="white",
        fontsize=22,
        fontweight="bold",
        pad=14,
    )
    main_axis.axis("off")

    information_axis = figure.add_subplot(grid[0, 3])
    information_axis.set_facecolor("#101f32")
    information_axis.axis("off")
    information_axis.text(
        0.04,
        0.97,
        build_information_text(result, request_time),
        transform=information_axis.transAxes,
        verticalalignment="top",
        color="white",
        fontsize=8.5,
        linespacing=1.3,
    )

    for index in range(MAX_SIMILAR_IMAGES):
        axis = figure.add_subplot(grid[1, index])
        axis.set_facecolor("#101f32")
        axis.axis("off")

        if index >= len(similar_images):
            axis.text(
                0.5,
                0.5,
                "No similar image",
                transform=axis.transAxes,
                horizontalalignment="center",
                verticalalignment="center",
                color="#a8b8ca",
            )
            continue

        image_data, downloaded_image = similar_images[index]

        if downloaded_image is not None:
            axis.imshow(downloaded_image)
        else:
            axis.text(
                0.5,
                0.5,
                "Download failed",
                transform=axis.transAxes,
                horizontalalignment="center",
                verticalalignment="center",
                color="#ff7777",
            )

        similarity = image_data.get("similarity")
        score_text = (
            f"Cosine similarity: {similarity:.3f}"
            if isinstance(similarity, (int, float))
            else "Similarity unavailable"
        )

        axis.set_title(
            textwrap.shorten(
                image_data.get("title", "Similar image"),
                width=55,
                placeholder="...",
            ),
            color="white",
            fontsize=10,
            pad=7,
        )
        axis.text(
            0.5,
            -0.07,
            score_text,
            transform=axis.transAxes,
            horizontalalignment="center",
            color="#55d6be",
            fontsize=9,
        )

    figure.suptitle(
        "OpenLens - Complete Server Response",
        color="white",
        fontsize=24,
        fontweight="bold",
        y=0.98,
    )

    figure.savefig(
        OUTPUT_IMAGE,
        dpi=160,
        bbox_inches="tight",
        facecolor=figure.get_facecolor(),
    )

    print(f"\nPreview saved to: {OUTPUT_IMAGE}")
    print(f"JSON saved to: {OUTPUT_JSON}")
    plt.show()


def main():
    result, request_time = send_image_to_server()

    OUTPUT_JSON.write_text(
        json.dumps(result, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    print("\n" + "=" * 70)
    print("COMPLETE SERVER RESPONSE")
    print("=" * 70)
    print(json.dumps(result, indent=2, ensure_ascii=False))

    display_result(result, request_time)


if __name__ == "__main__":
    main()
