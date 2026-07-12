import os
import base64
import mimetypes
import requests

API_KEY = "key"

IMAGE_PATH = "path"

MODEL = "openai/gpt-4.1-mini"

def encode_image(image_path):
    with open(image_path, "rb") as image_file:
        image_bytes = image_file.read()

    mime_type = mimetypes.guess_type(image_path)[0] or "image/jpeg"

    image_b64 = base64.b64encode(image_bytes).decode("utf-8")

    return mime_type, image_b64


def analyze_image(mime_type, image_b64):
    """
    Šalje sliku OpenRouter vision modelu
    """

    payload = {
        "model": MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": """
                            Analyze this image and describe it in detail.
                        """
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:{mime_type};base64,{image_b64}"
                        }
                    }
                ]
            }
        ]
    }


    response = requests.post(
        "https://openrouter.ai/api/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json"
        },
        json=payload,
        timeout=120
    )


    response.raise_for_status()

    result = response.json()

    return result["choices"][0]["message"]["content"]



if __name__ == "__main__":

    mime_type, image_b64 = encode_image(IMAGE_PATH)

    answer = analyze_image(
        mime_type,
        image_b64
    )

    print(answer)
