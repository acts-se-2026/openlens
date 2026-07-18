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


def crop_to_region(image_bytes, region, padding=0.25):
    """Crop the (EXIF-corrected) image to a normalized region, expanded by `padding` on each side
    for surrounding context, and return it as JPEG bytes.

    `region` is (x1, y1, x2, y2) in 0..1 coordinates, in the same EXIF-corrected, aspect-preserved
    frame the detector reports its boxes in (thumbnailing preserves aspect ratio, so normalized
    coords are identical against the original and the preprocessed image). The crop is returned as
    JPEG so it can be handed straight to analyze_image, which preprocesses again — a near no-op on an
    already-upright image.
    """
    image = Image.open(io.BytesIO(image_bytes))
    image = ImageOps.exif_transpose(image)
    image = image.convert("RGB")

    width, height = image.size

    x1, y1, x2, y2 = region
    # Normalize ordering and clamp so a malformed region can't invert the box or escape the frame.
    left_n, right_n = sorted((x1, x2))
    top_n, bottom_n = sorted((y1, y2))
    pad_x = (right_n - left_n) * padding
    pad_y = (bottom_n - top_n) * padding
    left = max(0.0, left_n - pad_x)
    top = max(0.0, top_n - pad_y)
    right = min(1.0, right_n + pad_x)
    bottom = min(1.0, bottom_n + pad_y)

    box = (
        int(left * width),
        int(top * height),
        int(right * width),
        int(bottom * height),
    )

    # A degenerate region (zero area after rounding) falls back to the whole image rather than
    # producing an empty crop the model can't read.
    if box[2] - box[0] < 1 or box[3] - box[1] < 1:
        cropped = image
    else:
        cropped = image.crop(box)

    output = io.BytesIO()
    cropped.save(output, format="JPEG", quality=JPEG_QUALITY, optimize=True)
    return output.getvalue()