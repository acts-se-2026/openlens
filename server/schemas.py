"""Request/response shapes for the auth endpoints (never expose the password hash)."""
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field

# Username pattern excludes '@' so a login identifier is never ambiguous with an email.
USERNAME_PATTERN = r"^[A-Za-z0-9_.-]+$"


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=USERNAME_PATTERN)
    email: EmailStr
    # 72 = bcrypt's byte limit; capping here makes the truncation explicit, not silent.
    password: str = Field(min_length=8, max_length=72)


class LoginRequest(BaseModel):
    identifier: str  # email OR username
    password: str


class RefreshRequest(BaseModel):
    refresh_token: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    email: str
    created_at: datetime
