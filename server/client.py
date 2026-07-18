import base64
import json
import os
import re
from pathlib import Path

import requests
from dotenv import load_dotenv

from image_prep import image_preprocessing


ENV_PATH = Path(__file__).resolve().parents[1] / ".env"
load_dotenv(ENV_PATH)

OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")

OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"

FREE_MODEL_API_URL = os.getenv(
    "FREE_MODEL_API_URL",
    "http://89.169.96.173:8000/v1/chat/completions",
)

FREE_MODEL_API_KEY = os.getenv("FREE_MODEL_API_KEY")

MODELS = {
    "fast": "openai/gpt-5.6-luna",
    "balanced": "google/gemini-3-flash-preview",
    "deep": "openai/gpt-5.6-sol",
    "free": "RedHatAI/gemma-4-31B-it-FP8-block",
}


def get_model(model):
    if model not in MODELS:
        available_models = ", ".join(MODELS.keys())
        raise ValueError(
            f"Unknown model tier: {model}. "
            f"Available tiers: {available_models}"
        )

    return MODELS[model]


def get_api_settings(model):
    if model == "free":
        headers = {
            "Content-Type": "application/json",
        }

        if FREE_MODEL_API_KEY:
            headers["Authorization"] = f"Bearer {FREE_MODEL_API_KEY}"

        return FREE_MODEL_API_URL, headers

    if not OPENROUTER_API_KEY:
        raise RuntimeError(
            "OPENROUTER_API_KEY is not set in the .env file."
        )

    headers = {
        "Authorization": f"Bearer {OPENROUTER_API_KEY}",
        "Content-Type": "application/json",
    }

    return OPENROUTER_API_URL, headers


def send_request(payload, model):
    api_url, headers = get_api_settings(model)

    response = requests.post(
        api_url,
        headers=headers,
        json=payload,
        timeout=120,
    )

    if not response.ok:
        try:
            error_details = response.json()
        except ValueError:
            error_details = response.text

        raise RuntimeError(
            f"Model request failed with status "
            f"{response.status_code}: {error_details}"
        )

    response_data = response.json()

    if "choices" not in response_data or not response_data["choices"]:
        raise RuntimeError(
            f"The model returned an invalid response: {response_data}"
        )

    content = response_data["choices"][0]["message"]["content"]

    if isinstance(content, list):
        content = " ".join(
            part.get("text", "")
            for part in content
            if isinstance(part, dict)
        )

    if not isinstance(content, str) or not content.strip():
        raise RuntimeError("The model returned an empty response.")

    return content.strip()


def normalize_language(language):
    language = str(language or "english").strip()

    if not re.fullmatch(r"[A-Za-zÀ-ž -]{2,40}", language):
        return "english"

    return language


def parse_image_response(content):
    content = content.strip()

    if content.startswith("```"):
        content = re.sub(r"^```(?:xml)?\s*", "", content)
        content = re.sub(r"\s*```$", "", content)

    heading_match = re.search(
        r"<(?:heading|title)>(.*?)</(?:heading|title)>",
        content,
        re.IGNORECASE | re.DOTALL,
    )

    description_match = re.search(
        r"<description>(.*?)</description>",
        content,
        re.IGNORECASE | re.DOTALL,
    )

    heading = (
        heading_match.group(1).strip()
        if heading_match
        else "Result"
    )

    body = (
        description_match.group(1).strip()
        if description_match
        else content
    )

    return {
        "heading": heading,
        "body": body,
    }


def analyze_image(
    image_bytes,
    model="balanced",
    language="english",
):
    selected_model = get_model(model)
    language = normalize_language(language)

    processed_image = image_preprocessing(image_bytes)
    image_b64 = base64.b64encode(processed_image).decode("utf-8")

    prompt = (
        "Return the answer using exactly two XML tags: "
        "<heading> and <description>. "
        "Inside <heading>, write a short and specific title of "
        "2-6 words that clearly identifies what is visible in the image. "
        "Inside <description>, identify the main subject and describe it "
        "in one concise, factual paragraph of 2-3 sentences. "
        "Include distinctive colors, materials, shapes, context, and any "
        "readable text that would help a visual search engine find similar "
        "objects or scenes. "
        "Avoid decorative language, unsupported assumptions, and phrases "
        "such as 'The image shows'. "
        "Express uncertain identifications cautiously using words such as "
        "'likely', 'possibly', or 'appears to be'. "
        f"Write both the heading and description in {language}. "
        "Use this exact format: "
        "<heading>Specific image title</heading>"
        "<description>Concise factual description.</description>"
    )

    payload = {
        "model": selected_model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt,
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": (
                                "data:image/jpeg;base64,"
                                + image_b64
                            )
                        },
                    },
                ],
            }
        ],
        "max_tokens": 500,
    }

    content = send_request(payload, model)

    return parse_image_response(content)


def parse_history(history):
    if not history:
        return []

    if isinstance(history, str):
        try:
            history = json.loads(history)
        except json.JSONDecodeError as error:
            raise ValueError(
                "History must be valid JSON."
            ) from error

    if not isinstance(history, list):
        raise ValueError("History must be a JSON array.")

    messages = []

    for message in history:
        if not isinstance(message, dict):
            continue

        role = message.get("role")
        content = message.get("content")

        if role not in {"user", "assistant"}:
            continue

        if not isinstance(content, str) or not content.strip():
            continue

        messages.append(
            {
                "role": role,
                "content": content.strip(),
            }
        )

    return messages[-12:]


def follow_up_question(
    history,
    question,
    model="free",
):
    selected_model = get_model(model)
    question = str(question or "").strip()

    if not question:
        raise ValueError("The question cannot be empty.")

    previous_messages = parse_history(history)

    messages = [
        {
            "role": "system",
            "content": (
                "You are the OpenLens visual assistant. "
                "Answer questions about the previously analyzed image using "
                "the heading, description, and conversation history. "
                "Be concise and factual. Do not invent visual details that "
                "were not mentioned. If the available information is not "
                "enough, clearly say that you are uncertain. "
                "Answer in the same language as the user's question."
            ),
        }
    ]

    messages.extend(previous_messages)

    messages.append(
        {
            "role": "user",
            "content": question,
        }
    )

    payload = {
        "model": selected_model,
        "messages": messages,
        "max_tokens": 400,
    }

    return send_request(payload, model)
