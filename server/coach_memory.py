from __future__ import annotations

import json
import re
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Iterable, Literal, Sequence

from pydantic import BaseModel, Field, field_validator, model_validator

try:  # server modules are imported both from app.py and directly by tests
    from auth import UserContext
    from health_store import connect, utc_now
except ModuleNotFoundError:  # pragma: no cover - package-style imports are a fallback
    from .auth import UserContext  # type: ignore[no-redef]
    from src.health_store import connect, utc_now  # type: ignore[no-redef]


MAX_MEMORY_ITEMS = 120
MAX_CONTEXT_ITEMS = 40
MAX_RELATED_HISTORY = 6
MAX_UPDATES_PER_TURN = 8
MAX_MEMORY_KEY_LENGTH = 80
MAX_MEMORY_TEXT_LENGTH = 500
MAX_HISTORY_CONTENT_LENGTH = 1000

MemoryCategory = Literal[
    "goals",
    "routines",
    "preferences",
    "constraints",
    "context",
    "response_style",
]
MemoryConfidence = Literal["user_stated", "tentative"]

_KEY_RE = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,79}$")
_CJK_SEQUENCE_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]+")
# ``\w`` is Unicode-aware in Python: this keeps accented Latin, Arabic, and
# other user-language words together while excluding underscores from slugs.
_WORD_RE = re.compile(r"[^\W_]+", re.UNICODE)


class CoachMemoryError(ValueError):
    """A caller-safe validation or persistence error for coach memory."""


class MemoryCapacityError(CoachMemoryError):
    """Raised when a turn would exceed the stable long-term memory capacity."""


class MemoryUpdate(BaseModel):
    """A model-proposed memory mutation, validated before it touches a user DB."""

    action: Literal["upsert", "delete"]
    key: str = Field(min_length=1, max_length=MAX_MEMORY_KEY_LENGTH)
    category: MemoryCategory | None = None
    text: str | None = Field(default=None, max_length=MAX_MEMORY_TEXT_LENGTH)
    confidence: MemoryConfidence | None = None
    source_message_ids: list[str] = Field(default_factory=list, max_length=16)

    @field_validator("key")
    @classmethod
    def validate_key(cls, value: str) -> str:
        key = value.strip()
        if not _KEY_RE.fullmatch(key):
            raise ValueError("memory key must be a lowercase stable slug of at most 80 characters")
        return key

    @field_validator("text")
    @classmethod
    def normalize_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        text = value.strip()
        return text or None

    @field_validator("source_message_ids")
    @classmethod
    def normalize_sources(cls, values: list[str]) -> list[str]:
        normalized = [value.strip() for value in values if value and value.strip()]
        if len(normalized) != len(set(normalized)):
            raise ValueError("source_message_ids must be unique")
        if any(len(value) > 160 for value in normalized):
            raise ValueError("source message id is too long")
        return normalized

    @model_validator(mode="after")
    def validate_action_fields(self) -> "MemoryUpdate":
        if self.action == "upsert":
            if self.category is None or self.text is None or self.confidence is None:
                raise ValueError("upsert requires category, text, and confidence")
        return self


_MEMORY_SCHEMA = """
CREATE TABLE IF NOT EXISTS coach_memory (
    key TEXT PRIMARY KEY,
    category TEXT NOT NULL CHECK(category IN ('goals','routines','preferences','constraints','context','response_style')),
    text TEXT NOT NULL,
    confidence TEXT NOT NULL CHECK(confidence IN ('user_stated','tentative')),
    source_message_ids TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS coach_memory_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key TEXT NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('upsert','delete')),
    category TEXT,
    text TEXT,
    confidence TEXT,
    source_message_ids TEXT NOT NULL DEFAULT '[]',
    previous_text TEXT,
    changed_at TEXT NOT NULL,
    current_thread_id TEXT,
    current_user_message_id TEXT,
    source_kind TEXT NOT NULL DEFAULT 'model_update'
);
CREATE INDEX IF NOT EXISTS idx_coach_memory_history_key_time
    ON coach_memory_history(key, id DESC);
"""


@contextmanager
def _memory_connection(user: UserContext):
    """Open only the authenticated user's health DB and initialize local tables."""

    con = connect(user.health_db)
    try:
        con.executescript(_MEMORY_SCHEMA)
        con.commit()
        yield con
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()


def _json_ids(value: str | None) -> list[str]:
    try:
        parsed = json.loads(value or "[]")
    except (TypeError, ValueError, json.JSONDecodeError):
        return []
    return [str(item) for item in parsed if str(item).strip()]


def _dump_ids(values: Iterable[str]) -> str:
    return json.dumps(list(dict.fromkeys(values)), ensure_ascii=False, separators=(",", ":"))


def _validate_key(key: str) -> str:
    value = key.strip()
    if not _KEY_RE.fullmatch(value):
        raise CoachMemoryError("memory key must be a lowercase stable slug of at most 80 characters")
    return value


def _validate_current_message(
    con: sqlite3.Connection,
    current_thread_id: str,
    current_user_message_id: str,
) -> sqlite3.Row:
    thread_id = current_thread_id.strip()
    message_id = current_user_message_id.strip()
    if not thread_id or not message_id:
        raise CoachMemoryError("current_thread_id and current_user_message_id are required")
    row = con.execute(
        "SELECT id,thread_id,role,content,created_at FROM chat_messages WHERE id=?",
        (message_id,),
    ).fetchone()
    if row is None or row["role"] != "user" or row["thread_id"] != thread_id:
        raise CoachMemoryError("current_user_message_id must be a user message in current_thread_id")
    return row


def _validate_sources(
    con: sqlite3.Connection,
    update: MemoryUpdate,
    current_user_message_id: str,
) -> dict[str, sqlite3.Row]:
    if not update.source_message_ids:
        raise CoachMemoryError("each memory update needs explicit user source_message_ids")
    if current_user_message_id not in update.source_message_ids:
        raise CoachMemoryError("each memory update must cite current_user_message_id")
    placeholders = ",".join("?" for _ in update.source_message_ids)
    rows = con.execute(
        f"SELECT id,thread_id,role,content,created_at FROM chat_messages WHERE id IN ({placeholders})",
        tuple(update.source_message_ids),
    ).fetchall()
    by_id = {str(row["id"]): row for row in rows}
    if set(by_id) != set(update.source_message_ids) or any(row["role"] != "user" for row in by_id.values()):
        raise CoachMemoryError("source_message_ids must refer only to existing user messages in this DB")
    return by_id


def _normalize_updates(updates: Sequence[MemoryUpdate | dict[str, Any]]) -> list[MemoryUpdate]:
    if len(updates) > MAX_UPDATES_PER_TURN:
        raise CoachMemoryError(f"at most {MAX_UPDATES_PER_TURN} memory updates are allowed per turn")
    try:
        return [item if isinstance(item, MemoryUpdate) else MemoryUpdate.model_validate(item) for item in updates]
    except Exception as exc:
        if isinstance(exc, CoachMemoryError):
            raise
        raise CoachMemoryError(str(exc)) from exc


def _source_details(con: sqlite3.Connection, source_ids: Sequence[str]) -> list[dict[str, str]]:
    if not source_ids:
        return []
    placeholders = ",".join("?" for _ in source_ids)
    rows = con.execute(
        f"SELECT id,thread_id,role,created_at FROM chat_messages WHERE id IN ({placeholders})",
        tuple(source_ids),
    ).fetchall()
    by_id = {str(row["id"]): row for row in rows}
    return [
        {
            "id": source_id,
            "thread_id": str(by_id[source_id]["thread_id"]),
            "role": str(by_id[source_id]["role"]),
            "created_at": str(by_id[source_id]["created_at"]),
        }
        for source_id in source_ids
        if source_id in by_id
    ]


def _memory_item(row: sqlite3.Row, con: sqlite3.Connection) -> dict[str, Any]:
    source_ids = _json_ids(row["source_message_ids"])
    return {
        "key": str(row["key"]),
        "category": str(row["category"]),
        "text": str(row["text"]),
        "confidence": str(row["confidence"]),
        "source_message_ids": source_ids,
        "source_messages": _source_details(con, source_ids),
        "created_at": str(row["created_at"]),
        "updated_at": str(row["updated_at"]),
    }


def _list_memory_items(con: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = con.execute(
        "SELECT key,category,text,confidence,source_message_ids,created_at,updated_at "
        "FROM coach_memory ORDER BY updated_at DESC,key ASC LIMIT ?",
        (MAX_MEMORY_ITEMS,),
    ).fetchall()
    return [_memory_item(row, con) for row in rows]


def list_memory_items(user: UserContext) -> list[dict[str, Any]]:
    """Return every active memory item from this user's DB for an authenticated UI."""

    with _memory_connection(user) as con:
        return _list_memory_items(con)


def _latest_forgotten(con: sqlite3.Connection) -> tuple[list[str], set[str], set[str]]:
    rows = con.execute(
        "SELECT key,action,source_message_ids,id FROM coach_memory_history ORDER BY id ASC"
    ).fetchall()
    latest: dict[str, sqlite3.Row] = {}
    for row in rows:
        latest[str(row["key"])] = row
    active = {str(row["key"]) for row in con.execute("SELECT key FROM coach_memory").fetchall()}
    forgotten_rows = [row for key, row in latest.items() if row["action"] == "delete" and key not in active]
    forgotten_keys = sorted(str(row["key"]) for row in forgotten_rows)
    source_ids: set[str] = set()
    for row in forgotten_rows:
        # Include every source used by earlier overwrites up to this tombstone,
        # so an old value from another thread cannot re-enter through history.
        history_rows = con.execute(
            "SELECT source_message_ids FROM coach_memory_history WHERE key=? AND id<=?",
            (str(row["key"]), int(row["id"])),
        ).fetchall()
        for history_row in history_rows:
            source_ids.update(_json_ids(history_row["source_message_ids"]))
    if source_ids:
        placeholders = ",".join("?" for _ in source_ids)
        thread_rows = con.execute(
            f"SELECT thread_id FROM chat_messages WHERE id IN ({placeholders})",
            tuple(source_ids),
        ).fetchall()
        forgotten_threads = {str(row["thread_id"]) for row in thread_rows}
    else:
        forgotten_threads = set()
    return forgotten_keys[:MAX_MEMORY_ITEMS], source_ids, forgotten_threads


def _tokens(text: str) -> set[str]:
    lowered = text.casefold()
    tokens = set(_WORD_RE.findall(lowered))
    for sequence in _CJK_SEQUENCE_RE.findall(lowered):
        tokens.update(sequence)
        tokens.update(sequence[index : index + 2] for index in range(len(sequence) - 1))
    return tokens


def _overlap_score(question_tokens: set[str], text: str) -> int:
    return len(question_tokens.intersection(_tokens(text))) if question_tokens else 0


def _timestamp_value(value: str) -> float:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0.0


def _related_history(
    con: sqlite3.Connection,
    current_question: str,
    exclude_thread_id: str | None,
    forgotten_source_ids: set[str],
    forgotten_threads: set[str],
) -> list[dict[str, Any]]:
    rows = con.execute(
        "SELECT id,thread_id,role,content,created_at FROM chat_messages "
        "WHERE role IN ('user','assistant') ORDER BY created_at DESC LIMIT 1000"
    ).fetchall()
    question_tokens = _tokens(current_question)
    candidates: list[tuple[int, str, sqlite3.Row]] = []
    for row in rows:
        thread_id = str(row["thread_id"])
        message_id = str(row["id"])
        if exclude_thread_id and thread_id == exclude_thread_id:
            continue
        if message_id in forgotten_source_ids or thread_id in forgotten_threads:
            continue
        content = str(row["content"] or "")
        score = _overlap_score(question_tokens, content)
        candidates.append((score, str(row["created_at"]), row))
    candidates.sort(key=lambda item: (-item[0], -_timestamp_value(item[1])))
    return [
        {
            "id": str(row["id"]),
            "thread_id": str(row["thread_id"]),
            "role": str(row["role"]),
            "content": str(row["content"] or "")[:MAX_HISTORY_CONTENT_LENGTH],
            "created_at": str(row["created_at"]),
        }
        for _, _, row in candidates[:MAX_RELATED_HISTORY]
    ]


def memory_context(
    user: UserContext,
    current_question: str,
    exclude_thread_id: str | None = None,
) -> dict[str, Any]:
    """Return scoped memory plus related cross-thread history for one user."""

    with _memory_connection(user) as con:
        items = _list_memory_items(con)
        question_tokens = _tokens(current_question)

        def score(item: dict[str, Any]) -> tuple[int, int, float, str]:
            relevance = _overlap_score(question_tokens, item["text"])
            category_bonus = 1 if item["category"] in {"goals", "preferences"} else 0
            return (-relevance, -category_bonus, -_timestamp_value(str(item["updated_at"])), str(item["key"]))

        items.sort(key=score)
        forgotten_keys, forgotten_source_ids, forgotten_threads = _latest_forgotten(con)
        return {
            "items": items[:MAX_CONTEXT_ITEMS],
            "forgotten_keys": forgotten_keys,
            "related_history": _related_history(
                con,
                current_question,
                exclude_thread_id,
                forgotten_source_ids,
                forgotten_threads,
            ),
        }


def _record_history(
    con: sqlite3.Connection,
    *,
    key: str,
    action: str,
    category: str | None,
    text: str | None,
    confidence: str | None,
    source_message_ids: Sequence[str],
    previous_text: str | None,
    changed_at: str,
    current_thread_id: str | None,
    current_user_message_id: str | None,
    source_kind: str,
) -> None:
    con.execute(
        """INSERT INTO coach_memory_history
           (key,action,category,text,confidence,source_message_ids,previous_text,
            changed_at,current_thread_id,current_user_message_id,source_kind)
           VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
        (
            key,
            action,
            category,
            text,
            confidence,
            _dump_ids(source_message_ids),
            previous_text,
            changed_at,
            current_thread_id,
            current_user_message_id,
            source_kind,
        ),
    )


def _all_source_ids_for_key(con: sqlite3.Connection, key: str, current_ids: Sequence[str]) -> list[str]:
    ids = list(current_ids)
    rows = con.execute(
        "SELECT source_message_ids FROM coach_memory_history WHERE key=?",
        (key,),
    ).fetchall()
    for row in rows:
        ids.extend(_json_ids(row["source_message_ids"]))
    return list(dict.fromkeys(ids))


def apply_memory_updates(
    user: UserContext,
    updates: Sequence[MemoryUpdate | dict[str, Any]],
    current_thread_id: str,
    current_user_message_id: str,
) -> dict[str, Any]:
    """Apply a bounded, source-validated model turn atomically to one user DB."""

    normalized = _normalize_updates(updates)
    if not normalized:
        return {"updated_keys": [], "deleted_keys": [], "ignored_keys": [], "forgotten_keys": []}
    with _memory_connection(user) as con:
        _validate_current_message(con, current_thread_id, current_user_message_id)
        for update in normalized:
            _validate_sources(con, update, current_user_message_id)

        rows = con.execute("SELECT key,category,text,confidence,source_message_ids,created_at,updated_at FROM coach_memory").fetchall()
        existing = {str(row["key"]): row for row in rows}
        new_keys = {update.key for update in normalized if update.action == "upsert" and update.key not in existing}
        if len(existing) + len(new_keys) > MAX_MEMORY_ITEMS:
            raise MemoryCapacityError(f"long-term coach memory is limited to {MAX_MEMORY_ITEMS} items")

        now = utc_now()
        updated_keys: list[str] = []
        deleted_keys: list[str] = []
        ignored_keys: list[str] = []
        for update in normalized:
            old = existing.get(update.key)
            if update.action == "upsert":
                created_at = str(old["created_at"]) if old else now
                con.execute(
                    """INSERT INTO coach_memory(key,category,text,confidence,source_message_ids,created_at,updated_at)
                       VALUES(?,?,?,?,?,?,?)
                       ON CONFLICT(key) DO UPDATE SET category=excluded.category,text=excluded.text,
                         confidence=excluded.confidence,source_message_ids=excluded.source_message_ids,
                         updated_at=excluded.updated_at""",
                    (
                        update.key,
                        update.category,
                        update.text,
                        update.confidence,
                        _dump_ids(update.source_message_ids),
                        created_at,
                        now,
                    ),
                )
                _record_history(
                    con,
                    key=update.key,
                    action="upsert",
                    category=update.category,
                    text=update.text,
                    confidence=update.confidence,
                    source_message_ids=update.source_message_ids,
                    previous_text=str(old["text"]) if old else None,
                    changed_at=now,
                    current_thread_id=current_thread_id,
                    current_user_message_id=current_user_message_id,
                    source_kind="model_update",
                )
                existing[update.key] = con.execute(
                    "SELECT key,category,text,confidence,source_message_ids,created_at,updated_at FROM coach_memory WHERE key=?",
                    (update.key,),
                ).fetchone()
                updated_keys.append(update.key)
            elif old is None:
                ignored_keys.append(update.key)
            else:
                con.execute("DELETE FROM coach_memory WHERE key=?", (update.key,))
                deleted_source_ids = _all_source_ids_for_key(con, update.key, _json_ids(old["source_message_ids"]))
                _record_history(
                    con,
                    key=update.key,
                    action="delete",
                    category=str(old["category"]),
                    text=str(old["text"]),
                    confidence=str(old["confidence"]),
                    source_message_ids=deleted_source_ids,
                    previous_text=str(old["text"]),
                    changed_at=now,
                    current_thread_id=current_thread_id,
                    current_user_message_id=current_user_message_id,
                    source_kind="model_update",
                )
                existing.pop(update.key, None)
                deleted_keys.append(update.key)
        forgotten_keys, _, _ = _latest_forgotten(con)
        return {
            "updated_keys": updated_keys,
            "deleted_keys": deleted_keys,
            "ignored_keys": ignored_keys,
            "forgotten_keys": forgotten_keys,
        }


def forget_memory_item(user: UserContext, key: str) -> bool:
    """Delete one item after an explicit user action, leaving an audit tombstone."""

    memory_key = _validate_key(key)
    with _memory_connection(user) as con:
        row = con.execute(
            "SELECT key,category,text,confidence,source_message_ids FROM coach_memory WHERE key=?",
            (memory_key,),
        ).fetchone()
        if row is None:
            return False
        now = utc_now()
        con.execute("DELETE FROM coach_memory WHERE key=?", (memory_key,))
        deleted_source_ids = _all_source_ids_for_key(con, memory_key, _json_ids(row["source_message_ids"]))
        _record_history(
            con,
            key=memory_key,
            action="delete",
            category=str(row["category"]),
            text=str(row["text"]),
            confidence=str(row["confidence"]),
            source_message_ids=deleted_source_ids,
            previous_text=str(row["text"]),
            changed_at=now,
            current_thread_id=None,
            current_user_message_id=None,
            source_kind="user_action",
        )
        return True
