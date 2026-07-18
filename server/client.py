import base64
import os
import requests
import re

from dotenv import load_dotenv

from image_prep import image_preprocessing

load_dotenv()

API_KEY = os.getenv("OPENROUTER_API_KEY")
if not API_KEY:
    raise RuntimeError("OPENROUTER_API_KEY is not set. Copy .env.example to .env and add your key.")

# Inference server (OpenAI-compatible). Defaults to the self-hosted vLLM box; override via env.
MODEL_SERVER_URL = os.getenv("MODEL_SERVER_URL", "http://89.169.96.173:8000")

# Preferred model id per tier for a multi-model backend (e.g. OpenRouter). A single self-hosted
# vLLM ignores this and uses whatever it serves — see _resolve_model.
MODELS = {
    "fast": "openai/gpt-5.6-luna",
    "balanced": "google/gemini-3-flash-preview",
    "deep": "openai/gpt-5.6-sol",
    "free": "google/gemma-4-26b-a4b-it:free"
}

_served_single_model = None


def _resolve_model(tier):
    """Choose the `model` value to send to the inference server.

    A self-hosted vLLM serves exactly ONE model and rejects any other `model` value with a 404, so
    when the backend exposes a single model we send that exact id (whatever checkpoint is loaded).
    When it serves many (OpenRouter-style), we honor the tier -> model mapping instead.
    """
    global _served_single_model
    if _served_single_model:
        return _served_single_model
    fallback = MODELS.get(tier, tier)
    try:
        resp = requests.get(f"{MODEL_SERVER_URL}/v1/models", timeout=10)
        resp.raise_for_status()
        served = resp.json().get("data", [])
    except (requests.RequestException, ValueError):
        return fallback
    if len(served) == 1:
        _served_single_model = served[0]["id"]
        return _served_single_model
    return fallback


def analyze_image(image_bytes, model="balanced"):
    selected_model = _resolve_model(model)

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
                            "Return the answer using exactly two XML tags: <heading> and <description>. "
                            "Inside <heading>, write a short and specific title of 2-6 words that clearly identifies what is visible in the image. "
                            "Inside <description>, identify the main subject and describe it in one concise, factual paragraph of 2-3 sentences. "
                            "Include distinctive colors, materials, shapes, context, and any readable text that would help a visual search engine find similar objects or scenes. "
                            "Avoid decorative language, unsupported assumptions, and phrases such as 'The image shows'. "
                            "Express uncertain identifications cautiously using words such as 'likely', 'possibly', or 'appears to be'. "
                            "Use this exact output format: <heading>Specific image title</heading><description>Concise factual description.</description>"
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
        f"{MODEL_SERVER_URL}/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json"
        },
        json=payload,
        timeout=120
    )

    response.raise_for_status()

    content = response.json()["choices"][0]["message"]["content"]

    # server.py reads result["heading"] and result["body"], so pull them out of the
    # model's <heading>/<description> tags. Fall back to the raw text if a tag is missing.
    heading = re.search(r"<heading>(.*?)</heading>", content, re.DOTALL)
    description = re.search(r"<description>(.*?)</description>", content, re.DOTALL)
    return {
        "heading": heading.group(1).strip() if heading else "Result",
        "body": description.group(1).strip() if description else content.strip(),
    }
