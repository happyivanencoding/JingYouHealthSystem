from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from health_store import connect  # noqa: E402
from auth import UserContext  # noqa: E402


def _con(user: UserContext) -> sqlite3.Connection:
    return connect(user.health_db)


def _json(value: str | None) -> Any:
    if not value:
        return None
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return None


def _latest_readiness(con: sqlite3.Connection) -> dict[str, Any] | None:
    row = con.execute(
        """SELECT event_date,payload_json FROM raw_records
           WHERE source='garmin' AND kind='training_readiness_raw'
           ORDER BY event_date DESC LIMIT 1"""
    ).fetchone()
    if not row:
        return None
    payload = _json(row["payload_json"])
    if isinstance(payload, list):
        items = [item for item in payload if isinstance(item, dict)]
        if not items:
            return None
        item = max(items, key=lambda x: str(x.get("timestampLocal") or x.get("timestamp") or ""))
        return {
            "date": row["event_date"],
            "score": item.get("score"),
            "level": item.get("level"),
            "sleep_score": item.get("sleepScore"),
            "recovery_time": item.get("recoveryTime"),
            "acute_load": item.get("acuteLoad"),
            "hrv_weekly_average": item.get("hrvWeeklyAverage"),
        }
    return None


def _latest_body_battery(con: sqlite3.Connection) -> dict[str, Any] | None:
    row = con.execute(
        """SELECT COALESCE(timestamp_local,timestamp_gmt,timestamp_key) AS ts,value
           FROM time_series_samples
           WHERE source='garmin' AND metric IN ('body_battery','body_battery_sparse')
             AND value IS NOT NULL
           ORDER BY ts DESC LIMIT 1"""
    ).fetchone()
    return {"timestamp": row["ts"], "value": row["value"]} if row else None


def dashboard(user: UserContext) -> dict[str, Any]:
    with _con(user) as con:
        day = con.execute(
            """SELECT * FROM daily_metrics WHERE source='garmin'
               ORDER BY date DESC LIMIT 1"""
        ).fetchone()
        hrv = con.execute(
            """SELECT date,status,weekly_avg,last_night_avg,last_night_5min_high,
                      baseline_balanced_low,baseline_balanced_upper
               FROM hrv_daily WHERE source='garmin'
               ORDER BY date DESC LIMIT 1"""
        ).fetchone()
        sleep = con.execute(
            """SELECT date,sleep_score,sleep_time_sec,deep_sleep_sec,rem_sleep_sec,
                      light_sleep_sec,awake_sleep_sec,sleep_start,sleep_end
               FROM sleep_sessions WHERE source='garmin'
               ORDER BY date DESC LIMIT 1"""
        ).fetchone()
        activities = [
            dict(row)
            for row in con.execute(
                """SELECT activity_id,activity_name,activity_type,start_time,distance_m,
                          duration_s,avg_hr,max_hr,training_effect,activity_training_load,calories
                   FROM activities WHERE source='garmin'
                   ORDER BY start_time DESC LIMIT 5"""
            ).fetchall()
        ]
        return {
            "user": {"display_name": user.display_name, "role": user.role},
            "date": day["date"] if day else None,
            "daily": dict(day) if day else None,
            "hrv": dict(hrv) if hrv else None,
            "sleep": dict(sleep) if sleep else None,
            "body_battery": _latest_body_battery(con),
            "readiness": _latest_readiness(con),
            "recent_activities": activities,
        }


def trends(user: UserContext, days: int = 30) -> dict[str, Any]:
    days = max(7, min(days, 180))
    with _con(user) as con:
        hrv = [
            dict(row)
            for row in con.execute(
                """SELECT date,last_night_avg,weekly_avg,status
                   FROM hrv_daily WHERE source='garmin'
                   ORDER BY date DESC LIMIT ?""",
                (days,),
            ).fetchall()[::-1]
        ]
        daily = [
            dict(row)
            for row in con.execute(
                """SELECT date,resting_hr,avg_stress,steps,sleep_sec,
                          body_battery_charged,body_battery_drained
                   FROM daily_metrics WHERE source='garmin'
                   ORDER BY date DESC LIMIT ?""",
                (days,),
            ).fetchall()[::-1]
        ]
        sleep = [
            dict(row)
            for row in con.execute(
                """SELECT date,sleep_score,sleep_time_sec,deep_sleep_sec,rem_sleep_sec
                   FROM sleep_sessions WHERE source='garmin'
                   ORDER BY date DESC LIMIT ?""",
                (days,),
            ).fetchall()[::-1]
        ]
        return {"days": days, "hrv": hrv, "daily": daily, "sleep": sleep}


def activities(user: UserContext, limit: int = 80, offset: int = 0) -> list[dict[str, Any]]:
    limit = max(1, min(limit, 200))
    offset = max(0, offset)
    with _con(user) as con:
        return [
            dict(row)
            for row in con.execute(
                """SELECT activity_id,activity_name,activity_type,start_time,distance_m,duration_s,
                          avg_hr,max_hr,avg_cadence,avg_power_w,elevation_gain_m,training_effect,
                          anaerobic_training_effect,activity_training_load,vo2max,calories,fit_path
                   FROM activities WHERE source='garmin'
                   ORDER BY start_time DESC LIMIT ? OFFSET ?""",
                (limit, offset),
            ).fetchall()
        ]


def agent_context(user: UserContext, days: int = 42) -> dict[str, Any]:
    return {
        "profile": {"display_name": user.display_name},
        "today": dashboard(user),
        "trends": trends(user, days=days),
        "recent_activities": activities(user, limit=24),
    }
