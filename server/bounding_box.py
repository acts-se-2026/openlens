import io
import os

from dotenv import load_dotenv
from PIL import Image
from ultralytics import YOLO

from image_prep import image_preprocessing


load_dotenv()

MODEL_PATH = os.getenv("YOLO_MODEL_PATH")

if not MODEL_PATH:
    raise RuntimeError("YOLO_MODEL_PATH is not set in the .env file.")

model = YOLO(MODEL_PATH)

def create_point(x, y, image_width, image_height):
    return {
        "x": round(x / image_width, 6),
        "y": round(y / image_height, 6),
    }


def detect_objects(image_bytes, confidence_threshold=0.25):
    """Per-object detections for the interactive box flow: each YOLO detection as its own box
    (label + confidence + normalized corners) — the boxes the app draws as editable rectangles.
    Returns an empty list when nothing clears the confidence threshold.
    """
    processed_bytes = image_preprocessing(image_bytes)
    processed_image = Image.open(io.BytesIO(processed_bytes))

    image_width, image_height = processed_image.size

    result = model.predict(
        source=processed_image,
        conf=confidence_threshold,
        verbose=False,
    )[0]

    if result.boxes is None or len(result.boxes) == 0:
        return []

    boxes = result.boxes.xyxy.tolist()
    class_ids = result.boxes.cls.tolist()
    confidences = result.boxes.conf.tolist()

    objects = []
    for (box_x1, box_y1, box_x2, box_y2), class_id, confidence in zip(
        boxes, class_ids, confidences
    ):
        objects.append(
            {
                "label": result.names[int(class_id)],
                "confidence": round(float(confidence), 4),
                "corners": {
                    "top_left": create_point(
                        box_x1, box_y1, image_width, image_height
                    ),
                    "top_right": create_point(
                        box_x2, box_y1, image_width, image_height
                    ),
                    "bottom_right": create_point(
                        box_x2, box_y2, image_width, image_height
                    ),
                    "bottom_left": create_point(
                        box_x1, box_y2, image_width, image_height
                    ),
                },
            }
        )
    return objects