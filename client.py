import base64
import os

import requests
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("OPENROUTER_API_KEY")
MODEL = "openai/gpt-4.1-mini"  # gemini 3.5 flash can work


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
                        "text": "Return the answer using exactly two XML tags: <heading> and <description>."
                        "Inside <heading>, write a short and specific title of 2-6 words that clearly identifies what is visible in the image. "
                        "Inside <description>, identify the main subject and describe it in one concise, factual paragraph of 2-3 sentences. "
                        "Include distinctive colors, materials, shapes, context, and any readable text that would help a visual search engine find similar objects or scenes. "
                        "Avoid decorative language, unsupported assumptions, and phrases such as 'The image shows. "
                        "Express uncertain identifications cautiously using words such as 'likely,' 'possibly,' or 'appears to be. "
                        "Use this exact output format: <heading>Specific image title</heading><description>Concise factual description.</description>"
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
