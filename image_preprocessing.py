import io

from PIL import Image, ImageEnhance, ImageOps


MAX_DIMENSION = 1600
JPEG_QUALITY = 90

# easy preprocessing
# resize, correct orientation, convert to rgb
# improve contrast and sharpness a little bit
# then, compress as jpeg 
def preprocess_image(image_bytes):
    image = Image.open(io.BytesIO(image_bytes))

    image = ImageOps.exif_transpose(image)

    image = image.convert("RGB")

    image.thumbnail((MAX_DIMENSION, MAX_DIMENSION),Image.Resampling.LANCZOS)

    image = ImageOps.autocontrast(image, cutoff = 1)

    image = ImageEnhance.Sharpness(image).enhance(1.10)

    output = io.BytesIO()

    image.save(output, format = "JPEG", quality = JPEG_QUALITY, optimize = True)

    return output.getvalue()