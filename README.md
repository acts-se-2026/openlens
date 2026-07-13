# OpenLens - Google Lens Clone
# Server 
# Requirement for the server
 
- Python >= 3.12
- [uv](https://docs.astral.sh/uv/) installed

# Running the server
-type command in promt or terminal

- uv run uvicorn server:app --reload


 - server:app refers to the app FastAPI instance in server.py

# Vision Model

The application uses:

- `google/gemini-3-flash-preview`
- OpenRouter API

Gemini 3 Flash was selected because it provides the best balance between image-description quality, response time and cost.

Evaluation results are available in:

```text
Model/ModelEvaluation
```

# Final Prompt

The prompt asks the model to generate:

- A short heading identifying the image
- A factual description of 2–3 sentences
- Relevant visual details and readable text
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
