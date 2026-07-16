"""Password hashing, JWT access tokens, refresh-token helpers, and the auth dependency."""
import hashlib
import secrets
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlmodel import Session

from . import config
from .db import get_session
from .models import User

_bearer = HTTPBearer(auto_error=True)
_BCRYPT_MAX_BYTES = 72  # bcrypt only considers the first 72 bytes of the password


def credentials_exception() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )


# ---- passwords (bcrypt) ----
def hash_password(password: str) -> str:
    pw = password.encode("utf-8")[:_BCRYPT_MAX_BYTES]
    return bcrypt.hashpw(pw, bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, hashed: str) -> bool:
    pw = password.encode("utf-8")[:_BCRYPT_MAX_BYTES]
    try:
        return bcrypt.checkpw(pw, hashed.encode("utf-8"))
    except ValueError:
        return False


# ---- access tokens (JWT, HS256) ----
def create_access_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "type": "access",
        "iat": now,
        "exp": now + timedelta(minutes=config.ACCESS_TOKEN_EXPIRE_MINUTES),
    }
    return jwt.encode(payload, config.JWT_SECRET, algorithm=config.JWT_ALGORITHM)


def decode_access_token(token: str) -> dict:
    try:
        payload = jwt.decode(
            token, config.JWT_SECRET, algorithms=[config.JWT_ALGORITHM]
        )
    except jwt.PyJWTError:
        raise credentials_exception()
    if payload.get("type") != "access":
        raise credentials_exception()
    return payload


# ---- refresh tokens (opaque, stored hashed) ----
def generate_refresh_token() -> tuple[str, str]:
    """Return (raw_token, sha256_hash). Only the hash is ever stored."""
    raw = secrets.token_urlsafe(32)
    return raw, hash_refresh_token(raw)


def hash_refresh_token(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


# ---- current-user dependency ----
def get_current_user(
    creds: HTTPAuthorizationCredentials = Depends(_bearer),
    session: Session = Depends(get_session),
) -> User:
    payload = decode_access_token(creds.credentials)
    sub = payload.get("sub")
    try:
        user = session.get(User, int(sub)) if sub is not None else None
    except (TypeError, ValueError):
        user = None
    if user is None:
        raise credentials_exception()
    return user
