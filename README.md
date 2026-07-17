# OpenLens - Google Lens Clone
# Server

# Server-model connection

This part of the project connects the backend server with the AI model. When the server receives an image, it sends it to the model and waits for the response. After that, the result is returned to the server so it can be shown to the user.

The API key is stored in an .env file so it's not included in the source code.

To start the server you have to install the required dependencies and run the FastAPI application with Uvicorn.
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
# Tests
Open your Command Prompt or Powershell, open the project folder and run command:
```text
pytest
```

# Tests
In order to run testing files you have to open Command Promt or PowerShell, open the folder you are storing the project into and run command:
```text
pytest -s -v
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
