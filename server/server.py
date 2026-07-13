from fastapi import FastAPI, UploadFile, File
from client import analyze_image

app = FastAPI()

@app.post("/image_to_model")
async def image_to_model(file: UploadFile = File(...)):
    image_bytes = await file.read()
    result = analyze_image(image_bytes)

    return {
    "Heading": result["heading"],
    "Body": result["body"]
    }