from pathlib import Path
import io
import sys

import matplotlib.pyplot as plt
from PIL import Image, ImageOps

PROJECT_ROOT = Path(__file__).resolve().parents[2]
sys.path.append(str(PROJECT_ROOT))
from image_prep import image_preprocessing


IMAGE_PATH = PROJECT_ROOT / "Model" / "ImageDataset" / "insect.jpg"

# Read the original image
original_bytes = IMAGE_PATH.read_bytes()

original_image = Image.open(
    io.BytesIO(original_bytes)
)

original_image = ImageOps.exif_transpose(
    original_image
).convert("RGB")


# Preprocess the image
processed_bytes = image_preprocessing(
    original_bytes
)

processed_image = Image.open(
    io.BytesIO(processed_bytes)
)


# Display both images
figure, axes = plt.subplots(
    1,
    2,
    figsize=(14, 7),
)

axes[0].imshow(original_image)
axes[0].set_title(
    f"Before preprocessing\n"
    f"{original_image.width} × {original_image.height}"
)
axes[0].axis("off")

axes[1].imshow(processed_image)
axes[1].set_title(
    f"After preprocessing\n"
    f"{processed_image.width} × {processed_image.height}"
)
axes[1].axis("off")

figure.suptitle(
    "Image Preprocessing Comparison",
    fontsize=18,
    fontweight="bold",
)

plt.tight_layout()
plt.show()