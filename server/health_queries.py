from __future__ import annotations

import json
import sqlite3
import sys
from contextlib import contextmanager
from collections import deque
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from health_store import connect  # noqa: E402
from auth import UserContext  # noqa: E402
from derived_metrics import compute_recovery, enrich_activity, recovery_history, sleep_clock  # noqa: E402
from training_method import compute_training_status  # noqa: E402
from training_history import compute_training_load_history  # noqa: E402


@contextmanager
def _con(user: UserContext):
    con = connect(user.health_db)
    try:
        yield con
    finally:
        con.close()


def _latest_body_battery(con: sqlite3.Connection) -> dict[str, Any] | None:
    row = con.execute(
        """SELECT COALESCE(timestamp_local,timestamp_gmt,timestamp_key) AS ts,value
           FROM time_series_samples
           WHERE source='garmin' AND metric IN ('body_battery','body_battery_sparse')
             AND value IS NOT NULL
           ORDER BY ts DESC LIMIT 1"""
    ).fetchone()
    return {"timestamp": row["ts"], "value": row["value"]} if row else None


def _timeline_timezone(con: sqlite3.Connection, target: date) -> tuple[timezone, str]:
    raw = con.execute(
        "SELECT payload_json FROM raw_records WHERE source='garmin' AND kind='stress_raw' AND event_date=?",
        (target.isoformat(),),
    ).fetchone()
    offset = timedelta(0)
    if raw is not None:
        payload = json.loads(raw["payload_json"])
        local = datetime.fromisoformat(str(payload["startTimestampLocal"])).replace(tzinfo=None)
        gmt = datetime.fromisoformat(str(payload["startTimestampGMT"])).replace(tzinfo=None)
        offset = local - gmt
    activity = con.execute(
        """SELECT timezone,start_time,start_time_gmt FROM activities
             WHERE source='garmin' AND substr(start_time,1,10)<=? ORDER BY start_time DESC LIMIT 1""",
        (target.isoformat(),),
    ).fetchone()
    if raw is None and activity is not None and activity["start_time"] and activity["start_time_gmt"]:
        local = datetime.fromisoformat(str(activity["start_time"])).replace(tzinfo=None)
        gmt = datetime.fromisoformat(str(activity["start_time_gmt"])).replace(tzinfo=None)
        offset = local - gmt
    name = str(activity["timezone"] or "UTC") if activity is not None else "UTC"
    return timezone(offset, name), name


def _sample_local_datetime(row: sqlite3.Row, zone: timezone) -> datetime | None:
    if row["timestamp_gmt"]:
        try:
            parsed = datetime.fromisoformat(str(row["timestamp_gmt"]).replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed.astimezone(zone)
        except ValueError:
            return None
    if row["timestamp_local"]:
        try:
            parsed = datetime.fromisoformat(str(row["timestamp_local"]).replace("Z", "+00:00"))
            return parsed.replace(tzinfo=zone) if parsed.tzinfo is None else parsed.astimezone(zone)
        except ValueError:
            return None
    return None


def _timeline_samples(
    con: sqlite3.Connection,
    metric: str,
    target: date,
    zone: timezone,
    *,
    null_invalid_stress: bool = False,
) -> list[dict[str, Any]]:
    start_local = datetime.combine(target, time.min, tzinfo=zone)
    end_local = start_local + timedelta(days=1)
    rows = con.execute(
        """SELECT timestamp_gmt,timestamp_local,timestamp_key,value
             FROM time_series_samples
            WHERE source='garmin' AND metric=? AND (
                (timestamp_gmt IS NOT NULL AND timestamp_gmt>=? AND timestamp_gmt<?)
                OR (timestamp_gmt IS NULL AND timestamp_local IS NOT NULL
                    AND timestamp_local>=? AND timestamp_local<?)
            )
            ORDER BY COALESCE(timestamp_gmt,timestamp_local,timestamp_key)""",
        (
            metric,
            start_local.astimezone(timezone.utc).isoformat(),
            end_local.astimezone(timezone.utc).isoformat(),
            start_local.replace(tzinfo=None).isoformat(),
            end_local.replace(tzinfo=None).isoformat(),
        ),
    ).fetchall()
    points: list[dict[str, Any]] = []
    for row in rows:
        local_dt = _sample_local_datetime(row, zone)
        if local_dt is None or local_dt.date() != target:
            continue
        raw_value = row["value"]
        value = float(raw_value) if isinstance(raw_value, (int, float)) else None
        if metric.startswith("body_battery"):
            if value is None or not 0.0 <= value <= 100.0:
                continue
        elif null_invalid_stress and (value is None or not 0.0 <= value <= 100.0):
            value = None
        points.append({"timestamp": local_dt.isoformat(timespec="seconds"), "value": value})
    return points


def _highest_stress_window(points: list[dict[str, Any]]) -> dict[str, Any] | None:
    window: deque[tuple[datetime, float]] = deque()
    best: tuple[float, datetime, datetime] | None = None
    for point in points:
        value = point.get("value")
        if not isinstance(value, (int, float)):
            continue
        try:
            stamp = datetime.fromisoformat(str(point["timestamp"]))
        except ValueError:
            continue
        while window and stamp - window[0][0] > timedelta(minutes=30):
            window.popleft()
        window.append((stamp, float(value)))
        if len(window) < 5:
            continue
        average = sum(item[1] for item in window) / len(window)
        if best is None or average > best[0]:
            best = (average, window[0][0], window[-1][0])
    if best is None:
        return None
    return {
        "start": best[1].isoformat(timespec="seconds"),
        "end": best[2].isoformat(timespec="seconds"),
        "average": round(best[0], 1),
    }


def _timeline_summary(
    body_battery: list[dict[str, Any]],
    stress: list[dict[str, Any]],
    daily: sqlite3.Row | None,
    activity_count: int,
) -> dict[str, Any]:
    valid_stress = [point for point in stress if isinstance(point.get("value"), (int, float))]
    latest_body = body_battery[-1] if body_battery else None
    latest_stress = valid_stress[-1] if valid_stress else None
    peak_body = max(body_battery, key=lambda point: float(point["value"])) if body_battery else None
    average_stress = daily["avg_stress"] if daily and daily["avg_stress"] is not None else None
    if average_stress is None and valid_stress:
        average_stress = sum(float(point["value"]) for point in valid_stress) / len(valid_stress)
    return {
        "latest_body_battery": latest_body["value"] if latest_body else None,
        "latest_body_battery_at": latest_body["timestamp"] if latest_body else None,
        "body_battery_peak": peak_body["value"] if peak_body else None,
        "body_battery_peak_at": peak_body["timestamp"] if peak_body else None,
        "body_battery_drop_since_peak": (
            round(float(peak_body["value"]) - float(latest_body["value"]), 1)
            if peak_body and latest_body else None
        ),
        "body_battery_charged": daily["body_battery_charged"] if daily else None,
        "body_battery_drained": daily["body_battery_drained"] if daily else None,
        "latest_stress": latest_stress["value"] if latest_stress else None,
        "latest_stress_at": latest_stress["timestamp"] if latest_stress else None,
        "average_stress": round(float(average_stress), 1) if average_stress is not None else None,
        "highest_stress_window": _highest_stress_window(stress),
        "activity_count": activity_count,
    }


_ACTIVITY_COLUMNS = """
    a.activity_id,a.activity_name,a.activity_type,a.start_time,a.start_time_gmt,
    a.timezone,a.distance_m,a.duration_s,a.moving_duration_s,a.elapsed_duration_s,
    a.avg_speed_mps,a.avg_moving_speed_mps,a.max_speed_mps,a.avg_hr,a.max_hr,a.min_hr,
    a.avg_cadence,a.max_cadence,a.stride_length_cm,a.ground_contact_time_ms,
    a.vertical_oscillation_cm,a.vertical_ratio,a.avg_power_w,a.max_power_w,
    a.normalized_power_w,a.elevation_gain_m,a.elevation_loss_m,a.max_elevation_m,
    a.min_elevation_m,a.avg_temperature,a.max_temperature,a.min_temperature,
    a.training_effect,a.anaerobic_training_effect,a.training_effect_label,
    a.activity_training_load,a.vo2max,a.calories,a.steps,a.body_battery_diff,
    a.start_lat,a.start_lon,a.end_lat,a.end_lon,a.fit_path,
    e.effort_rpe,e.category_override
"""


def _activity_rows(
    con: sqlite3.Connection,
    *,
    limit: int | None = None,
    offset: int = 0,
    activity_id: str | None = None,
) -> list[dict[str, Any]]:
    where = "a.source='garmin'"
    params_list: list[Any] = []
    if activity_id is not None:
        where += " AND a.activity_id=?"
        params_list.append(activity_id)
    suffix = ""
    if limit is not None:
        suffix = " LIMIT ? OFFSET ?"
        params_list.extend((limit, offset))
    rows = con.execute(
        f"""SELECT {_ACTIVITY_COLUMNS}
             FROM activities a
             LEFT JOIN activity_effort e ON e.activity_id=a.activity_id
            WHERE {where}
            ORDER BY a.start_time DESC{suffix}""",
        tuple(params_list),
    ).fetchall()
    return [enrich_activity(dict(row)) for row in rows]


def _recovery_rows(
    con: sqlite3.Connection,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    sleep_rows = [
        dict(row)
        for row in con.execute(
            """SELECT date,sleep_score,sleep_time_sec,deep_sleep_sec,rem_sleep_sec,
                      light_sleep_sec,awake_sleep_sec,raw_json
                 FROM sleep_sessions WHERE source='garmin'
                   AND (sleep_score IS NOT NULL OR sleep_time_sec IS NOT NULL
                        OR sleep_start IS NOT NULL OR sleep_end IS NOT NULL)
                ORDER BY date"""
        ).fetchall()
    ]
    hrv_rows = [
        dict(row)
        for row in con.execute(
            """SELECT date,last_night_avg,weekly_avg,status
                 FROM hrv_daily WHERE source='garmin'
                   AND (status IS NOT NULL OR weekly_avg IS NOT NULL OR last_night_avg IS NOT NULL)
                ORDER BY date"""
        ).fetchall()
    ]
    daily_rows = [
        dict(row)
        for row in con.execute(
            """SELECT date,resting_hr,avg_stress,steps,sleep_sec,
                      body_battery_charged,body_battery_drained
                 FROM daily_metrics WHERE source='garmin'
                   AND (resting_hr IS NOT NULL OR avg_stress IS NOT NULL OR steps IS NOT NULL
                        OR sleep_sec IS NOT NULL)
                ORDER BY date"""
        ).fetchall()
    ]
    activity_rows = _activity_rows(con)
    return sleep_rows, hrv_rows, daily_rows, activity_rows


def _ensure_training_tables(con: sqlite3.Connection) -> None:
    con.executescript(
        """
        CREATE TABLE IF NOT EXISTS training_preferences (
            id INTEGER PRIMARY KEY CHECK(id=1),
            goal TEXT NOT NULL CHECK(goal IN ('balanced','endurance','strength')),
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS training_checkins (
            date TEXT PRIMARY KEY,
            feeling TEXT CHECK(feeling IS NULL OR feeling IN ('fresh','normal','tired')),
            updated_at TEXT NOT NULL
        );
        """
    )
    con.commit()


def activity_coverage_dates(
    con: sqlite3.Connection,
    target_date: str | date | None = None,
    lookback_days: int = 35,
) -> set[date]:
    """Derive activity-list coverage from completed sync metadata only.

    This is read-only.  Wellness dates never prove that an activity list was
    checked.  A full activities/all sync declares its requested date range;
    a recent list is usable only when fetched during a completed refresh.
    """

    target = target_date if isinstance(target_date, date) else date.fromisoformat(str(target_date)[:10]) if target_date else date.today()
    lookback_days = max(35, int(lookback_days))
    start = target - timedelta(days=lookback_days - 1)
    covered: set[date] = set()

    def add_range(first: date, last: date) -> None:
        first = max(first, start)
        last = min(last, target)
        if first <= last:
            covered.update(first + timedelta(days=index) for index in range((last - first).days + 1))

    for row in con.execute(
        """SELECT start_date,end_date FROM sync_runs
           WHERE source='garmin' AND kind IN ('activities','all') AND status='completed'
             AND start_date IS NOT NULL AND end_date IS NOT NULL"""
    ).fetchall():
        try:
            add_range(date.fromisoformat(str(row["start_date"])), date.fromisoformat(str(row["end_date"])))
        except ValueError:
            continue

    def parse_dt(value: Any) -> datetime | None:
        if not value:
            return None
        try:
            parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        except ValueError:
            return None
        return parsed

    refreshes = [
        (parse_dt(row["started_at"]), parse_dt(row["finished_at"]))
        for row in con.execute(
            """SELECT started_at,finished_at FROM sync_runs
               WHERE source='garmin' AND kind='refresh' AND status='completed'
                 AND started_at IS NOT NULL AND finished_at IS NOT NULL"""
        ).fetchall()
    ]
    recent_rows = con.execute(
        """SELECT payload_json,fetched_at FROM raw_records
           WHERE source='garmin' AND kind='activity_list_recent' AND record_key='latest'
           ORDER BY fetched_at DESC"""
    ).fetchall()
    for row in recent_rows:
        fetched = parse_dt(row["fetched_at"])
        if fetched is None or not any(started and finished and started <= fetched <= finished for started, finished in refreshes):
            continue
        try:
            payload = json.loads(row["payload_json"])
        except (TypeError, ValueError, json.JSONDecodeError):
            continue
        if not isinstance(payload, list):
            continue
        local_dates = [
            date.fromisoformat(str(item.get("startTimeLocal"))[:10])
            for item in payload
            if isinstance(item, dict) and item.get("startTimeLocal")
            and _safe_iso_date(str(item.get("startTimeLocal"))[:10])
        ]
        if not local_dates:
            continue
        fetched_local = fetched.astimezone().date() if fetched.tzinfo else fetched.date()
        oldest = min(local_dates)
        # A full list with fewer than 20 rows is the complete current list;
        # exactly 20 is capped, so the oldest returned date is excluded.
        first = oldest if len(payload) < 20 else oldest + timedelta(days=1)
        add_range(first, fetched_local)
    return covered


def _safe_iso_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
        return True
    except ValueError:
        return False


def _training_goal(con: sqlite3.Connection, override: str | None = None) -> str:
    if override in {"balanced", "endurance", "strength"}:
        return override
    row = con.execute("SELECT goal FROM training_preferences WHERE id=1").fetchone()
    return str(row["goal"]) if row else "balanced"


def _training_checkin(con: sqlite3.Connection, target_date: str | None) -> dict[str, Any]:
    if not target_date:
        return {"date": None, "feeling": None}
    row = con.execute("SELECT date,feeling FROM training_checkins WHERE date=?", (target_date,)).fetchone()
    return {"date": target_date, "feeling": row["feeling"] if row else None}


def _training_from_connection(
    con: sqlite3.Connection,
    *,
    recovery: dict[str, Any] | None,
    target_date: str | None,
    goal: str | None = None,
    rows: tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]] | None = None,
) -> dict[str, Any] | None:
    if not target_date:
        return None
    _ensure_training_tables(con)
    sleep_rows, hrv_rows, daily_rows, activity_rows = rows or _recovery_rows(con)
    checkin = _training_checkin(con, target_date)
    coverage_dates = activity_coverage_dates(con, target_date, lookback_days=56)
    return compute_training_status(
        sleep_rows=sleep_rows,
        daily_rows=daily_rows,
        hrv_rows=hrv_rows,
        enriched_activity_rows=activity_rows,
        recovery=recovery,
        target_date=target_date,
        goal=_training_goal(con, goal),
        checkin=checkin,
        activity_coverage_dates=coverage_dates,
    )


def _sleep_response(row: dict[str, Any]) -> dict[str, Any]:
    payload = row.pop("raw_json", None)
    clock = sleep_clock(payload)
    row.update(clock)
    # Existing clients use these names.  They now contain only explicit Local
    # values, never the GMT fallback that older sync rows may have stored.
    row["sleep_start"] = clock["sleep_start_local"]
    row["sleep_end"] = clock["sleep_end_local"]
    return row


def dashboard(user: UserContext) -> dict[str, Any]:
    with _con(user) as con:
        today = date.today().isoformat()
        day = con.execute(
            """SELECT date,steps,resting_hr,avg_stress,calories,active_min,sleep_sec,
                      deep_sleep_sec,rem_sleep_sec,body_battery_charged,body_battery_drained,
                      floors,intensity_min,fetched_at
               FROM daily_metrics WHERE source='garmin'
               AND (resting_hr IS NOT NULL OR avg_stress IS NOT NULL OR steps IS NOT NULL
                    OR sleep_sec IS NOT NULL OR calories IS NOT NULL
                    OR body_battery_charged IS NOT NULL OR body_battery_drained IS NOT NULL)
               AND date <= ?
               ORDER BY date DESC LIMIT 1""",
            (today,),
        ).fetchone()
        hrv = con.execute(
            """SELECT date,status,weekly_avg,last_night_avg,last_night_5min_high,
                      baseline_balanced_low,baseline_balanced_upper
               FROM hrv_daily WHERE source='garmin'
                 AND (status IS NOT NULL OR weekly_avg IS NOT NULL OR last_night_avg IS NOT NULL)
                 AND date <= ?
               ORDER BY date DESC LIMIT 1""",
            (today,),
        ).fetchone()
        sleep = con.execute(
            """SELECT date,sleep_score,sleep_time_sec,deep_sleep_sec,rem_sleep_sec,
                      light_sleep_sec,awake_sleep_sec,raw_json
               FROM sleep_sessions WHERE source='garmin'
                 AND (sleep_score IS NOT NULL OR sleep_time_sec IS NOT NULL
                      OR sleep_start IS NOT NULL OR sleep_end IS NOT NULL)
                 AND date <= ?
               ORDER BY date DESC LIMIT 1""",
            (today,),
        ).fetchone()
        body_battery = _latest_body_battery(con)
        sleep_rows, hrv_rows, daily_rows, activity_rows = _recovery_rows(con)
        readiness = compute_recovery(
            sleep_rows=sleep_rows,
            hrv_rows=hrv_rows,
            daily_rows=daily_rows,
            activity_rows=activity_rows,
        )
        recent_activities = activity_rows[:5]
        sleep_payload = _sleep_response(dict(sleep)) if sleep else None
        component_dates = [
            str(day["date"]) if day else None,
            str(hrv["date"]) if hrv else None,
            str(sleep_payload["date"]) if sleep_payload else None,
            str(readiness["date"]) if readiness.get("date") else None,
        ]
        recovery_anchor = str(readiness["date"]) if readiness.get("date") else None
        training = _training_from_connection(
            con,
            recovery=readiness,
            target_date=recovery_anchor,
            rows=(sleep_rows, hrv_rows, daily_rows, activity_rows),
        )
        return {
            "user": {"display_name": user.display_name, "role": user.role},
            "date": recovery_anchor or max((value for value in component_dates if value), default=None),
            "daily": dict(day) if day else None,
            "hrv": dict(hrv) if hrv else None,
            "sleep": sleep_payload,
            "body_battery": body_battery,
            "readiness": readiness,
            "training": training,
            "freshness": {
                "daily": day["date"] if day else None,
                "hrv": hrv["date"] if hrv else None,
                "sleep": sleep_payload["date"] if sleep_payload else None,
                "body_battery": body_battery["timestamp"] if body_battery else None,
                "readiness": readiness.get("date"),
            },
            "recent_activities": recent_activities,
        }

def state_timeline(user: UserContext, target_date: str | date | None = None) -> dict[str, Any]:
    requested = target_date if isinstance(target_date, date) else date.fromisoformat(target_date) if target_date else None
    with _con(user) as con:
        target = requested or date.today()
        zone, zone_name = _timeline_timezone(con, target)
        body_battery = _timeline_samples(con, "body_battery", target, zone)
        if not body_battery:
            body_battery = _timeline_samples(con, "body_battery_sparse", target, zone)
        stress = _timeline_samples(con, "stress_level", target, zone, null_invalid_stress=True)
        daily = con.execute(
            """SELECT avg_stress,body_battery_charged,body_battery_drained
                 FROM daily_metrics WHERE source='garmin' AND date=?""",
            (target.isoformat(),),
        ).fetchone()
        sleep = con.execute(
            """SELECT date,sleep_score,sleep_time_sec,deep_sleep_sec,rem_sleep_sec,
                      light_sleep_sec,awake_sleep_sec,raw_json
                 FROM sleep_sessions WHERE source='garmin' AND date=?""",
            (target.isoformat(),),
        ).fetchone()
        sleep_payload = _sleep_response(dict(sleep)) if sleep else None
        day_activities = [
            row for row in _activity_rows(con)
            if str(row.get("start_time") or "")[:10] == target.isoformat()
        ]
        activities_payload = [
            {
                "activity_id": row.get("activity_id"),
                "activity_name": row.get("activity_name"),
                "activity_type": row.get("activity_type"),
                "start_time": row.get("start_time"),
                "duration_s": row.get("duration_s"),
                "category": row.get("category"),
            }
            for row in day_activities
        ]
        return {
            "date": target.isoformat(),
            "timezone": zone_name,
            "generated_at": datetime.now(zone).isoformat(timespec="seconds"),
            "body_battery": body_battery,
            "stress": stress,
            "sleep": {
                "start": sleep_payload.get("sleep_start_local") if sleep_payload else None,
                "end": sleep_payload.get("sleep_end_local") if sleep_payload else None,
            },
            "activities": activities_payload,
            "summary": _timeline_summary(body_battery, stress, daily, len(activities_payload)),
        }


def trends(user: UserContext, days: int = 30, training_days: int | None = None) -> dict[str, Any]:
    days = max(7, min(days, 180))
    with _con(user) as con:
        sleep_rows, hrv_rows, daily_rows, activity_rows = _recovery_rows(con)
        today = date.today().isoformat()

        def current_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
            return [row for row in rows if str(row.get("date") or "")[:10] <= today]

        def limited(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
            return rows[-days:]

        current_sleep_rows = current_rows(sleep_rows)
        current_hrv_rows = current_rows(hrv_rows)
        current_daily_rows = current_rows(daily_rows)
        current_activity_rows = [
            row for row in activity_rows if str(row.get("start_time") or row.get("date") or "")[:10] <= today
        ]
        hrv = limited(current_hrv_rows)
        daily = limited(current_daily_rows)
        sleep = [_sleep_response(dict(row)) for row in limited(current_sleep_rows)]
        history_dates = [str(row["date"]) for row in sleep]
        readiness = recovery_history(
            dates=history_dates,
            sleep_rows=current_sleep_rows,
            hrv_rows=current_hrv_rows,
            daily_rows=current_daily_rows,
            activity_rows=current_activity_rows,
        )
        sleep_clocks = [
            {
                "date": row["date"],
                "sleep_start_local": row.get("sleep_start_local"),
                "sleep_end_local": row.get("sleep_end_local"),
                "clock_source": row.get("clock_source"),
                "clock_offset_changed": row.get("clock_offset_changed"),
            }
            for row in sleep
        ]
        result = {
            "days": days,
            "hrv": hrv,
            "daily": daily,
            "sleep": sleep,
            "readiness": readiness,
            "sleep_clocks": sleep_clocks,
        }
        if training_days is not None:
            coverage_end = max(
                [
                    str(row.get("date"))[:10]
                    for row in sleep_rows + daily_rows + hrv_rows
                    if row.get("date") and str(row.get("date"))[:10] <= today
                ]
                + [str(row.get("start_time"))[:10] for row in activity_rows if row.get("start_time") and str(row.get("start_time"))[:10] <= today],
                default=today,
            )
            coverage_dates = activity_coverage_dates(con, coverage_end, lookback_days=training_days + 55)
            result["training_load"] = compute_training_load_history(
                activity_rows,
                coverage_dates,
                end_date=coverage_end,
                days=training_days,
            )
        return result


def activities(user: UserContext, limit: int = 80, offset: int = 0) -> list[dict[str, Any]]:
    limit = max(1, min(limit, 200))
    offset = max(0, offset)
    with _con(user) as con:
        return _activity_rows(con, limit=limit, offset=offset)


def training(user: UserContext, goal: str | None = None, target_date: str | None = None) -> dict[str, Any] | None:
    with _con(user) as con:
        sleep_rows, hrv_rows, daily_rows, activity_rows = _recovery_rows(con)
        readiness = compute_recovery(
            sleep_rows=sleep_rows,
            hrv_rows=hrv_rows,
            daily_rows=daily_rows,
            activity_rows=activity_rows,
            anchor_date=target_date,
        )
        return _training_from_connection(
            con,
            recovery=readiness,
            target_date=str(readiness["date"]) if readiness.get("date") else None,
            goal=goal,
            rows=(sleep_rows, hrv_rows, daily_rows, activity_rows),
        )


def activity_by_id(user: UserContext, activity_id: str) -> dict[str, Any] | None:
    with _con(user) as con:
        rows = _activity_rows(con, limit=1, activity_id=activity_id)
        return rows[0] if rows else None


def agent_context(user: UserContext, days: int = 42) -> dict[str, Any]:
    timeline = state_timeline(user)
    return {
        "profile": {"display_name": user.display_name},
        "today": dashboard(user),
        "today_state": {
            "date": timeline["date"],
            "timezone": timeline["timezone"],
            "summary": timeline["summary"],
            "sleep": timeline["sleep"],
            "activities": timeline["activities"],
        },
        "trends": trends(user, days=days),
        "recent_activities": activities(user, limit=24),
    }
