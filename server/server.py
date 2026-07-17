from fastapi import FastAPI, UploadFile, File, Form
from client import analyze_image

app = FastAPI()

@app.post("/image_to_model")
async def image_to_model(file: UploadFile = File(...), model: str = Form("free")):
    image_bytes = await file.read()
    result = await analyze_image(image_bytes, model)
    return {
    "Heading": result["heading"],
    "Body": result["body"]
    }
