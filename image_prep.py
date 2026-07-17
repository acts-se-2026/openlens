import io

from PIL import Image, ImageOps


MAX_DIMENSION = 1600
JPEG_QUALITY = 92

def image_preprocessing(image_bytes):
    image = Image.open(io.BytesIO(image_bytes))
    image = ImageOps.exif_transpose(image)
    image = image.convert("RGB")

    image.thumbnail(
        (MAX_DIMENSION, MAX_DIMENSION),
        Image.Resampling.LANCZOS,
    )

    output = io.BytesIO()

    image.save(
        output,
        format="JPEG",
        quality=JPEG_QUALITY,
        optimize=True,
    )

    return output.getvalue()
