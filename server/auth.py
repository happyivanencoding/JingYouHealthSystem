from __future__ import annotations

import json
import os
import secrets
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import jwt
from jwt import PyJWKClient

ROOT = Path(__file__).resolve().parents[1]
APP_DATA = ROOT / "data" / "app"
PROFILES_PATH = APP_DATA / "profiles.json"
APP_DB = APP_DATA / "app.db"
SESSION_DAYS = 90


@dataclass(frozen=True)
class UserContext:
    user_id: str
    display_name: str
    role: str
    auth_email: str

    @property
    def root(self) -> Path:
        return ROOT / "data" / "users" / self.user_id

    @property
    def health_db(self) -> Path:
        return self.root / "health.db"

    @property
    def garmin_home(self) -> Path:
        return self.root / "garmin"


def _profiles() -> dict[str, Any]:
    return json.loads(PROFILES_PATH.read_text(encoding="utf-8-sig"))


def all_users() -> list[UserContext]:
    users: list[UserContext] = []
    for value in _profiles().values():
        users.append(
            UserContext(
                user_id=str(value["user_id"]),
                display_name=str(value["display_name"]),
                role=str(value["role"]),
                auth_email=str(value.get("auth_email") or "").lower(),
            )
        )
    return users


def user_by_id(user_id: str) -> UserContext | None:
    return next((user for user in all_users() if user.user_id == user_id), None)


def user_by_email(email: str) -> UserContext | None:
    needle = email.strip().lower()
    return next((user for user in all_users() if user.auth_email == needle), None)


def user_by_profile(profile: str) -> UserContext | None:
    needle = profile.strip().casefold()
    for user in all_users():
        if user.display_name.casefold() == needle:
            return user
        if needle == "owner" and user.role.casefold() == "owner":
            return user
        if needle == "member" and user.role.casefold() == "member":
            return user
    return None


def _app_connection() -> sqlite3.Connection:
    APP_DATA.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(APP_DB)
    con.row_factory = sqlite3.Row
    con.executescript(
        """
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS sessions (
            token TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            created_at TEXT NOT NULL,
            expires_at TEXT NOT NULL,
            source TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
        """
    )
    return con


def issue_session(user: UserContext, source: str) -> str:
    now = datetime.now(timezone.utc)
    expires = now + timedelta(days=SESSION_DAYS)
    token = secrets.token_urlsafe(48)
    with _app_connection() as con:
        con.execute("DELETE FROM sessions WHERE expires_at < ?", (now.isoformat(),))
        con.execute(
            "INSERT INTO sessions(token,user_id,created_at,expires_at,source) VALUES(?,?,?,?,?)",
            (token, user.user_id, now.isoformat(), expires.isoformat(), source),
        )
    return token


def resolve_session(token: str | None) -> UserContext | None:
    if not token:
        return None
    now = datetime.now(timezone.utc).isoformat()
    with _app_connection() as con:
        row = con.execute(
            "SELECT user_id FROM sessions WHERE token=? AND expires_at>=?",
            (token, now),
        ).fetchone()
    return user_by_id(str(row["user_id"])) if row else None


def revoke_session(token: str) -> None:
    with _app_connection() as con:
        con.execute("DELETE FROM sessions WHERE token=?", (token,))


def validate_cloudflare_access_jwt(token: str) -> dict[str, Any]:
    team_domain = os.getenv("JINGYOU_CF_TEAM_DOMAIN", "").strip().rstrip("/")
    audience = os.getenv("JINGYOU_CF_AUD", "").strip()
    if not team_domain or not audience:
        raise RuntimeError("Cloudflare Access validation is not configured")
    if not team_domain.startswith("https://"):
        team_domain = f"https://{team_domain}"
    jwks_client = PyJWKClient(f"{team_domain}/cdn-cgi/access/certs")
    signing_key = jwks_client.get_signing_key_from_jwt(token)
    return jwt.decode(
        token,
        signing_key.key,
        algorithms=["RS256"],
        audience=audience,
        options={"require": ["exp", "aud"]},
    )


def cloudflare_user(token: str) -> UserContext:
    claims = validate_cloudflare_access_jwt(token)
    email = str(claims.get("email") or "").strip().lower()
    if not email:
        raise PermissionError("Cloudflare identity has no email claim")
    user = user_by_email(email)
    if user is None:
        raise PermissionError("This Cloudflare identity is not a JingYou Health user")
    return user
