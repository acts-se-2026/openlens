import argparse
import csv
import io
import json
import math
import os
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests
from dotenv import load_dotenv


load_dotenv()

MODEL_ID = os.getenv(
    "SIGLIP_MODEL_ID",
    "google/siglip2-so400m-patch16-naflex",
)
DATA_URL_TEMPLATE = os.getenv(
    "PD12M_DATA_URL_TEMPLATE",
    (
        "https://huggingface.co/datasets/Intelligent-Internet/"
        "pd12m/resolve/main/data/{shard}.csv"
    ),
)

SERVER_FOLDER = Path(__file__).resolve().parent
INDEX_FOLDER = Path(
    os.getenv("PD12M_INDEX_DIR", SERVER_FOLDER / "pd12m_index")
)
VECTORS_PATH = INDEX_FOLDER / "vectors.npy"
METADATA_PATH = INDEX_FOLDER / "metadata.json"

VECTOR_DIMENSION = 1152
DEFAULT_INDEX_SIZE = 10_000
DEFAULT_SHARD_COUNT = 10
DEVICE = "cpu"

_model = None
_processor = None
_torch = None
_vectors = None
_metadata = None

_model_lock = threading.Lock()
_index_lock = threading.Lock()
_inference_lock = threading.Lock()


def read_shard(shard_index, item_limit):
    import numpy as np

    vectors = []
    metadata = []
    shard_name = f"{shard_index:07d}"
    shard_url = DATA_URL_TEMPLATE.format(shard=shard_name)

    response = requests.get(shard_url, stream=True, timeout=(30, 600))
    response.raise_for_status()
    response.raw.decode_content = True

    text_stream = io.TextIOWrapper(
        response.raw,
        encoding="utf-8",
        newline="",
    )
    reader = csv.DictReader(text_stream)

    try:
        for row in reader:
            image_url = row.get("url", "").strip()
            vector_text = row.get("vector", "").strip()

            if not image_url.startswith(("http://", "https://")):
                continue

            if not vector_text:
                continue

            vector = np.fromstring(
                vector_text.strip("[]"),
                sep=",",
                dtype=np.float32,
            )

            if vector.shape != (VECTOR_DIMENSION,):
                continue

            vector_norm = np.linalg.norm(vector)
            if vector_norm == 0:
                continue

            vector /= vector_norm
            vectors.append(vector.astype(np.float16))

            metadata.append(
                {
                    "id": row.get("id", ""),
                    "url": image_url,
                    "caption": row.get("caption", ""),
                    "caption_long": row.get("caption_long", ""),
                    "source": (
                        row.get("origin_source")
                        or row.get("source")
                        or "PD12M"
                    ),
                    "license": row.get("license", ""),
                }
            )

            if len(vectors) >= item_limit:
                break
    finally:
        response.close()

    if not vectors:
        raise RuntimeError(f"PD12M shard {shard_name} returned no vectors.")

    return vectors, metadata


def build_index(
    limit=DEFAULT_INDEX_SIZE,
    shard_count=DEFAULT_SHARD_COUNT,
):
    """Create a diverse subset by reading several PD12M shards in parallel."""
    import numpy as np

    if limit < 1:
        raise ValueError("Index size must be greater than zero.")

    shard_count = max(1, min(shard_count, limit))
    items_per_shard = math.ceil(limit / shard_count)

    INDEX_FOLDER.mkdir(parents=True, exist_ok=True)

    print(
        f"Preparing {limit} PD12M entries from "
        f"{shard_count} shards..."
    )

    vectors = []
    metadata = []

    with ThreadPoolExecutor(
        max_workers=min(shard_count, 5)
    ) as executor:
        futures = {
            executor.submit(
                read_shard,
                shard_index,
                items_per_shard,
            ): shard_index
            for shard_index in range(shard_count)
        }

        for future in as_completed(futures):
            shard_index = futures[future]

            try:
                shard_vectors, shard_metadata = future.result()
            except Exception as error:
                print(f"Shard {shard_index:07d} failed: {error}")
                continue

            vectors.extend(shard_vectors)
            metadata.extend(shard_metadata)
            print(
                f"Shard {shard_index:07d} complete: "
                f"{len(metadata)}/{limit}"
            )

    if not vectors:
        raise RuntimeError("PD12M returned no valid vectors.")

    vectors = vectors[:limit]
    metadata = metadata[:limit]

    np.save(VECTORS_PATH, np.stack(vectors))
    METADATA_PATH.write_text(
        json.dumps(metadata, ensure_ascii=False),
        encoding="utf-8",
    )

    print(f"Index ready with {len(metadata)} images.")
    print(f"Vectors: {VECTORS_PATH}")
    print(f"Metadata: {METADATA_PATH}")


def index_is_ready():
    return VECTORS_PATH.exists() and METADATA_PATH.exists()


def load_index():
    global _vectors
    global _metadata

    if _vectors is not None:
        return

    with _index_lock:
        if _vectors is not None:
            return

        if not index_is_ready():
            raise FileNotFoundError(
                "The PD12M index is missing. Run once from the server folder: "
                f"python search_client.py --build --limit {DEFAULT_INDEX_SIZE}"
            )

        import numpy as np

        vector_array = np.load(VECTORS_PATH)
        metadata = json.loads(METADATA_PATH.read_text(encoding="utf-8"))

        if vector_array.ndim != 2 or vector_array.shape[1] != VECTOR_DIMENSION:
            raise RuntimeError(
                f"Invalid vector matrix shape: {vector_array.shape}"
            )

        if len(vector_array) != len(metadata):
            raise RuntimeError(
                "The PD12M vector and metadata counts do not match."
            )

        _vectors = vector_array.astype(np.float32)
        _metadata = metadata

        print(f"Loaded {len(_metadata)} PD12M vectors on CPU.")


def load_model():
    global _model
    global _processor
    global _torch

    if _model is not None:
        return

    with _model_lock:
        if _model is not None:
            return

        try:
            import torch
            from transformers import AutoModel, AutoProcessor
        except ImportError as error:
            raise RuntimeError(
                "Similar-image search needs transformers. Install it with: "
                "pip install transformers"
            ) from error

        print(f"Loading {MODEL_ID} on CPU. The first load can take a while.")

        _processor = AutoProcessor.from_pretrained(MODEL_ID)
        _model = AutoModel.from_pretrained(MODEL_ID)
        _model.to(DEVICE)
        _model.eval()
        _torch = torch


def encode_image(image_bytes):
    from PIL import Image, ImageOps

    load_model()

    with Image.open(io.BytesIO(image_bytes)) as opened_image:
        image = ImageOps.exif_transpose(opened_image).convert("RGB")

    inputs = _processor(images=[image], return_tensors="pt")
    inputs = {
        name: value.to(DEVICE)
        for name, value in inputs.items()
    }

    with _inference_lock:
        with _torch.inference_mode():
            image_features = _model.get_image_features(**inputs)

    if hasattr(image_features, "pooler_output"):
        image_features = image_features.pooler_output
    elif hasattr(image_features, "image_embeds"):
        image_features = image_features.image_embeds
    elif not _torch.is_tensor(image_features):
        image_features = image_features[0]

    if image_features.ndim == 1:
        image_features = image_features.unsqueeze(0)

    if image_features.shape[-1] != VECTOR_DIMENSION:
        raise RuntimeError(
            "SigLIP2 returned an unexpected embedding shape: "
            f"{tuple(image_features.shape)}"
        )

    image_features = image_features.float().clone()
    image_features = image_features / image_features.norm(
        dim=-1,
        keepdim=True,
    ).clamp_min(1e-12)

    return image_features[0].cpu().numpy()


def search_related_content(image_bytes, limit=4):
    """Return the closest PD12M images using exact cosine similarity."""
    import numpy as np

    if limit < 1:
        return {
            "images": [],
            "links": [],
            "engine": "pd12m-siglip2",
            "status": "disabled",
            "error": None,
        }

    load_index()
    query_vector = encode_image(image_bytes).astype(np.float32)

    result_count = min(limit, len(_metadata))
    scores = _vectors @ query_vector

    if result_count == len(scores):
        best_indices = np.argsort(scores)[::-1]
    else:
        best_indices = np.argpartition(
            scores,
            -result_count,
        )[-result_count:]
        best_indices = best_indices[
            np.argsort(scores[best_indices])[::-1]
        ]

    images = []
    links = []

    for index in best_indices:
        item = _metadata[int(index)]
        caption = (
            item.get("caption_long")
            or item.get("caption")
            or "Similar image"
        )
        image_url = item["url"]

        images.append(
            {
                "title": caption[:180],
                "image_url": image_url,
                "url": image_url,
                "source_url": image_url,
                "source": item.get("source", "PD12M"),
                "license": item.get("license", ""),
                "similarity": round(float(scores[index]), 4),
            }
        )
        links.append(
            {
                "title": caption[:180],
                "url": image_url,
                "source": item.get("source", "PD12M"),
                "license": item.get("license", ""),
            }
        )

    return {
        "images": images,
        "links": links,
        "engine": "pd12m-siglip2",
        "status": "ready",
        "error": None,
    }


def warmup_search():
    load_index()
    load_model()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--limit", type=int, default=DEFAULT_INDEX_SIZE)
    parser.add_argument("--image", type=Path)
    arguments = parser.parse_args()

    if arguments.build:
        build_index(arguments.limit)
        return

    if arguments.image:
        if not arguments.image.exists():
            raise FileNotFoundError(arguments.image)

        result = search_related_content(
            arguments.image.read_bytes(),
            limit=4,
        )
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return

    print(
        "Create the index:\n"
        f"python search_client.py --build --limit {DEFAULT_INDEX_SIZE}\n\n"
        "Test an image:\n"
        "python search_client.py --image test_images/cat.jpg"
    )


if __name__ == "__main__":
    main()
