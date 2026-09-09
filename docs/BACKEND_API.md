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

Production API is `https://health.thegreatnovel.com` through the existing remote-managed Cloudflare Tunnel. Cloudflare Access protects only the identity-exchange route `https://health.thegreatnovel.com/api/mobile-auth/bridge`; after the browser exchange, normal native API requests go directly through the Tunnel and are authorized by the JingYou Bearer session. This avoids requiring the native HTTP client to carry the system browser's Cloudflare cookie.

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

Loopback-only development endpoint. The only accepted development aliases are `owner` and `member`; real display/profile names are not accepted as login selectors. The private local profile registry maps those aliases to real accounts. Requests carrying Cloudflare proxy headers are rejected even though `cloudflared` reaches the origin from loopback, so this route is not usable through the public Tunnel. Production Android must use the Cloudflare mobile auth bridge instead of exposing a profile switcher.

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

The Cloudflare Access auth domain and this application's audience are configured as Windows user environment variables (`JINGYOU_CF_TEAM_DOMAIN`, `JINGYOU_CF_AUD`). The Access application has a single Allow policy containing the two private JingYou account emails; the backend then maps the verified email claim to exactly one `UserContext` and issues its own JingYou session.

The current Cloudflare Access login configuration for the Health app uses **One-time PIN**. The allow policy still admits only the original two JingYou account emails; their addresses are intentionally omitted from this public contract. An earlier IdP configuration that admitted only Cloudflare account members rejected the MEMBER login; that configuration was corrected in Chrome. The backend still performs the final email-to-user mapping and does not expose an account switcher.

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

Returns the current user's latest normalized Garmin snapshot, including the latest meaningful daily summary, HRV, sleep, Body Battery, the JingYou recovery reference index, and recent activities. Empty same-day placeholder rows created before Garmin has synchronized real values are skipped.

The response also includes a `freshness` object with the source date/timestamp for each major component (`daily`, `hrv`, `sleep`, `body_battery`, `readiness`). Coach/current-state logic uses these component timestamps rather than assuming every metric belongs to the same calendar day.

`recent_activities` uses the same enriched activity fields and internal-load calculation as `GET /api/activities`.

`readiness` is the JingYou personal recovery reference index shared by the dashboard, trends, and Coach context. It has the following shape:

```json
{
  "source": "jingyou",
  "formula_version": "personal-v1",
  "date": "2026-09-06",
  "score": 78.4,
  "level": "moderate",
  "components": [
    {"key": "sleep", "score": 82.0, "weight": 0.4, "value": 7.4, "baseline": 8.0},
    {"key": "hrv", "score": 79.0, "weight": 0.3, "value": 51.0, "baseline": 50.0},
    {"key": "rhr", "score": 76.0, "weight": 0.2, "value": 61.0, "baseline": 60.0},
    {"key": "load", "score": 75.0, "weight": 0.1, "value": 180.0, "baseline": 210.0}
  ],
  "coverage": 4
}
```

The four component keys are always stable; a component with insufficient data has a null score. `coverage` is the number of components with a score. The overall score is null unless sleep is available and at least two components are available; available weights are then renormalized. `date` follows the latest meaningful sleep record, so an empty same-day placeholder does not become the current recovery date. Garmin's original training-readiness payload remains in the local raw archive and is not used as this primary score.

`personal-v1` is an engineering composite for this user's historical data, not a clinical scale or a medically validated universal formula. Sleep, log-HRV, and resting-HR references use the strict prior 42 days. Load compares the recent 3 days against observed dates in the prior 28 days; future rows and another user's database are excluded. Load component details also include `source`, `estimated_ratio`, and `reported_ratio` when available. An estimated activity effort is a category default, never a claim about the user's actual RPE.

### Intraday state timeline

```http
GET /api/state-timeline?date=2026-08-12
```

`date` is optional and defaults to today. The endpoint is current-user scoped and returns the raw normalized intraday observations needed by the Android “Today’s state” card without creating another fatigue score. Body Battery remains Garmin’s 0–100 energy-reserve signal; stress remains Garmin physiological stress.

```json
{
  "date": "2026-08-12",
  "timezone": "Europe/Paris",
  "generated_at": "2026-08-12T21:30:00+02:00",
  "body_battery": [
    {"timestamp": "2026-08-12T07:30:00+02:00", "value": 82.0}
  ],
  "stress": [
    {"timestamp": "2026-08-12T14:00:00+02:00", "value": 64.0},
    {"timestamp": "2026-08-12T14:03:00+02:00", "value": null}
  ],
  "sleep": {"start": "2026-08-12T00:10:00", "end": "2026-08-12T07:45:00"},
  "activities": [],
  "summary": {
    "latest_body_battery": 31.0,
    "body_battery_peak": 82.0,
    "body_battery_drop_since_peak": 51.0,
    "latest_stress": 42.0,
    "average_stress": 36.0,
    "highest_stress_window": {
      "start": "2026-08-12T14:00:00+02:00",
      "end": "2026-08-12T14:30:00+02:00",
      "average": 71.4
    },
    "activity_count": 0
  }
}
```

Garmin stress sentinel values such as `-1`/`-2` are returned as `null`; clients must render them as missing gaps, never as zero stress. Intraday epoch samples are converted to the device day using Garmin’s own Local/GMT day-boundary offset, while the human-readable timezone label follows the user’s recent Garmin activity timezone. This avoids interpreting UTC samples as local clock time.

Coach does **not** receive these hundreds of timeline points. `agent_context` includes a compact `today_state` with the date/timezone, sleep interval, activity events, current/peak energy, energy drop, average/latest stress, and the highest-stress interval. This keeps Coach useful without expanding every turn with raw high-frequency samples.

### JingYou Rhythm training status

```http
GET /api/training?goal=balanced|endurance|strength
PUT /api/training/preferences
PUT /api/training/checkin
```

Training status uses `methodology_version="jingyou-rhythm-v1"` and is included in dashboard `training` and Coach `health.today.training`. The preference table and check-ins are created inside the authenticated user's own `health.db`.

```json
{
  "training": {
    "methodology_version": "jingyou-rhythm-v1",
    "date": "2026-09-06",
    "goal": "balanced",
    "feeling": null,
    "load_trend": "usual",
    "relative_ratio": 1.0,
    "chronic_relative_ratio": 1.125,
    "chronic_trend": "usual",
    "confidence": "recorded",
    "mode": "balanced",
    "focus": "strength",
    "intensity": "balanced",
    "reasons": ["goal_strength_gap"],
    "acute": {"total_au": 280.0, "active_days": 2, "coverage_days": 7, "window_days": 7},
    "chronic": {"total_au": 1260.0, "active_days": 7, "coverage_days": 28, "window_days": 28},
    "reference": {
      "total_au": 1080.0,
      "coverage_days": 27,
      "weekly_equivalent_au": 280.0,
      "scaled_for_coverage": true
    },
    "chronic_reference": {
      "from": "2026-07-13",
      "through": "2026-08-09",
      "total_au": 1080.0,
      "coverage_days": 27,
      "equivalent_au": 1120.0,
      "scaled_for_coverage": true
    },
    "estimated_ratio": 0.35,
    "reported_ratio": 0.65,
    "hard_days3": 0,
    "anaerobic_days3": 0,
    "categories": [
      {"key": "strength", "days_7": 1, "days_28": 2, "sessions_7": 1, "sessions_28": 3, "minutes_7": 60.0, "minutes_28": 195.0, "au_7": 240.0, "au_28": 540.0, "last_date": "2026-09-04"}
    ],
    "direction": {"intensity": "balanced", "focus": "strength", "confidence": "recorded", "reason_key": "goal_strength_gap"}
  }
}
```

The backend also returns the complete category breakdown and method metadata. `acute` is `[D-6,D]`, `chronic` is `[D-27,D]`, the short-term reference is `[D-34,D-7]`, and the independent chronic reference is `[D-55,D-28]`. The short reference is a weekly equivalent; the chronic reference is a 28-day equivalent. Unknown dates are excluded rather than treated as zero. Relative ratios and `rising`/`lighter`/`usual` trends require all 7 observed short-term days and at least 24 observed reference days; chronic comparison also requires complete 28-day current coverage and at least 24 chronic-reference days. Recorded totals and coverage remain available when those thresholds are not met, with `trend="insufficient"`; a zero reference is labeled `building` instead of producing a ratio. Activity coverage comes only from completed activity sync/refresh metadata. Neither wellness dates nor an actual activity row proves complete activity-list coverage for a day. Reference scaling includes only AU from verified covered dates; partial-day activities remain in recorded totals without restoring coverage.

AU is always `duration_minutes × effective_RPE`, using the existing reported/estimated effort source. Estimated effort is surfaced through `estimated_ratio`, `reported_ratio`, and `confidence`; a high estimated share makes the direction conservative and asks for better effort records, but does not block goal-based focus. Recovery score, short sleep, and a `tired` check-in take priority. The labels are engineering rhythm guidance only: they do not diagnose overtraining, undertraining, injury risk, or prescribe a clinical plan.

Set a goal with:

```json
{"goal":"balanced"}
```

Set or clear the current-date check-in with:

```json
{"date":"2026-09-06","feeling":"fresh"}
```

`feeling` is `fresh`, `normal`, `tired`, or `null`; omitting `date` uses the current training date. Both PUT endpoints return `{ "training": ... }` and never change the recovery score or recorded AU.

### Trends

```http
GET /api/trends?days=30&training_days=730
```

`days`: 7–180.

`training_days` is optional and accepts 28–730. When omitted, the legacy trends response is unchanged and no training history is added. When present, the response adds `training_load` with `methodology_version="jingyou-rhythm-v1"` and calendar points containing `coverage_7`, `coverage_28`, `all`, and the four category maps. Each load map has `load_7`, `load_28`, `reference_weekly`, `reference_28`, `recorded_7`, and `recorded_28`. Full-window loads are null until activity coverage is complete for all 7 or 28 dates; recorded fields contain only actual activity AU sums and may remain available under partial coverage. The independent references are `[D-34,D-7]` for the weekly-equivalent field and `[D-55,D-28]` for `reference_28`, using the same completed activity-sync/refresh metadata and coverage thresholds as the main Rhythm method. History points may come from verified dates or actual activity dates; actual activity dates alone never increase coverage. An actual but unverified date can retain recorded values with null complete-window loads. Reference scaling uses only covered-date AU. Future dates and wholly unknown activity days are excluded rather than filled with zero.

Returns chronological series for HRV, daily metrics, sleep, and the same recovery calculation used by the dashboard. `readiness` contains `{date, score, value}` entries (with `value` equal to `score`) and is calculated from one in-memory historical snapshot, so a historical date uses the same formula and windows as the dashboard.

Each `sleep` row includes `light_sleep_sec`, `awake_sleep_sec`, `sleep_start_local`, `sleep_end_local`, `clock_source`, and `clock_offset_changed`. Local timestamps are ISO 8601 strings without a timezone suffix. Garmin's numeric `sleepStartTimestampLocal` / `sleepEndTimestampLocal` are decoded as UTC to recover their local wall-clock fields. GMT is used only to compare start/end offsets; it never fills a missing Local endpoint. If either Local endpoint is missing, non-finite, reversed, or spans more than 36 hours, all clock fields are null. The same clock fields are also available under `sleep_clocks`. The complete raw sleep payload is kept in the database but is never returned by this endpoint.

### Activities

```http
GET /api/activities?limit=80&offset=0
```

`limit`: 1–200.

Each activity includes the Garmin `training_effect_label` and `anaerobic_training_effect` plus JingYou effort fields:

```json
{
  "category": "easy_aerobic",
  "category_override": null,
  "effort_rpe": 3.0,
  "effort_source": "estimated",
  "internal_load": 45.0
}
```

Category resolution is user override first, then strength/weight-training type, recognizable training-effect label, and finally the aerobic/anaerobic training-effect fallback. `internal_load` is duration in minutes × effective RPE in arbitrary units (AU). A self-reported effort has `effort_source=reported`; otherwise the category defaults are easy aerobic 3, hard aerobic 6, anaerobic 8, and strength 6 with `effort_source=estimated`. The Garmin `activity_training_load` field is retained as source data for compatibility, but is not added to `internal_load` and Coach is instructed to prefer `internal_load`.

### Save activity effort and category

```http
PUT /api/activities/{activity_id}/effort
Content-Type: application/json

{"rpe": 7.5, "category": "hard_aerobic"}
```

Both fields are nullable. Sending `null` clears that user's override; `category: null` returns the automatic classification. The activity must exist in the authenticated user's own database, otherwise the endpoint returns `404`. The response is the same enriched activity object returned by `GET /api/activities`. `activity_effort` is stored in that user's database only; it cannot be used to read or modify another user's activity.

### Garmin pull refresh

```http
POST /api/refresh
```

Backend resolves the logged-in user and uses only that user's Garmin token. A user-initiated pull refresh deliberately **re-queries the most recent 3 calendar days** instead of trusting the historical-sync checkpoint, because Garmin can create same-day placeholder records before the watch finishes syncing. It also checks the latest 20 activity summaries incrementally so newly synced activities appear without redownloading the user's entire activity history/FIT archive. The updated dashboard is returned after the sync completes.

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

The Android client may attach a validated personal sleep-analysis snapshot. It is stored in `chat_messages.metadata_json`; it is never appended to the user-visible `content`:

```json
{
  "content": "我为什么最近睡得短？",
  "sleep_analysis": {
    "schema_version": 1,
    "source": "android_personal_sleep_v1",
    "through_date": "2026-09-06",
    "generated_at": "2026-09-06T10:00:00Z",
    "french_holidays": true,
    "models": [
      {
        "outcome": "DURATION_HOURS",
        "status": "READY",
        "algorithm": "RANDOM_FOREST",
        "factor_a": "STRESS",
        "factor_b": "TRAINING_LOAD",
        "feature_pack": "ENRICHED",
        "lag_days": 1,
        "train_n": 60,
        "validation_n": 10,
        "validation_start": "2026-08-01",
        "validation_end": "2026-08-10",
        "selection_mae": 1.1,
        "selection_reference_mae": 1.2,
        "mae": 1.0,
        "reference_mae": 1.2,
        "feature_importance": [
          {"feature": "factor_a", "mae_increase": -0.1, "repeat_sd": 0.2}
        ],
        "dropped_features": []
      }
    ],
    "timing": null
  }
}
```

The closed schema permits at most five models, twenty importance rows per model, and thirty dropped-feature tokens. Outcomes, statuses, factors, algorithm, feature pack, dates, MAE values and finite numeric ranges are validated. Unknown fields such as `user_id` or free-form `narrative` are rejected. Negative `mae_increase` is preserved because permutation importance can be negative; `repeat_sd` and MAE values must be finite and nonnegative. A request without `sleep_analysis` remains a normal Coach message.

### Generate Coach answer

```http
POST /api/chat/threads/{thread_id}/answer
```

The backend:

1. Resolves the authenticated user.
2. Reads only that user's health DB, the latest user-message metadata, conversation history, and current-user Coach memory.
3. Builds one bounded in-memory business payload containing the exact current question, conversation, health evidence, response-focus flags, optional sleep analysis, and selected memory. No per-turn `context.json`, prompt file, answer file, or user-scoped model workspace is created.
4. Serializes that payload inline into the Coach prompt and calls the generic `server/model_transport.py::generate_text(...)` boundary. Business code supplies only prompt, model, reasoning effort, and timeout; it does not know about ACP session ids, `cwd`, modes, permissions, MCP, tools, or workspaces.
5. The current ACP adapter uses the fixed neutral directory `%LOCALAPPDATA%\JingYouHealthSystem\acp-transport` only for Codex runtime configuration. It contains no health/chat/business payload. `additional_directories` is always empty. The generated Codex config disables web search, shell/unified-exec, browser/computer-use, apps/plugins, image/view, hooks, memory, multi-agent, skill search/host discovery, permission-tool features and project-document discovery; every currently configured user-level MCP server is explicitly disabled for this transport session.
6. `server/run-health-agent.ps1` sends the already-complete prompt through AgentDock ACP/Codex. Its ACP mode is hard-coded to the least-privileged `read-only` mode because the current ACP protocol adapter requires an agent mode; there is no runtime fallback to `agent` or `agent-full-access`. The runner returns only a transport envelope containing model text plus event-audit metadata. Any observed tool-capability event or permission interaction makes the transport fail instead of persisting a result.
7. The backend parses the JSON model envelope `{ "answer": string, "memory_updates": [...] }` (with the historical plain-text compatibility path), validates memory updates against the current user's original message, then persists the assistant answer and valid updates itself. The model never writes chat history, memory, reports, files, tracker state, or databases.

The redirected Python ↔ PowerShell transport boundary is explicitly UTF-8. The runner pins stdin/stdout to UTF-8, sends MCP JSON request bodies as UTF-8 bytes, and escapes non-ASCII characters in the final stdout JSON envelope. `model_transport.py` decodes stdout strictly as UTF-8; invalid transport bytes fail the turn instead of being replaced with `�` and saved into chat history. This is transport framing only and does not change Coach business logic.

Runtime architecture invariant:

> **All runtime ACP model calls are transport-only. ACP is a temporary model transport, not an application agent runtime. All I/O, retrieval, persistence and orchestration belong to the application. Every ACP call must remain directly replaceable by a direct model API call without changing business logic.**

`server/model_transport.py` is the only business-visible model boundary. If ACP is replaced by OpenAI Responses, DeepSeek, or another direct provider, the Coach payload construction, health retrieval, memory selection, output parsing/validation, persistence, and endpoint orchestration remain unchanged.

There is one current Codex-ACP limitation that must not be mislabeled as solved: the installed `codex-acp`/Codex runtime does not expose a per-session `tools=[]` or a way to remove every host bootstrap item. A real post-change rollout had empty Codex base instructions and no JingYou project path/repository context, but Codex still injected fixed host-skill, permission/sandbox, global AGENTS, and neutral environment-context blocks. Those blocks are transport/runtime overhead only; the business prompt does not depend on them. The live transport smoke produced only `agent_message_chunk`, `completed`, `session_info_update`, and `usage_update`, with **0 tool-capability events and 0 permission interactions**. The neutral transport directory contained only `.codex/config.toml`.

Current Coach model defaults:

```text
gpt-5.6-sol
reasoning=medium
```

Environment overrides:

```text
JINGYOU_COACH_MODEL
JINGYOU_COACH_REASONING
```

The current `/answer` endpoint is a blocking HTTP request. Android can show local typing/status animation while waiting. The ACP helper receives the prompt on stdin and returns the transport envelope on stdout; no model-turn prompt/output files are used. True token/event streaming can be added later if the frontend requires it.

The 2026-09-08 transport smoke used the same runtime path with `gpt-5.6-luna`/low reasoning and returned the exact requested sentinel in about 5.9 seconds. There is **no reliable JingYou before-migration token/time/cost baseline**, so no before/after percentage is claimed.

Coach output language is locked to the **latest user question only**. Earlier conversation history, Android UI language, profile/display names, and health-data labels must not influence the reply language. The agent must answer entirely in the same language as the current question, except for unavoidable proper nouns, standard units, abbreviations, or quoted text. This behavior has been smoke-tested with Chinese, English, French, and Arabic prompts against the real ACP Coach path.

Sleep-analysis quality is `recent_validation_improved` only when a `READY` model has at least ten validation records and finite `mae < reference_mae`; otherwise a ready model is `unstable`, and no ready models is `insufficient`. Weak or negative importance is still passed to Coach as exploratory evidence, but Coach must not present it as a cause, direction, or explanation of one night. If `through_date` differs from the server's latest meaningful sleep date, `freshness` is `historical` and `stale` is true; the snapshot is never silently described as last night's result.

### Coach memory

```http
GET /api/coach/memory
DELETE /api/coach/memory/{key}
```

Both endpoints use the authenticated user's own memory store. The list response is `{ "items": [...] }` with at most 120 items; each item contains `key`, `category`, `text`, `confidence`, `source_message_ids`, `created_at`, and `updated_at`. DELETE is an explicit user action and returns `{ "deleted": true|false }`. Memory updates from Coach are accepted only when the memory layer can verify a current-user original message source; assistant analysis cannot self-confirm a durable fact.

## Current backend status

Completed:

- Per-user Garmin token homes.
- Per-user physical `health.db` isolation.
- Historical Garmin daily/activity/FIT synchronization completed for both private app accounts.
- Dashboard/trends/activities API.
- Authenticated Garmin refresh.
- Persistent chat threads/messages.
- Transport-only Coach model boundary with AgentDock/ACP as the current adapter.
- Real ACP transport smoke with zero tool-capability events/permission interactions and no project-repo context.
- Backend regression tests for distinct sessions, Cloudflare identity mapping, physical health/chat DB isolation, per-user model payloads, and the neutral transport boundary.
- SQLite request connections are explicitly closed; FastAPI no longer leaves `health.db` / `app.db` handles open after requests.
- Latest-state queries skip empty Garmin placeholder rows so Coach does not incorrectly report synchronized metrics as missing.
- `health.thegreatnovel.com` DNS and Tunnel ingress are provisioned on the existing `TGN` remote-managed tunnel and route to `127.0.0.1:8788`.
- A dedicated Cloudflare Access application protects only `/api/mobile-auth/bridge` with one two-user email Allow policy. Public boundary verification is: bridge `302 -> Access`, health check `200`, authenticated-data endpoint without JingYou Bearer `401`, and remote dev-login `404`.
- Cloudflare Access JWT audience/team-domain runtime values are persisted in the Windows user environment and loaded by the backend startup script.
- FastAPI is launched by the `JingYouHealthBackend` Windows scheduled task at user logon using `server/run-backend.ps1`; the task restarts on failure and ignores duplicate instances.

Remaining integration / optional work:

- Production Android wiring and the OWNER interactive Cloudflare login round trip have been verified. The remaining auth smoke is to perform the same first interactive login once with the MEMBER account and confirm it resolves to that account's `/api/me`, dashboard, refresh, and Coach history.


- Optionally add true streaming Coach events if the frontend wants backend-driven phase updates rather than local status animation.
