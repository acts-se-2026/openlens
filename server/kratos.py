"""Kratos relying-party glue: validate a session token by asking Kratos who it belongs to.

FastAPI stores no credentials and signs nothing. On every protected request it forwards the
caller's Kratos session token to Kratos's `/sessions/whoami` and trusts the answer. That's the
whole "relying party" idea — identity lives in Kratos, we just check with it.
"""
import httpx
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import config

# The app sends `Authorization: Bearer <kratos_session_token>`. auto_error=True → a missing or
# malformed header is a 403 before our code runs; a present-but-invalid token becomes a 401 below.
_bearer = HTTPBearer(auto_error=True)


async def whoami(session_token: str) -> dict | None:
    """Ask Kratos to resolve a session token to an identity. None if the session isn't valid.

    Kratos wants API-flow session tokens in the `X-Session-Token` header (not `Authorization`).
    A 200 means the session is active and returns the full session (incl. identity + traits);
    401/403 means expired/unknown/revoked.
    """
    url = f"{config.KRATOS_PUBLIC_URL}/sessions/whoami"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url, headers={"X-Session-Token": session_token})
    except httpx.HTTPError:
        # Kratos unreachable — treat as "can't authorize" rather than crashing the request.
        return None
    if resp.status_code == 200:
        return resp.json()
    return None


async def get_current_identity(
    creds: HTTPAuthorizationCredentials = Depends(_bearer),
) -> dict:
    """FastAPI dependency: 401 unless the bearer token maps to an active Kratos session.

    Returns the Kratos `identity` object (id, traits like email/name, …) for the caller.
    """
    session = await whoami(creds.credentials)
    if session is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired session",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return session["identity"]
