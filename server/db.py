"""SQLite engine + session wiring for the auth store."""
from collections.abc import Iterator

from sqlalchemy import event
from sqlmodel import Session, SQLModel, create_engine

from .config import DATABASE_URL

_is_sqlite = DATABASE_URL.startswith("sqlite")
# check_same_thread=False: FastAPI may touch the connection from a threadpool thread.
connect_args = {"check_same_thread": False} if _is_sqlite else {}
engine = create_engine(DATABASE_URL, connect_args=connect_args)

if _is_sqlite:
    @event.listens_for(engine, "connect")
    def _set_sqlite_pragmas(dbapi_conn, _record):
        cur = dbapi_conn.cursor()
        cur.execute("PRAGMA journal_mode=WAL")  # better concurrent reads
        cur.execute("PRAGMA foreign_keys=ON")   # enforce FK constraints
        cur.close()


def init_db() -> None:
    """Create tables. Import models first so they register on SQLModel.metadata."""
    from . import models  # noqa: F401  (populates metadata)

    SQLModel.metadata.create_all(engine)


def get_session() -> Iterator[Session]:
    with Session(engine) as session:
        yield session
