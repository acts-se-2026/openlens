from fastapi import Depends, FastAPI, File, UploadFile

from .client import analyze_image
from .kratos import get_current_identity

app = FastAPI()


@app.post("/image_to_model")
async def image_to_model(
    file: UploadFile = File(...),
    identity: dict = Depends(get_current_identity),  # gated: requires a valid Kratos session
):
    image_bytes = await file.read()
    result = analyze_image(image_bytes)

    return {
    "Heading": result["heading"],
    "Body": result["body"]
    }
