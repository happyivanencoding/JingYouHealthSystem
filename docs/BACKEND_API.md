# JingYou HealthSystem Backend API

This document is the contract for the Android/frontend work. Backend implementation lives under `server/` and `scripts/`.

## Runtime

Local development API:

```text
http://127.0.0.1:8788
```

USB Android development uses:

```text
adb reverse tcp:8788 tcp:8788
```

Production target is `health.thegreatnovel.com` through Cloudflare Access/Tunnel. The hostname/Access application is not yet provisioned in Cloudflare.

## Authentication model

The client never sends an arbitrary `user_id` for health access.

```text
Bearer session
  -> backend resolves current UserContext
  -> UserContext selects that user's health.db and Garmin token home
```

This is the isolation boundary between independent app accounts.

### USB development login

```http
POST /api/dev/login/{profile}
```

Loopback-only development endpoint. The only accepted development aliases are `owner` and `member`; real display/profile names are not accepted as login selectors. The private local profile registry maps those aliases to real accounts. Production Android must use the Cloudflare mobile auth bridge instead of exposing a profile switcher.

Response:

```json
{
  "token": "...",
  "user": {"display_name": "<current-user>", "role": "OWNER"}
}
```

### Production mobile auth bridge

```http
GET /api/mobile-auth/bridge
```

Cloudflare Access supplies `Cf-Access-Jwt-Assertion`. Backend validates the Access JWT, maps its email to a JingYou account, creates a JingYou server session, then returns to:

```text
jingyouhealth://auth?token=<jingyou-session>
```

Cloudflare team domain/audience still need production configuration.

## Common authorization

Except health check, development login, and auth bridge, endpoints require:

```http
Authorization: Bearer <jingyou-session>
```

## Health endpoints

### Health check

```http
GET /api/healthz
```

### Current user

```http
GET /api/me
```

### Dashboard

```http
GET /api/dashboard
```

Returns the current user's latest normalized Garmin snapshot, including the latest meaningful daily summary, HRV, sleep, Body Battery, Training Readiness when present, and recent activities. Empty same-day placeholder rows created before Garmin has synchronized real values are skipped.

The response also includes a `freshness` object with the source date/timestamp for each major component (`daily`, `hrv`, `sleep`, `body_battery`, `readiness`). Coach/current-state logic uses these component timestamps rather than assuming every metric belongs to the same calendar day.

### Trends

```http
GET /api/trends?days=30
```

`days`: 7–180.

Returns chronological series for HRV, daily metrics, and sleep.

### Activities

```http
GET /api/activities?limit=80&offset=0
```

`limit`: 1–200.

### Garmin pull refresh

```http
POST /api/refresh
```

Backend resolves the logged-in user, uses only that user's Garmin token, refreshes the most recent days into that user's `health.db`, then returns the updated dashboard.

The Android client never stores or receives Garmin credentials/tokens.

## Coach / chat

Chat state is stored in the current user's own `health.db`.

### List threads

```http
GET /api/chat/threads
```

### Create thread

```http
POST /api/chat/threads
Content-Type: application/json

{"title":"新对话"}
```

### Read messages

```http
GET /api/chat/threads/{thread_id}/messages
```

### Persist user message

```http
POST /api/chat/threads/{thread_id}/messages
Content-Type: application/json

{"content":"我今天适合跑 10km 吗？"}
```

This returns immediately after persisting the user message.

### Generate Coach answer

```http
POST /api/chat/threads/{thread_id}/answer
```

The backend:

1. Resolves the authenticated user.
2. Reads only that user's health DB and conversation history.
3. Generates a per-turn `context.json` inside a per-user, per-thread temporary workspace.
4. Starts AgentDock ACP Codex in `read-only` mode with that workspace as `cwd`.
5. Persists the final assistant response into the same user's chat history.
6. Removes the temporary turn workspace.

The ACP sandbox was explicitly verified: a read-only session could read a marker inside its workspace and refused to read a marker outside it.

Current Coach model defaults:

```text
gpt-5.6-sol
reasoning=medium
mode=read-only
```

Environment overrides:

```text
JINGYOU_COACH_MODEL
JINGYOU_COACH_REASONING
```

The current `/answer` endpoint is a blocking HTTP request. Android can show local typing/status animation while waiting. True token/event streaming can be added later if the frontend requires it.

## Current backend status

Completed:

- Per-user Garmin token homes.
- Per-user physical `health.db` isolation.
- Historical Garmin daily/activity/FIT synchronization completed for both private app accounts.
- Dashboard/trends/activities API.
- Authenticated Garmin refresh.
- Persistent chat threads/messages.
- Scoped AgentDock ACP Coach answer generation.
- Real ACP isolation test.
- Backend regression tests for distinct sessions, Cloudflare identity mapping, physical health/chat DB isolation, and per-user ACP context.
- SQLite request connections are explicitly closed; FastAPI no longer leaves `health.db` / `app.db` handles open after requests.
- Latest-state queries skip empty Garmin placeholder rows so Coach does not incorrectly report synchronized metrics as missing.

Remaining backend work:

- Provision `health.thegreatnovel.com` ingress on the existing remote-managed Cloudflare Tunnel.
- Configure Cloudflare Access application, JWT audience/team-domain values, and production email mapping flow.
- Run the FastAPI server as a persistent Windows service/startup process rather than the current development uvicorn session.
- Optionally add true streaming Coach events if the frontend wants backend-driven phase updates rather than local status animation.
