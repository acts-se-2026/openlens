# OpenLens - Google Lens Clone
# Server 
# Requirement for the server
 
- Python >= 3.12
- [uv](https://docs.astral.sh/uv/) installed

# Running the server
-type command in promt or terminal

- uv run uvicorn server:app --reload


 - server:app refers to the app FastAPI instance in server.py

## Vision Model

The application uses:

- `openai/gpt-5.5`
- OpenRouter API

GPT-5.5 was selected after testing multiple vision models. It produced the most accurate and consistent image descriptions.

Evaluation results are available in:

```text
Model/ModelEvaluation
```

## Final Prompt

The prompt asks the model to generate:

- A short heading identifying the image
- A factual description of 2–3 sentences
- Details such as colors, materials, shapes and visible text
- The response inside `<heading>` and `<description>` tags

Example output:

```xml
<heading>Red Sports Bicycle</heading>
<description>A red bicycle with black tires is parked beside a concrete wall.</description>
```

The final prompt is available in:

```text
Model/BestPrompt
```
