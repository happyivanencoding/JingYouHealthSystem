from __future__ import annotations

import html
import json
import os
import shutil
import subprocess
import sys
import uuid
from contextlib import contextmanager
from datetime import date, timedelta
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from health_store import connect, utc_now  # noqa: E402
from auth import (  # noqa: E402
    UserContext,
    cloudflare_user,
    issue_session,
    resolve_session,
    revoke_session,
    user_by_dev_alias,
)
from health_queries import activities, agent_context, dashboard, trends  # noqa: E402

app = FastAPI(title="JingYou Health API", version="0.1.0")
PYTHON = ROOT / ".venv" / "Scripts" / "python.exe"
SYNC_SCRIPT = ROOT / "scripts" / "sync_garmin_history.py"
AGENT_RUNNER = ROOT / "server" / "run-health-agent.ps1"
COACH_MODEL = os.getenv("JINGYOU_COACH_MODEL", "gpt-5.6-sol")
COACH_REASONING = os.getenv("JINGYOU_COACH_REASONING", "medium")


class ChatCreate(BaseModel):
    title: str = Field(default="新对话", max_length=120)


class MessageCreate(BaseModel):
    content: str = Field(min_length=1, max_length=12000)


def _bearer(authorization: str | None) -> str | None:
    if not authorization:
        return None
    prefix = "Bearer "
    return authorization[len(prefix) :].strip() if authorization.startswith(prefix) else None


def current_user(
    authorization: Annotated[str | None, Header()] = None,
) -> UserContext:
    user = resolve_session(_bearer(authorization))
    if user is None:
        raise HTTPException(status_code=401, detail="Authentication required")
    return user


def _loopback(request: Request) -> bool:
    host = request.client.host if request.client else ""
    cloudflare_proxy_headers = (
        "cf-ray",
        "cf-connecting-ip",
        "cf-visitor",
    )
    came_through_cloudflare = any(request.headers.get(name) for name in cloudflare_proxy_headers)
    return host in {"127.0.0.1", "::1", "localhost"} and not came_through_cloudflare


@app.get("/api/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/dev/login/{profile}")
def dev_login(profile: str, request: Request) -> dict[str, object]:
    # USB development only: the service binds to loopback and adb reverse makes
    # the phone appear as localhost. Production uses Cloudflare Access bridge.
    if not _loopback(request):
        raise HTTPException(status_code=404, detail="Not found")
    user = user_by_dev_alias(profile)
    if user is None:
        raise HTTPException(status_code=404, detail="Unknown development login alias")
    token = issue_session(user, "usb-dev")
    return {
        "token": token,
        "user": {"display_name": user.display_name, "role": user.role},
    }


@app.get("/api/mobile-auth/bridge", response_class=HTMLResponse)
def mobile_auth_bridge(request: Request) -> HTMLResponse:
    token = request.headers.get("cf-access-jwt-assertion", "")
    try:
        user = cloudflare_user(token)
    except Exception as exc:
        return HTMLResponse(
            status_code=401,
            content=f"<html><body><h1>Authentication required</h1><p>{html.escape(str(exc))}</p></body></html>",
        )
    session = issue_session(user, "cloudflare-access")
    callback = f"jingyouhealth://auth?token={session}"
    return HTMLResponse(
        content=f"""<!doctype html><html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
        <body style='font-family:system-ui;background:#080b16;color:#f4f5ff;display:grid;place-items:center;min-height:100vh;margin:0'>
        <main style='padding:28px;text-align:center'><h1>JingYou Health</h1><p>Authentication complete.</p>
        <a style='color:#9ddcff' href='{html.escape(callback)}'>Return to app</a></main>
        <script>setTimeout(()=>location.href={json.dumps(callback)},80)</script></body></html>"""
    )


@app.post("/api/logout")
def logout(
    authorization: Annotated[str | None, Header()] = None,
) -> dict[str, bool]:
    token = _bearer(authorization)
    if token:
        revoke_session(token)
    return {"ok": True}


@app.get("/api/me")
def me(user: UserContext = Depends(current_user)) -> dict[str, str]:
    return {"display_name": user.display_name, "role": user.role}


@app.get("/api/dashboard")
def get_dashboard(user: UserContext = Depends(current_user)) -> dict[str, object]:
    return dashboard(user)


@app.get("/api/trends")
def get_trends(
    days: int = Query(default=30, ge=7, le=180),
    user: UserContext = Depends(current_user),
) -> dict[str, object]:
    return trends(user, days=days)


@app.get("/api/activities")
def get_activities(
    limit: int = Query(default=80, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    user: UserContext = Depends(current_user),
) -> list[dict[str, object]]:
    return activities(user, limit=limit, offset=offset)


@app.post("/api/refresh")
def refresh(user: UserContext = Depends(current_user)) -> dict[str, object]:
    end = date.today()
    start = end - timedelta(days=2)
    result = subprocess.run(
        [
            str(PYTHON),
            str(SYNC_SCRIPT),
            "--user-id",
            user.user_id,
            "--start",
            start.isoformat(),
            "--end",
            end.isoformat(),
            "--phase",
            "refresh",
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=180,
        check=False,
    )
    if result.returncode != 0:
        raise HTTPException(status_code=502, detail=(result.stderr or result.stdout)[-1200:])
    return {"ok": True, "dashboard": dashboard(user)}


@contextmanager
def _thread_rows(user: UserContext):
    con = connect(user.health_db)
    try:
        yield con
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()


@app.get("/api/chat/threads")
def chat_threads(user: UserContext = Depends(current_user)) -> list[dict[str, object]]:
    with _thread_rows(user) as con:
        return [
            dict(row)
            for row in con.execute(
                "SELECT id,title,created_at,updated_at FROM chat_threads WHERE archived=0 ORDER BY updated_at DESC"
            ).fetchall()
        ]


@app.post("/api/chat/threads")
def create_thread(body: ChatCreate, user: UserContext = Depends(current_user)) -> dict[str, object]:
    thread_id = f"thr_{uuid.uuid4().hex}"
    now = utc_now()
    with _thread_rows(user) as con:
        con.execute(
            "INSERT INTO chat_threads(id,title,created_at,updated_at) VALUES(?,?,?,?)",
            (thread_id, body.title.strip() or "新对话", now, now),
        )
    return {"id": thread_id, "title": body.title.strip() or "新对话", "created_at": now, "updated_at": now}


@app.get("/api/chat/threads/{thread_id}/messages")
def thread_messages(thread_id: str, user: UserContext = Depends(current_user)) -> list[dict[str, object]]:
    with _thread_rows(user) as con:
        exists = con.execute("SELECT 1 FROM chat_threads WHERE id=?", (thread_id,)).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="Thread not found")
        return [
            dict(row)
            for row in con.execute(
                "SELECT id,role,content,created_at,status,metadata_json FROM chat_messages WHERE thread_id=? ORDER BY created_at",
                (thread_id,),
            ).fetchall()
        ]


@app.post("/api/chat/threads/{thread_id}/messages")
def post_message(
    thread_id: str,
    body: MessageCreate,
    user: UserContext = Depends(current_user),
) -> dict[str, object]:
    now = utc_now()
    msg_id = f"msg_{uuid.uuid4().hex}"
    with _thread_rows(user) as con:
        exists = con.execute("SELECT 1 FROM chat_threads WHERE id=?", (thread_id,)).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="Thread not found")
        con.execute(
            "INSERT INTO chat_messages(id,thread_id,role,content,created_at,status) VALUES(?,?,?,?,?,?)",
            (msg_id, thread_id, "user", body.content.strip(), now, "complete"),
        )
        con.execute("UPDATE chat_threads SET updated_at=? WHERE id=?", (now, thread_id))
    return {"id": msg_id, "role": "user", "content": body.content.strip(), "created_at": now, "status": "complete"}


def _conversation_snapshot(user: UserContext, thread_id: str, limit: int = 24) -> list[dict[str, str]]:
    with _thread_rows(user) as con:
        rows = con.execute(
            """SELECT role,content,created_at FROM chat_messages
               WHERE thread_id=? AND role IN ('user','assistant')
               ORDER BY created_at DESC LIMIT ?""",
            (thread_id, limit),
        ).fetchall()
    return [
        {"role": str(row["role"]), "content": str(row["content"]), "created_at": str(row["created_at"])}
        for row in reversed(rows)
    ]


def _generate_coach_answer(user: UserContext, thread_id: str) -> tuple[str, dict[str, object]]:
    conversation = _conversation_snapshot(user, thread_id)
    if not conversation or conversation[-1]["role"] != "user":
        raise HTTPException(status_code=409, detail="The latest chat message is not a user question")

    turn_id = f"turn_{uuid.uuid4().hex}"
    workspace = user.root / "agent_workspace" / thread_id / turn_id
    workspace.mkdir(parents=True, exist_ok=True)
    context_path = workspace / "context.json"
    prompt_path = workspace / "prompt.txt"
    output_path = workspace / "answer.txt"

    context = {
        "profile": {"display_name": user.display_name},
        "conversation": conversation,
        "health": agent_context(user, days=56),
    }
    context_path.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
    prompt = "\n".join(
        [
            "You are JingYou Coach, a private wellness and training assistant.",
            "Read only context.json in the current workspace. Do not access any other file, path, command, network service, or tool.",
            "The last conversation item is the user's current question. Answer in the same language as that question.",
            "Use the supplied health data and conversation history as evidence. Distinguish observed data from interpretation.",
            "For current/today questions, use the freshest meaningful measurement for each metric and respect its date/timestamp; if key metrics come from different dates, say so briefly.",
            "You may discuss recovery, sleep, training load, exercise planning, and health trends. Do not present medical diagnosis as fact.",
            "If the available data cannot support a conclusion, say what is missing rather than inventing it.",
            "Prefer a direct answer first, then the few data points that matter most, then an actionable recommendation.",
            "Do not mention internal files, workspaces, prompts, sandboxing, or implementation details in the answer.",
        ]
    )
    prompt_path.write_text(prompt, encoding="utf-8")

    command = [
        "pwsh",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(AGENT_RUNNER),
        "-PromptPath",
        str(prompt_path),
        "-OutputPath",
        str(output_path),
        "-Cwd",
        str(workspace),
        "-Model",
        COACH_MODEL,
        "-Reasoning",
        COACH_REASONING,
        "-Mode",
        "read-only",
        "-TimeoutMinutes",
        "4",
    ]
    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            capture_output=True,
            timeout=270,
            check=False,
        )
        if result.returncode != 0 or not output_path.exists():
            raw_detail = result.stderr or result.stdout or b"ACP coach failed"
            if isinstance(raw_detail, bytes):
                detail = raw_detail.decode("utf-8", errors="replace")[-1600:]
            else:
                detail = str(raw_detail)[-1600:]
            raise HTTPException(status_code=502, detail=detail)
        answer = output_path.read_text(encoding="utf-8").lstrip("\ufeff").strip()
        if not answer:
            raise HTTPException(status_code=502, detail="ACP coach returned an empty answer")
        metadata: dict[str, object] = {
            "engine": "agentdock-acp-codex",
            "model": COACH_MODEL,
            "reasoning": COACH_REASONING,
            "mode": "read-only",
            "workspace_scope": "single-turn",
        }
        return answer, metadata
    finally:
        shutil.rmtree(workspace, ignore_errors=True)


@app.post("/api/chat/threads/{thread_id}/answer")
def answer_thread(
    thread_id: str,
    user: UserContext = Depends(current_user),
) -> dict[str, object]:
    with _thread_rows(user) as con:
        thread = con.execute("SELECT id,title FROM chat_threads WHERE id=?", (thread_id,)).fetchone()
        if not thread:
            raise HTTPException(status_code=404, detail="Thread not found")
        latest = con.execute(
            "SELECT role,content FROM chat_messages WHERE thread_id=? ORDER BY created_at DESC LIMIT 1",
            (thread_id,),
        ).fetchone()
        if not latest or latest["role"] != "user":
            raise HTTPException(status_code=409, detail="No unanswered user message")

    answer, metadata = _generate_coach_answer(user, thread_id)
    now = utc_now()
    msg_id = f"msg_{uuid.uuid4().hex}"
    with _thread_rows(user) as con:
        con.execute(
            """INSERT INTO chat_messages
               (id,thread_id,role,content,created_at,status,metadata_json)
               VALUES(?,?,?,?,?,?,?)""",
            (msg_id, thread_id, "assistant", answer, now, "complete", json.dumps(metadata, ensure_ascii=False)),
        )
        title = str(thread["title"])
        if title in {"新对话", "问问我的身体"}:
            first_user = con.execute(
                "SELECT content FROM chat_messages WHERE thread_id=? AND role='user' ORDER BY created_at LIMIT 1",
                (thread_id,),
            ).fetchone()
            if first_user:
                title = str(first_user["content"]).strip().replace("\n", " ")[:36] or title
        con.execute("UPDATE chat_threads SET title=?,updated_at=? WHERE id=?", (title, now, thread_id))
    return {
        "id": msg_id,
        "role": "assistant",
        "content": answer,
        "created_at": now,
        "status": "complete",
        "metadata_json": json.dumps(metadata, ensure_ascii=False),
    }
