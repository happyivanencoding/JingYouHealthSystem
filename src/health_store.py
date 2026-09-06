from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "2"

SCHEMA = r"""
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

CREATE TABLE IF NOT EXISTS metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    kind TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL,
    start_date TEXT,
    end_date TEXT,
    records_written INTEGER NOT NULL DEFAULT 0,
    note TEXT
);

CREATE TABLE IF NOT EXISTS raw_records (
    source TEXT NOT NULL,
    kind TEXT NOT NULL,
    record_key TEXT NOT NULL,
    event_date TEXT,
    payload_json TEXT NOT NULL,
    fetched_at TEXT NOT NULL,
    PRIMARY KEY (source, kind, record_key)
);
CREATE INDEX IF NOT EXISTS idx_raw_records_date ON raw_records(source, kind, event_date);

CREATE TABLE IF NOT EXISTS legacy_artifacts (
    relative_path TEXT PRIMARY KEY,
    media_type TEXT NOT NULL,
    content BLOB NOT NULL,
    imported_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS daily_metrics (
    source TEXT NOT NULL,
    date TEXT NOT NULL,
    steps INTEGER,
    resting_hr REAL,
    avg_stress REAL,
    calories REAL,
    active_min REAL,
    sleep_sec REAL,
    deep_sleep_sec REAL,
    rem_sleep_sec REAL,
    body_battery_charged REAL,
    body_battery_drained REAL,
    floors REAL,
    intensity_min REAL,
    raw_json TEXT,
    fetched_at TEXT,
    PRIMARY KEY (source, date)
);

CREATE TABLE IF NOT EXISTS hrv_daily (
    source TEXT NOT NULL,
    date TEXT NOT NULL,
    status TEXT,
    weekly_avg REAL,
    last_night_avg REAL,
    last_night_5min_high REAL,
    baseline_low_upper REAL,
    baseline_balanced_low REAL,
    baseline_balanced_upper REAL,
    raw_json TEXT,
    fetched_at TEXT,
    PRIMARY KEY (source, date)
);

CREATE TABLE IF NOT EXISTS sleep_sessions (
    source TEXT NOT NULL,
    date TEXT NOT NULL,
    sleep_score REAL,
    sleep_time_sec REAL,
    deep_sleep_sec REAL,
    rem_sleep_sec REAL,
    light_sleep_sec REAL,
    awake_sleep_sec REAL,
    sleep_start TEXT,
    sleep_end TEXT,
    raw_json TEXT,
    fetched_at TEXT,
    PRIMARY KEY (source, date)
);

CREATE TABLE IF NOT EXISTS time_series_samples (
    source TEXT NOT NULL,
    metric TEXT NOT NULL,
    timestamp_key TEXT NOT NULL,
    timestamp_gmt TEXT,
    timestamp_local TEXT,
    value REAL,
    unit TEXT,
    context_key TEXT NOT NULL DEFAULT '',
    raw_json TEXT,
    PRIMARY KEY (source, metric, timestamp_key, context_key)
);
CREATE INDEX IF NOT EXISTS idx_ts_metric_time ON time_series_samples(source, metric, timestamp_local);

CREATE TABLE IF NOT EXISTS activities (
    source TEXT NOT NULL,
    activity_id TEXT NOT NULL,
    activity_name TEXT,
    activity_type TEXT,
    start_time TEXT,
    start_time_gmt TEXT,
    timezone TEXT,
    distance_m REAL,
    duration_s REAL,
    moving_duration_s REAL,
    elapsed_duration_s REAL,
    avg_speed_mps REAL,
    avg_moving_speed_mps REAL,
    max_speed_mps REAL,
    avg_hr REAL,
    max_hr REAL,
    min_hr REAL,
    avg_cadence REAL,
    max_cadence REAL,
    stride_length_cm REAL,
    ground_contact_time_ms REAL,
    vertical_oscillation_cm REAL,
    vertical_ratio REAL,
    avg_power_w REAL,
    max_power_w REAL,
    normalized_power_w REAL,
    elevation_gain_m REAL,
    elevation_loss_m REAL,
    max_elevation_m REAL,
    min_elevation_m REAL,
    avg_temperature REAL,
    max_temperature REAL,
    min_temperature REAL,
    training_effect REAL,
    anaerobic_training_effect REAL,
    training_effect_label TEXT,
    activity_training_load REAL,
    vo2max REAL,
    calories REAL,
    steps INTEGER,
    body_battery_diff REAL,
    start_lat REAL,
    start_lon REAL,
    end_lat REAL,
    end_lon REAL,
    fit_path TEXT,
    raw_json TEXT,
    fetched_at TEXT,
    PRIMARY KEY (source, activity_id)
);
CREATE INDEX IF NOT EXISTS idx_activities_start ON activities(source, start_time);

-- User-entered activity effort and category overrides live in each user's
-- already-isolated health database.  activity_id is therefore sufficient as
-- the primary key and never crosses the user boundary.
CREATE TABLE IF NOT EXISTS activity_effort (
    activity_id TEXT PRIMARY KEY,
    effort_rpe REAL CHECK(effort_rpe IS NULL OR (effort_rpe >= 0 AND effort_rpe <= 10)),
    category_override TEXT CHECK(category_override IS NULL OR category_override IN ('easy_aerobic','hard_aerobic','anaerobic','strength')),
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS activity_laps (
    source TEXT NOT NULL,
    activity_id TEXT NOT NULL,
    lap_number INTEGER NOT NULL,
    start_time_gmt TEXT,
    distance_m REAL,
    duration_s REAL,
    moving_duration_s REAL,
    elapsed_duration_s REAL,
    avg_speed_mps REAL,
    avg_moving_speed_mps REAL,
    max_speed_mps REAL,
    avg_hr REAL,
    max_hr REAL,
    avg_cadence REAL,
    max_cadence REAL,
    stride_length_cm REAL,
    ground_contact_time_ms REAL,
    vertical_oscillation_cm REAL,
    avg_power_w REAL,
    max_power_w REAL,
    normalized_power_w REAL,
    elevation_gain_m REAL,
    elevation_loss_m REAL,
    avg_temperature REAL,
    calories REAL,
    raw_json TEXT,
    PRIMARY KEY (source, activity_id, lap_number)
);

CREATE TABLE IF NOT EXISTS whoop_cycles (
    cycle_start TEXT PRIMARY KEY,
    cycle_end TEXT,
    timezone TEXT,
    recovery_pct REAL,
    resting_hr REAL,
    hrv_ms REAL,
    skin_temp_c REAL,
    blood_oxygen REAL,
    day_strain REAL,
    energy_cal REAL,
    max_hr REAL,
    avg_hr REAL,
    sleep_onset TEXT,
    wake_onset TEXT,
    sleep_perf_pct REAL,
    resp_rate REAL,
    asleep_min REAL,
    in_bed_min REAL,
    light_sleep_min REAL,
    deep_sleep_min REAL,
    rem_sleep_min REAL,
    awake_min REAL,
    sleep_need_min REAL,
    sleep_debt_min REAL,
    sleep_efficiency_pct REAL,
    sleep_consistency_pct REAL,
    source TEXT,
    imported_at TEXT
);

CREATE TABLE IF NOT EXISTS whoop_sleeps (
    id INTEGER PRIMARY KEY,
    cycle_start TEXT,
    sleep_onset TEXT,
    wake_onset TEXT,
    timezone TEXT,
    sleep_perf_pct REAL,
    resp_rate REAL,
    asleep_min REAL,
    in_bed_min REAL,
    light_sleep_min REAL,
    deep_sleep_min REAL,
    rem_sleep_min REAL,
    awake_min REAL,
    sleep_need_min REAL,
    sleep_debt_min REAL,
    sleep_efficiency_pct REAL,
    sleep_consistency_pct REAL,
    is_nap INTEGER,
    source TEXT,
    imported_at TEXT
);

CREATE TABLE IF NOT EXISTS whoop_workouts (
    id INTEGER PRIMARY KEY,
    cycle_start TEXT,
    workout_start TEXT,
    workout_end TEXT,
    timezone TEXT,
    duration_min REAL,
    activity_name TEXT,
    strain REAL,
    energy_cal REAL,
    max_hr REAL,
    avg_hr REAL,
    hr_zone1_pct REAL,
    hr_zone2_pct REAL,
    hr_zone3_pct REAL,
    hr_zone4_pct REAL,
    hr_zone5_pct REAL,
    gps_enabled INTEGER,
    source TEXT,
    imported_at TEXT
);

CREATE TABLE IF NOT EXISTS whoop_journal (
    id INTEGER PRIMARY KEY,
    cycle_start TEXT,
    question TEXT,
    answered_yes INTEGER,
    notes TEXT,
    source TEXT,
    imported_at TEXT
);

CREATE TABLE IF NOT EXISTS chat_threads (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id TEXT PRIMARY KEY,
    thread_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('user','assistant','status')),
    content TEXT NOT NULL,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'complete',
    metadata_json TEXT,
    FOREIGN KEY(thread_id) REFERENCES chat_threads(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_thread_time
    ON chat_messages(thread_id, created_at);
"""


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str)


def connect(db_path: str | Path) -> sqlite3.Connection:
    path = Path(db_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(path)
    con.row_factory = sqlite3.Row
    con.executescript(SCHEMA)
    con.execute(
        "INSERT INTO metadata(key,value) VALUES('schema_version',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        (SCHEMA_VERSION,),
    )
    con.commit()
    return con


def put_raw(
    con: sqlite3.Connection,
    source: str,
    kind: str,
    record_key: str,
    payload: Any,
    *,
    event_date: str | None = None,
    fetched_at: str | None = None,
) -> None:
    con.execute(
        """INSERT INTO raw_records(source,kind,record_key,event_date,payload_json,fetched_at)
           VALUES(?,?,?,?,?,?)
           ON CONFLICT(source,kind,record_key) DO UPDATE SET
             event_date=excluded.event_date,
             payload_json=excluded.payload_json,
             fetched_at=excluded.fetched_at""",
        (source, kind, record_key, event_date, json_dumps(payload), fetched_at or utc_now()),
    )


def put_sample(
    con: sqlite3.Connection,
    source: str,
    metric: str,
    timestamp_key: str,
    value: float | int | None,
    *,
    timestamp_gmt: str | None = None,
    timestamp_local: str | None = None,
    unit: str | None = None,
    context_key: str = "",
    raw: Any = None,
) -> None:
    con.execute(
        """INSERT INTO time_series_samples
           (source,metric,timestamp_key,timestamp_gmt,timestamp_local,value,unit,context_key,raw_json)
           VALUES(?,?,?,?,?,?,?,?,?)
           ON CONFLICT(source,metric,timestamp_key,context_key) DO UPDATE SET
             timestamp_gmt=excluded.timestamp_gmt,
             timestamp_local=excluded.timestamp_local,
             value=excluded.value,
             unit=excluded.unit,
             raw_json=excluded.raw_json""",
        (
            source,
            metric,
            timestamp_key,
            timestamp_gmt,
            timestamp_local,
            value,
            unit,
            context_key,
            json_dumps(raw) if raw is not None else None,
        ),
    )
