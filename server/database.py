import sqlite3
from contextlib import contextmanager
from pathlib import Path

DB_PATH = Path(__file__).parent / "tokens.db"


def init_db():
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                user_id TEXT PRIMARY KEY,
                tokens INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        conn.commit()


@contextmanager
def get_conn():
    conn = sqlite3.connect(DB_PATH)
    try:
        yield conn
    finally:
        conn.close()


def user_exists(user_id: str) -> bool:
    with get_conn() as conn:
        row = conn.execute(
            "SELECT 1 FROM users WHERE user_id = ?", (user_id,)
        ).fetchone()
    return row is not None


def get_token_balance(user_id: str) -> int | None:
    with get_conn() as conn:
        row = conn.execute(
            "SELECT tokens FROM users WHERE user_id = ?", (user_id,)
        ).fetchone()
    return row[0] if row else None


def try_decrement_tokens(user_id: str, amount: int) -> bool:
    with get_conn() as conn:
        cursor = conn.execute(
            "UPDATE users SET tokens = tokens - ? WHERE user_id = ? AND tokens >= ?",
            (amount, user_id, amount),
        )
        conn.commit()
        return cursor.rowcount > 0


def refund_tokens(user_id: str, amount: int) -> None:
    with get_conn() as conn:
        conn.execute(
            "UPDATE users SET tokens = tokens + ? WHERE user_id = ?",
            (amount, user_id),
        )
        conn.commit()


def create_user(user_id: str, initial_tokens: int = 0) -> None:
    with get_conn() as conn:
        conn.execute(
            "INSERT OR IGNORE INTO users (user_id, tokens) VALUES (?, ?)",
            (user_id, initial_tokens),
        )
        conn.commit()