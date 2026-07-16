"""Auth/config settings, read from the environment (root .env, same as client.py)."""
import os

from dotenv import load_dotenv

load_dotenv()

# Signing key for JWTs. Required — generate one with:
#   python -c "import secrets; print(secrets.token_urlsafe(32))"
JWT_SECRET = os.getenv("JWT_SECRET")
if not JWT_SECRET:
    raise RuntimeError(
        "JWT_SECRET is not set. Add it to your .env — see .env.example."
    )

JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "30"))
REFRESH_TOKEN_EXPIRE_DAYS = int(os.getenv("REFRESH_TOKEN_EXPIRE_DAYS", "30"))

# File-based SQLite by default (repo root, since we run uvicorn from there).
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./openlens.db")
