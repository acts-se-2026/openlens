"""Server config, read from the environment (root .env, same as client.py)."""
import os

from dotenv import load_dotenv

load_dotenv()

# Where Kratos's PUBLIC API lives. FastAPI calls this to validate session tokens.
# Local dev default matches planning/kratos-sandbox (kratos serve on :4433).
KRATOS_PUBLIC_URL = os.getenv("KRATOS_PUBLIC_URL", "http://localhost:4433")

# DEV-ONLY auth bypass. When set, sending this exact string as the bearer token authenticates as a
# fixed dev identity WITHOUT contacting Kratos — so teammates can hit the API without registering.
# Unset (None) by default, so it's inert unless a dev opts in. MUST stay empty in any deployment:
# a non-empty value here is a standing backdoor. `or None` treats an empty string as "off".
DEV_TEST_TOKEN = os.getenv("DEV_TEST_TOKEN") or None
