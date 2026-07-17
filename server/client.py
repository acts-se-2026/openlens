import base64
import requests

from image_prep import image_preprocessing

API_KEY = "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"

MODELS = {
    "fast": "openai/gpt-5.6-luna",
    "balanced": "google/gemini-3-flash-preview",
    "deep": "openai/gpt-5.6-sol",
    "free": "google/gemma-4-26b-a4b-it:free"
}


def analyze_image(image_bytes, model="balanced"):
    selected_model = MODELS.get(model)

    processed_image = image_preprocessing(image_bytes)
    image_b64 = base64.b64encode(processed_image).decode("utf-8")

    payload = {
        "model": selected_model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Return the answer using exactly two XML tags: <title> and <description>. "
                            "Inside <title>, write a short and specific title of 2-6 words that clearly identifies what is visible in the image. "
                            "Inside <description>, identify the main subject and describe it in one concise, factual paragraph of 2-3 sentences. "
                            "Include distinctive colors, materials, shapes, context, and any readable text that would help a visual search engine find similar objects or scenes. "
                            "Avoid decorative language, unsupported assumptions, and phrases such as 'The image shows'. "
                            "Express uncertain identifications cautiously using words such as 'likely', 'possibly', or 'appears to be'. "
                            "Use this exact output format: <title>Specific image title</title><description>Concise factual description.</description>"
                        )
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
