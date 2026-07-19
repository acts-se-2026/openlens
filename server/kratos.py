"""Kratos relying-party glue: validate a session token by asking Kratos who it belongs to.

FastAPI stores no credentials and signs nothing. On every protected request it forwards the
caller's Kratos session token to Kratos's `/sessions/whoami` and trusts the answer. That's the
whole "relying party" idea — identity lives in Kratos, we just check with it.
"""
import secrets

import httpx
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

import config

# The app sends `Authorization: Bearer <kratos_session_token>`. auto_error=True → a missing or
# malformed header is a 403 before our code runs; a present-but-invalid token becomes a 401 below.
_bearer = HTTPBearer(auto_error=True)

# Fixed identity returned for the shared dev test token (see config.DEV_TEST_TOKEN). Its wallet is
# auto-provisioned like any real user's, so all dev testing is metered against this one account.
_DEV_IDENTITY = {"id": "dev-test-user", "traits": {"email": "dev@openlens.test", "name": "Dev Tester"}}


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
    # DEV-ONLY shortcut: a configured static token authenticates as a fixed identity without Kratos.
    # Inert unless config.DEV_TEST_TOKEN is set; compared in constant time so it can't be probed by
    # timing. Real Kratos sessions are unaffected — they never equal the dev token.
    if config.DEV_TEST_TOKEN and secrets.compare_digest(creds.credentials, config.DEV_TEST_TOKEN):
        return _DEV_IDENTITY

    session = await whoami(creds.credentials)
    if session is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired session",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return session["identity"]
