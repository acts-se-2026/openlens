from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from transformers import pipeline
import io
app = FastAPI()

model=pipeline("image-to-text", model="Model_name")#SUPPOSED TO INPUT NAME/ ROUTE TO THE MODEL
@app.post("/image_to_model")
async def image_to_model(file: UploadFile=File(...)):
    image_bytes=await file.read()
    image=Image.open(io.BytesIO(image_bytes))
    output=model(image)
    result_text=output[0]["generated_text"]
    return {"result": result_text}