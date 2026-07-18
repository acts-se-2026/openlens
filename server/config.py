"""Server config, read from the environment (root .env, same as client.py)."""
import os

from dotenv import load_dotenv

load_dotenv()

# Where Kratos's PUBLIC API lives. FastAPI calls this to validate session tokens.
# Local dev default matches planning/kratos-sandbox (kratos serve on :4433).
KRATOS_PUBLIC_URL = os.getenv("KRATOS_PUBLIC_URL", "http://localhost:4433")
