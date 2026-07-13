import base64
import requests

API_KEY = "key"
MODEL = "openai/gpt-4.1-mini" #gemini 3.5 flash

def analyze_image(image_bytes):
    image_b64 = base64.b64encode(image_bytes).decode("utf-8")
    payload = {
        "model": MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "Analyze this image and describe it in detail."
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{image_b64}"
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
