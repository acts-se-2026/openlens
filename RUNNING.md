# Running OpenLens

OpenLens is two pieces that talk over HTTP:

- **Backend** (`server/` + `client.py`) — a FastAPI server that sends the photo to a vision model and returns `{ "Heading", "Body" }`.
- **App** (`app/`) — a Kotlin Multiplatform / Compose app (Android + iOS) that captures a photo and shows the result.

The app POSTs the photo to the backend at `http://localhost:8000/image_to_model`. On a phone, `localhost` is bridged to your machine with `adb reverse` (see step 3).

Works on **macOS, Windows, and Linux**. Android dev works on any of them; only the **iOS** build needs macOS + Xcode. Commands below are macOS/Linux — Windows equivalents are noted where they differ.

## Prerequisites

- **Backend:** Python 3.12+ and [uv](https://docs.astral.sh/uv/) (cross-platform); an [OpenRouter API key](https://openrouter.ai/keys).
- **App:** Android Studio (or JDK 11 + Android SDK/`adb`); an Android device (USB debugging on) or an emulator.

## 1. Start the backend

From the repo root:

```bash
# one-time: add your key   (Windows: copy .env.example .env)
cp .env.example .env          # then edit .env and set OPENROUTER_API_KEY=...

# install deps
uv sync --project server

# run — MUST be from the repo root so server.py's `from client import` resolves
uv run --project server uvicorn server.server:app --host 0.0.0.0 --port 8000
```

Quick check (returns `{"Heading": "...", "Body": "..."}`):

```bash
curl -F "file=@Model/ImageDataset/Apples.jpg" http://localhost:8000/image_to_model
```

## 2. Run the app

With a device connected (or emulator running):

```bash
cd app
./gradlew :composeApp:installDebug     # Windows: gradlew.bat :composeApp:installDebug
```

…or just open `app/` in Android Studio and hit Run. Grant the camera permission on first launch.

## 3. Bridge the phone to the server

The app calls `localhost:8000`, so point that at your machine's server:

```bash
adb reverse tcp:8000 tcp:8000
```

Run this **every time** you plug the phone back in or restart adb — it drops on disconnect. (Works for emulators too.)

Then in the app: point the camera at something and tap the cyan shutter. You should get a real heading + description; swipe the result sheet up for the full text.

## Troubleshooting

- **"Couldn't reach the server"** → the backend isn't running, or `adb reverse` dropped. Restart the server and re-run `adb reverse tcp:8000 tcp:8000`.
- **Server error / weird result** → check `OPENROUTER_API_KEY` in `.env`.
- **`ModuleNotFoundError: client`** → you started uvicorn from inside `server/`. Run it from the repo root (step 1).
- The app's server URL is hardcoded to `http://localhost:8000` (`RemoteScanRepository.kt`) — fine for local dev over `adb reverse`.
