"""Auth endpoints: register, login, refresh, logout, me."""
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select

from . import config
from .db import get_session
from .models import RefreshToken, User, utcnow
from .schemas import LoginRequest, RefreshRequest, RegisterRequest, TokenPair, UserRead
from .security import (
    create_access_token,
    generate_refresh_token,
    get_current_user,
    hash_password,
    hash_refresh_token,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])


def _issue_token_pair(session: Session, user: User) -> TokenPair:
    """Mint a fresh access token + a new persisted refresh token for this user."""
    raw_refresh, token_hash = generate_refresh_token()
    session.add(
        RefreshToken(
            user_id=user.id,
            token_hash=token_hash,
            expires_at=utcnow() + timedelta(days=config.REFRESH_TOKEN_EXPIRE_DAYS),
        )
    )
    session.commit()
    return TokenPair(
        access_token=create_access_token(user.id),
        refresh_token=raw_refresh,
    )


@router.post("/register", response_model=TokenPair, status_code=status.HTTP_201_CREATED)
def register(body: RegisterRequest, session: Session = Depends(get_session)) -> TokenPair:
    email = body.email.lower()
    clash = session.exec(
        select(User).where((User.email == email) | (User.username == body.username))
    ).first()
    if clash is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username or email already registered",
        )
    user = User(
        username=body.username,
        email=email,
        hashed_password=hash_password(body.password),
    )
    session.add(user)
    try:
        session.commit()
    except IntegrityError:  # lost a uniqueness race between the check and the insert
        session.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username or email already registered",
        )
    session.refresh(user)
    return _issue_token_pair(session, user)


@router.post("/login", response_model=TokenPair)
def login(body: LoginRequest, session: Session = Depends(get_session)) -> TokenPair:
    identifier = body.identifier.strip()
    # identifier may be an email (compared lowercased) or a username (case-sensitive).
    user = session.exec(
        select(User).where(
            (User.email == identifier.lower()) | (User.username == identifier)
        )
    ).first()
    if user is None or not verify_password(body.password, user.hashed_password):
        # Generic message — never reveal whether the account exists.
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials"
        )
    return _issue_token_pair(session, user)


def _find_active_refresh(session: Session, raw_token: str) -> RefreshToken | None:
    record = session.exec(
        select(RefreshToken).where(
            RefreshToken.token_hash == hash_refresh_token(raw_token)
        )
    ).first()
    if record is None or record.revoked or record.expires_at < utcnow():
        return None
    return record


@router.post("/refresh", response_model=TokenPair)
def refresh(body: RefreshRequest, session: Session = Depends(get_session)) -> TokenPair:
    record = _find_active_refresh(session, body.refresh_token)
    if record is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
        )
    user = session.get(User, record.user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
        )
    # Rotate: revoke the used token, then hand out a fresh pair (both committed together).
    record.revoked = True
    session.add(record)
    return _issue_token_pair(session, user)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(body: RefreshRequest, session: Session = Depends(get_session)) -> Response:
    record = _find_active_refresh(session, body.refresh_token)
    if record is not None:
        record.revoked = True
        session.add(record)
        session.commit()
    # 204 even if the token was already unknown/revoked — logout is idempotent.
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/me", response_model=UserRead)
def me(current_user: User = Depends(get_current_user)) -> User:
    return current_user
