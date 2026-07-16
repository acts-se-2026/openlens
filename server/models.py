"""SQLModel tables for the auth store."""
from datetime import datetime, timezone

from sqlmodel import Field, SQLModel


def utcnow() -> datetime:
    """Naive UTC 'now'. Kept naive so it compares cleanly with values SQLite reads back
    (SQLite doesn't persist tzinfo, so stored datetimes come back naive)."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    username: str = Field(index=True, unique=True)
    email: str = Field(index=True, unique=True)  # stored lowercased
    hashed_password: str
    created_at: datetime = Field(default_factory=utcnow)


class RefreshToken(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    token_hash: str = Field(index=True, unique=True)  # sha256 of the opaque token
    expires_at: datetime
    revoked: bool = Field(default=False)
    created_at: datetime = Field(default_factory=utcnow)


# Future one-to-many off User — same shape, ~10 lines:
# class ScanHistory(SQLModel, table=True):
#     id: int | None = Field(default=None, primary_key=True)
#     user_id: int = Field(foreign_key="user.id", index=True)
#     heading: str
#     body: str
#     created_at: datetime = Field(default_factory=utcnow)
