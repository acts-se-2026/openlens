# OpenLens

**OpenLens** is an open-source, visual-search app in the spirit of Google Lens: point your camera at an object, and the app detects it, crops it, and describes it — then finds related content across the web.

It was built as an **educational project** during the [**ACTS 2026.2**](https://lp.jetbrains.com/academy/acts/2026.2/) SE Track by [**Milán Fülöp**](https://github.com/milanfulop), [**David-Matei Popa**](https://github.com/pht-poapa), [**Minja Savić**](https://github.com/minjasav92), and [**Nikolina Trajković**](https://github.com/nniiinnnaa), mentored by [**Alexander Kovrigin**](https://github.com/waleko), in Bremen, Germany (July 2026).

## Demo

<table>
  <tr>
    <td align="center" valign="middle">
      <a href="docs/assets/demo.mp4"><img src="docs/assets/demo-scan.webp" alt="OpenLens scanning a chalk box" height="360"></a>
      <br>🎬 <a href="docs/assets/demo.mp4"><b>Watch the demo</b></a>
    </td>
    <td align="center" valign="middle">
      <a href="https://canva.link/90dqa8mdykggjlt"><img src="docs/assets/slides-cover.png" alt="OpenLens presentation" height="360"></a>
      <br>📊 <a href="https://canva.link/90dqa8mdykggjlt"><b>Presentation on Canva</b></a>
    </td>
  </tr>
</table>

## Features

- 📷 **Live camera capture** — Kotlin Multiplatform (Compose) app for **Android and iOS**.
- 🎯 **Automatic object detection** — YOLO finds the main object in frame and draws an adjustable bounding box.
- 🌫️ **Anti-blur** — rejects blurry frames so only sharp images reach the model.
- 👆 **Tap to identify** — focuses on the selected region and identifies it on demand.
- 🧠 **AI vision descriptions** — heading + factual description via `google/gemini-3-flash-preview` on OpenRouter.
- 🖥️ **Self-hosted VLM** — a self-hosted vision-language model for free inference alongside external providers.
- 🔎 **Related content search** — surfaces similar images and cited web sources for the detected object.
- 🎟️ **User credits system** — per-user credit accounting for model calls.
- ⚙️ **FastAPI backend** — image analysis, detection, related search, and token-based auth.

## Architecture

- **`app/`** — Kotlin Multiplatform client (Compose UI, Android + iOS).
- **`server/`** — FastAPI backend (detection, cropping, model calls, related search, auth).
- **`Model/`** — prompt engineering and model-evaluation results.
- **`BoundingBox/`** — object-detection experiments.

---

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
