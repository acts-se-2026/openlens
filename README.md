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

The vision model receives an image and generates a heading and a short factual description.

### Selected Model

The selected model is:

- `openai/gpt-5.5`
- Accessed through the OpenRouter API

The model was selected after testing multiple vision models on the same image dataset using the same prompt.

GPT-5.5 achieved the best overall result because it:

- Identified the main subjects accurately
- Included useful visual details
- Followed the requested format consistently
- Produced clear and factual descriptions
- Avoided unnecessary or unsupported information

The model evaluation files and results are available in:

```text
Model/ModelEvaluation
