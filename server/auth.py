"""Auth endpoints: register, login (refresh/logout/me added below)."""
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select

from . import config
from .db import get_session
from .models import RefreshToken, User, utcnow
from .schemas import LoginRequest, RegisterRequest, TokenPair
from .security import (
    create_access_token,
    generate_refresh_token,
    hash_password,
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
