import io
from pathlib import Path

from PIL import Image
from ultralytics import YOLO

from image_prep import image_preprocessing


MODEL_PATH = Path(__file__).resolve().parents[1] / "BoundingBox" / "yolo26n.pt"

model = YOLO(str(MODEL_PATH))


def create_point(x, y, image_width, image_height):
    return {
        "x": round(x / image_width, 6),
        "y": round(y / image_height, 6),
    }


def detect_main_area(image_bytes, confidence_threshold=0.25):
    processed_bytes = image_preprocessing(image_bytes)
    processed_image = Image.open(io.BytesIO(processed_bytes))

    image_width, image_height = processed_image.size

    result = model.predict(
        source=processed_image,
        conf=confidence_threshold,
        verbose=False,
    )[0]

    if result.boxes is None or len(result.boxes) == 0:
        minimum_x = 0
        minimum_y = 0
        maximum_x = image_width
        maximum_y = image_height
        detected_objects = []
    else:
        boxes = result.boxes.xyxy.tolist()

        minimum_x = min(box[0] for box in boxes)
        minimum_y = min(box[1] for box in boxes)
        maximum_x = max(box[2] for box in boxes)
        maximum_y = max(box[3] for box in boxes)

        detected_objects = [
            result.names[int(class_id)]
            for class_id in result.boxes.cls.tolist()
        ]

    return {
        "detected_objects": detected_objects,
        "corners": {
            "top_left": create_point(
                minimum_x, minimum_y, image_width, image_height
            ),
            "top_right": create_point(
                maximum_x, minimum_y, image_width, image_height
            ),
            "bottom_right": create_point(
                maximum_x, maximum_y, image_width, image_height
            ),
            "bottom_left": create_point(
                minimum_x, maximum_y, image_width, image_height
            ),
        },
    }