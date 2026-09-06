from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from garminconnect import Garmin  # noqa: E402
from garminconnect.exceptions import (  # noqa: E402
    GarminConnectConnectionError,
    GarminConnectTooManyRequestsError,
)
from health_store import connect, json_dumps, put_raw, put_sample, utc_now  # noqa: E402

USER_ID = "_unconfigured"
USER_ROOT = ROOT / "data" / "users" / USER_ID
GARMIN_HOME = USER_ROOT / "garmin"
DB_PATH = USER_ROOT / "health.db"
FIT_DIR = USER_ROOT / "activities" / "fit"
DEFAULT_START = date(2026, 4, 21)


def resolve_profile(profile: str | None, user_id: str | None) -> str:
    if user_id:
        return user_id
    if profile:
        profiles_path = ROOT / "data" / "app" / "profiles.json"
        profiles = json.loads(profiles_path.read_text(encoding="utf-8-sig"))
        for name, entry in profiles.items():
            if name.casefold() == profile.casefold():
                return str(entry["user_id"])
        raise SystemExit(f"Unknown profile: {profile}")
    owner = json.loads((ROOT / "data" / "app" / "owner.json").read_text(encoding="utf-8-sig"))
    return str(owner["user_id"])


def configure_user(user_id: str) -> None:
    global USER_ID, USER_ROOT, GARMIN_HOME, DB_PATH, FIT_DIR
    USER_ID = user_id
    USER_ROOT = ROOT / "data" / "users" / USER_ID
    GARMIN_HOME = USER_ROOT / "garmin"
    DB_PATH = USER_ROOT / "health.db"
    FIT_DIR = USER_ROOT / "activities" / "fit"


def daterange(start: date, end: date):
    d = start
    while d <= end:
        yield d
        d += timedelta(days=1)


def epoch_iso(value: Any) -> str | None:
    if not isinstance(value, (int, float)):
        return None
    return datetime.fromtimestamp(value / 1000.0, tz=timezone.utc).isoformat()


def nested(obj: Any, *path: str) -> Any:
    cur = obj
    for key in path:
        if not isinstance(cur, dict):
            return None
        cur = cur.get(key)
    return cur


class Sync:
    def __init__(self, *, refresh: bool = False, delay: float = 0.12):
        self.refresh = refresh
        self.delay = delay
        self.con = connect(DB_PATH)
        self.client = Garmin()
        self.client.login(tokenstore=str(GARMIN_HOME))
        self.calls = 0
        self.writes = 0
        FIT_DIR.mkdir(parents=True, exist_ok=True)

    def close(self):
        self.con.commit()
        self.con.close()

    def has_raw(self, kind: str, key: str) -> bool:
        if self.refresh:
            return False
        return (
            self.con.execute(
                "SELECT 1 FROM raw_records WHERE source='garmin' AND kind=? AND record_key=?",
                (kind, key),
            ).fetchone()
            is not None
        )

    def call(
        self,
        kind: str,
        key: str,
        fn: Callable[..., Any],
        *args: Any,
        event_date: str | None = None,
        **kwargs: Any,
    ) -> Any:
        if self.has_raw(kind, key):
            row = self.con.execute(
                "SELECT payload_json FROM raw_records WHERE source='garmin' AND kind=? AND record_key=?",
                (kind, key),
            ).fetchone()
            return json.loads(row[0]) if row else None

        delays = (0, 20, 60)
        for attempt, wait in enumerate(delays):
            if wait:
                print(f"RATE_LIMIT_WAIT {wait}s {kind} {key}", flush=True)
                time.sleep(wait)
            try:
                payload = fn(*args, **kwargs)
                put_raw(self.con, "garmin", kind, key, payload, event_date=event_date)
                self.con.commit()
                self.calls += 1
                self.writes += 1
                if self.delay:
                    time.sleep(self.delay)
                return payload
            except GarminConnectTooManyRequestsError:
                if attempt == len(delays) - 1:
                    raise
            except GarminConnectConnectionError as exc:
                if "429" in str(exc) and attempt < len(delays) - 1:
                    continue
                # Missing/not-applicable endpoints are useful sync state but not measurements.
                put_raw(
                    self.con,
                    "garmin",
                    kind,
                    key,
                    {"_status": "unavailable", "error": str(exc)},
                    event_date=event_date,
                )
                self.con.commit()
                return None
            except Exception as exc:
                put_raw(
                    self.con,
                    "garmin",
                    kind,
                    key,
                    {"_status": "unavailable", "error_type": type(exc).__name__, "error": str(exc)},
                    event_date=event_date,
                )
                self.con.commit()
                return None
        return None

    def put_daily(self, day: str, *, stats=None, heart=None, stress=None, sleep=None, body=None):
        existing = self.con.execute(
            "SELECT * FROM daily_metrics WHERE source='garmin' AND date=?", (day,)
        ).fetchone()
        old = dict(existing) if existing else {}
        dto = (sleep or {}).get("dailySleepDTO", {}) if isinstance(sleep, dict) else {}
        values = {
            "steps": nested(stats, "totalSteps") or old.get("steps"),
            "resting_hr": nested(heart, "restingHeartRate") or nested(stats, "restingHeartRate") or old.get("resting_hr"),
            "avg_stress": nested(stress, "avgStressLevel") or old.get("avg_stress"),
            "calories": nested(stats, "totalKilocalories") or nested(stats, "totalCalories") or old.get("calories"),
            "active_min": nested(stats, "activeSeconds") / 60 if isinstance(nested(stats, "activeSeconds"), (int, float)) else old.get("active_min"),
            "sleep_sec": dto.get("sleepTimeSeconds") or old.get("sleep_sec"),
            "deep_sleep_sec": dto.get("deepSleepSeconds") or old.get("deep_sleep_sec"),
            "rem_sleep_sec": dto.get("remSleepSeconds") or old.get("rem_sleep_sec"),
            "body_battery_charged": (body or {}).get("charged") if isinstance(body, dict) else old.get("body_battery_charged"),
            "body_battery_drained": (body or {}).get("drained") if isinstance(body, dict) else old.get("body_battery_drained"),
            "floors": nested(stats, "floorsAscended") or old.get("floors"),
            "intensity_min": old.get("intensity_min"),
        }
        self.con.execute(
            """INSERT INTO daily_metrics
               (source,date,steps,resting_hr,avg_stress,calories,active_min,sleep_sec,
                deep_sleep_sec,rem_sleep_sec,body_battery_charged,body_battery_drained,
                floors,intensity_min,raw_json,fetched_at)
               VALUES('garmin',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(source,date) DO UPDATE SET
                 steps=excluded.steps, resting_hr=excluded.resting_hr,
                 avg_stress=excluded.avg_stress, calories=excluded.calories,
                 active_min=excluded.active_min, sleep_sec=excluded.sleep_sec,
                 deep_sleep_sec=excluded.deep_sleep_sec, rem_sleep_sec=excluded.rem_sleep_sec,
                 body_battery_charged=excluded.body_battery_charged,
                 body_battery_drained=excluded.body_battery_drained,
                 floors=excluded.floors, intensity_min=excluded.intensity_min,
                 raw_json=excluded.raw_json, fetched_at=excluded.fetched_at""",
            (
                day,
                values["steps"], values["resting_hr"], values["avg_stress"], values["calories"],
                values["active_min"], values["sleep_sec"], values["deep_sleep_sec"],
                values["rem_sleep_sec"], values["body_battery_charged"], values["body_battery_drained"],
                values["floors"], values["intensity_min"],
                json_dumps({"stats": stats, "heart": heart, "stress": stress, "sleep": sleep, "body": body}),
                utc_now(),
            ),
        )

    def normalize_hrv(self, day: str, payload: Any):
        if not isinstance(payload, dict):
            return
        summary = payload.get("hrvSummary") or {}
        if not summary:
            return
        baseline = summary.get("baseline") or {}
        self.con.execute(
            """INSERT INTO hrv_daily
               (source,date,status,weekly_avg,last_night_avg,last_night_5min_high,
                baseline_low_upper,baseline_balanced_low,baseline_balanced_upper,raw_json,fetched_at)
               VALUES('garmin',?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(source,date) DO UPDATE SET
                 status=excluded.status, weekly_avg=excluded.weekly_avg,
                 last_night_avg=excluded.last_night_avg,
                 last_night_5min_high=excluded.last_night_5min_high,
                 baseline_low_upper=excluded.baseline_low_upper,
                 baseline_balanced_low=excluded.baseline_balanced_low,
                 baseline_balanced_upper=excluded.baseline_balanced_upper,
                 raw_json=excluded.raw_json, fetched_at=excluded.fetched_at""",
            (
                day, summary.get("status"), summary.get("weeklyAvg"), summary.get("lastNightAvg"),
                summary.get("lastNight5MinHigh"), baseline.get("lowUpper"), baseline.get("balancedLow"),
                baseline.get("balancedUpper"), json_dumps(payload), utc_now(),
            ),
        )
        for r in payload.get("hrvReadings") or []:
            key = r.get("readingTimeLocal") or r.get("readingTimeGMT")
            if key:
                put_sample(
                    self.con, "garmin", "hrv_ms", key, r.get("hrvValue"),
                    timestamp_gmt=r.get("readingTimeGMT"), timestamp_local=r.get("readingTimeLocal"),
                    unit="ms", raw=r,
                )

    def normalize_sleep(self, day: str, payload: Any):
        if not isinstance(payload, dict):
            return
        dto = payload.get("dailySleepDTO") or {}
        if not dto:
            return
        score = nested(dto, "sleepScores", "overall", "value") or dto.get("sleepScore")
        self.con.execute(
            """INSERT INTO sleep_sessions
               (source,date,sleep_score,sleep_time_sec,deep_sleep_sec,rem_sleep_sec,
                light_sleep_sec,awake_sleep_sec,sleep_start,sleep_end,raw_json,fetched_at)
               VALUES('garmin',?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(source,date) DO UPDATE SET
                 sleep_score=excluded.sleep_score, sleep_time_sec=excluded.sleep_time_sec,
                 deep_sleep_sec=excluded.deep_sleep_sec, rem_sleep_sec=excluded.rem_sleep_sec,
                 light_sleep_sec=excluded.light_sleep_sec, awake_sleep_sec=excluded.awake_sleep_sec,
                 sleep_start=excluded.sleep_start, sleep_end=excluded.sleep_end,
                 raw_json=excluded.raw_json, fetched_at=excluded.fetched_at""",
            (
                day, score, dto.get("sleepTimeSeconds"), dto.get("deepSleepSeconds"), dto.get("remSleepSeconds"),
                dto.get("lightSleepSeconds"), dto.get("awakeSleepSeconds"),
                dto.get("sleepStartTimestampLocal") or dto.get("sleepStartTimestampGMT"),
                dto.get("sleepEndTimestampLocal") or dto.get("sleepEndTimestampGMT"),
                json_dumps(payload), utc_now(),
            ),
        )

    def normalize_series(self, heart: Any, stress: Any, spo2: Any, respiration: Any):
        if isinstance(heart, dict):
            for row in heart.get("heartRateValues") or []:
                if isinstance(row, list) and len(row) >= 2:
                    key = str(row[0])
                    put_sample(self.con, "garmin", "heart_rate_bpm", key, row[1], timestamp_gmt=epoch_iso(row[0]), unit="bpm", raw=row)
        if isinstance(stress, dict):
            for row in stress.get("stressValuesArray") or []:
                if isinstance(row, list) and len(row) >= 2:
                    key = str(row[0])
                    put_sample(self.con, "garmin", "stress_level", key, row[1], timestamp_gmt=epoch_iso(row[0]), raw=row)
            for row in stress.get("bodyBatteryValuesArray") or []:
                if isinstance(row, list) and len(row) >= 3:
                    key = str(row[0])
                    put_sample(self.con, "garmin", "body_battery", key, row[2], timestamp_gmt=epoch_iso(row[0]), raw=row)
        if isinstance(spo2, dict):
            for row in spo2.get("spO2HourlyAverages") or []:
                if isinstance(row, list) and len(row) >= 2:
                    key = str(row[0])
                    put_sample(self.con, "garmin", "spo2_pct_hourly", key, row[1], timestamp_gmt=epoch_iso(row[0]), unit="%", raw=row)
            for row in spo2.get("spO2SingleValues") or []:
                if isinstance(row, list) and len(row) >= 2:
                    key = str(row[0])
                    put_sample(self.con, "garmin", "spo2_pct", key, row[1], timestamp_gmt=epoch_iso(row[0]), unit="%", raw=row)
        if isinstance(respiration, dict):
            for row in respiration.get("respirationValuesArray") or []:
                if isinstance(row, list) and len(row) >= 2:
                    key = str(row[0])
                    put_sample(self.con, "garmin", "respiration_brpm", key, row[1], timestamp_gmt=epoch_iso(row[0]), unit="breaths/min", raw=row)

    def sync_static(self, start: date, end: date):
        static_calls = [
            ("profile", "current", self.client.get_user_profile, ()),
            ("profile_settings", "current", self.client.get_userprofile_settings, ()),
            ("devices", "current", self.client.get_devices, ()),
            ("primary_training_device", "current", self.client.get_primary_training_device, ()),
            ("device_last_used", "current", self.client.get_device_last_used, ()),
            ("device_alarms", "current", self.client.get_device_alarms, ()),
            ("personal_records", "all", self.client.get_personal_record, ()),
            ("cycling_ftp", "current", self.client.get_cycling_ftp, ()),
            ("lactate_threshold_latest", "current", self.client.get_lactate_threshold, ()),
            ("workouts", "all", self.client.get_workouts, (0, 100)),
            ("training_plans", "all", self.client.get_training_plans, ()),
            ("goals_active", "all", self.client.get_goals, ("active", 0, 100)),
            ("goals_completed", "all", self.client.get_goals, ("completed", 0, 100)),
        ]
        for kind, key, fn, args in static_calls:
            self.call(kind, key, fn, *args)

        devices = self.call("devices", "current", self.client.get_devices) or []
        for device in devices if isinstance(devices, list) else []:
            did = device.get("deviceId") or device.get("device_id")
            if did:
                self.call("device_settings", str(did), self.client.get_device_settings, str(did))

        # Range-oriented health/performance endpoints.
        s, e = start.isoformat(), end.isoformat()
        ranges = [
            ("daily_steps_range", f"{s}:{e}", self.client.get_daily_steps, (s, e)),
            ("body_composition_range", f"{s}:{e}", self.client.get_body_composition, (s, e)),
            ("blood_pressure_range", f"{s}:{e}", self.client.get_blood_pressure, (s, e)),
            ("endurance_score_range", f"{s}:{e}", self.client.get_endurance_score, (s, e)),
            ("running_tolerance_range", f"{s}:{e}", self.client.get_running_tolerance, (s, e, "daily")),
            ("race_predictions_range", f"{s}:{e}", self.client.get_race_predictions, (s, e)),
            ("hill_score_range", f"{s}:{e}", self.client.get_hill_score, (s, e)),
            ("weekly_intensity_minutes_range", f"{s}:{e}", self.client.get_weekly_intensity_minutes, (s, e)),
        ]
        for kind, key, fn, args in ranges:
            self.call(kind, key, fn, *args)

        self.call(
            "lactate_threshold_range",
            f"{s}:{e}",
            self.client.get_lactate_threshold,
            latest=False,
            start_date=s,
            end_date=e,
            aggregation="daily",
        )

        # Garmin limits Body Battery ranges; use 30-day chunks.
        cursor = start
        while cursor <= end:
            chunk_end = min(cursor + timedelta(days=29), end)
            cs, ce = cursor.isoformat(), chunk_end.isoformat()
            body = self.call("body_battery_range", f"{cs}:{ce}", self.client.get_body_battery, cs, ce) or []
            for r in body if isinstance(body, list) else []:
                if isinstance(r, dict) and r.get("date"):
                    self.put_daily(r["date"], body=r)
                    for row in r.get("bodyBatteryValuesArray") or []:
                        if isinstance(row, list) and len(row) >= 2:
                            put_sample(self.con, "garmin", "body_battery_sparse", str(row[0]), row[1], timestamp_gmt=epoch_iso(row[0]), raw=row)
            cursor = chunk_end + timedelta(days=1)

        # Menstrual calendar allows at most 92 days; use 90-day chunks.
        cursor = start
        while cursor <= end:
            chunk_end = min(cursor + timedelta(days=89), end)
            cs, ce = cursor.isoformat(), chunk_end.isoformat()
            self.call("menstrual_calendar_range", f"{cs}:{ce}", self.client.get_menstrual_calendar_data, cs, ce)
            cursor = chunk_end + timedelta(days=1)

        # Scheduled workouts are month-oriented.
        cursor = date(start.year, start.month, 1)
        until = date(end.year, end.month, 1) + timedelta(days=120)
        while cursor <= until:
            self.call("scheduled_workouts", f"{cursor.year:04d}-{cursor.month:02d}", self.client.get_scheduled_workouts, cursor.year, cursor.month)
            cursor = (cursor.replace(day=28) + timedelta(days=4)).replace(day=1)
        self.con.commit()

    def sync_daily(self, start: date, end: date):
        total = (end - start).days + 1
        for idx, d in enumerate(daterange(start, end), 1):
            day = d.isoformat()
            stats = self.call("daily_stats", day, self.client.get_stats, day, event_date=day)
            heart = self.call("heart_rate_raw", day, self.client.get_heart_rates, day, event_date=day)
            stress = self.call("stress_raw", day, self.client.get_stress_data, day, event_date=day)
            respiration = self.call("respiration_raw", day, self.client.get_respiration_data, day, event_date=day)
            spo2 = self.call("spo2_raw", day, self.client.get_spo2_data, day, event_date=day)
            readiness = self.call("training_readiness_raw", day, self.client.get_training_readiness, day, event_date=day)
            self.call("morning_training_readiness_raw", day, self.client.get_morning_training_readiness, day, event_date=day)
            self.call("training_status_raw", day, self.client.get_training_status, day, event_date=day)
            self.call("fitness_age_raw", day, self.client.get_fitnessage_data, day, event_date=day)
            self.call("hydration_raw", day, self.client.get_hydration_data, day, event_date=day)
            self.call("all_day_events_raw", day, self.client.get_all_day_events, day, event_date=day)
            self.call("lifestyle_logging_raw", day, self.client.get_lifestyle_logging_data, day, event_date=day)
            self.call("body_battery_events_raw", day, self.client.get_body_battery_events, day, event_date=day)
            self.call("intensity_minutes_raw", day, self.client.get_intensity_minutes_data, day, event_date=day)
            self.call("rhr_raw", day, self.client.get_rhr_day, day, event_date=day)
            self.call("max_metrics_raw", day, self.client.get_max_metrics, day, event_date=day)

            sleep_row = self.con.execute(
                "SELECT raw_json FROM sleep_sessions WHERE source='garmin' AND date=?",
                (day,),
            ).fetchone()
            hrv_row = self.con.execute(
                "SELECT raw_json FROM hrv_daily WHERE source='garmin' AND date=?",
                (day,),
            ).fetchone()

            sleep = json.loads(sleep_row[0]) if sleep_row and sleep_row[0] and not self.refresh else None
            hrv = json.loads(hrv_row[0]) if hrv_row and hrv_row[0] and not self.refresh else None

            if sleep is None:
                sleep = self.call("sleep_raw", day, self.client.get_sleep_data, day, event_date=day)
                self.normalize_sleep(day, sleep)
            if hrv is None:
                hrv = self.call("hrv_raw", day, self.client.get_hrv_data, day, event_date=day)
                self.normalize_hrv(day, hrv)

            self.put_daily(day, stats=stats, heart=heart, stress=stress, sleep=sleep)
            self.normalize_series(heart, stress, spo2, respiration)
            self.con.commit()
            if idx == 1 or idx % 10 == 0 or idx == total:
                print(f"DAILY {idx}/{total} {day} calls={self.calls} samples={self.con.execute('SELECT COUNT(*) FROM time_series_samples').fetchone()[0]}", flush=True)

    def normalize_activity(self, payload: Any):
        if not isinstance(payload, dict):
            return
        aid = payload.get("activityId")
        if not aid:
            return
        summary = payload.get("summaryDTO") or payload
        atype = payload.get("activityTypeDTO") or payload.get("activityType") or {}
        atype_key = atype.get("typeKey") if isinstance(atype, dict) else atype
        tz = payload.get("timeZoneUnitDTO") or {}
        values = {
            "activity_id": str(aid),
            "activity_name": payload.get("activityName"),
            "activity_type": atype_key,
            "start_time": summary.get("startTimeLocal") or payload.get("startTimeLocal"),
            "start_time_gmt": summary.get("startTimeGMT") or payload.get("startTimeGMT"),
            "timezone": tz.get("timeZone") or tz.get("unitKey"),
            "distance_m": summary.get("distance"),
            "duration_s": summary.get("duration"),
            "moving_duration_s": summary.get("movingDuration"),
            "elapsed_duration_s": summary.get("elapsedDuration"),
            "avg_speed_mps": summary.get("averageSpeed"),
            "avg_moving_speed_mps": summary.get("averageMovingSpeed"),
            "max_speed_mps": summary.get("maxSpeed"),
            "avg_hr": summary.get("averageHR"),
            "max_hr": summary.get("maxHR"),
            "min_hr": summary.get("minHR"),
            "avg_cadence": summary.get("averageRunCadence") or summary.get("averageBikingCadence"),
            "max_cadence": summary.get("maxRunCadence") or summary.get("maxBikingCadence"),
            "stride_length_cm": summary.get("avgStrideLength"),
            "ground_contact_time_ms": summary.get("avgGroundContactTime"),
            "vertical_oscillation_cm": summary.get("avgVerticalOscillation"),
            "vertical_ratio": summary.get("avgVerticalRatio"),
            "avg_power_w": summary.get("avgPower"),
            "max_power_w": summary.get("maxPower"),
            "normalized_power_w": summary.get("normPower"),
            "elevation_gain_m": summary.get("elevationGain"),
            "elevation_loss_m": summary.get("elevationLoss"),
            "max_elevation_m": summary.get("maxElevation"),
            "min_elevation_m": summary.get("minElevation"),
            "avg_temperature": summary.get("averageTemperature"),
            "max_temperature": summary.get("maxTemperature"),
            "min_temperature": summary.get("minTemperature"),
            "training_effect": summary.get("aerobicTrainingEffect"),
            "anaerobic_training_effect": summary.get("anaerobicTrainingEffect"),
            "training_effect_label": summary.get("trainingEffectLabel"),
            "activity_training_load": summary.get("activityTrainingLoad"),
            "vo2max": summary.get("vO2MaxValue"),
            "calories": summary.get("calories"),
            "steps": summary.get("steps"),
            "body_battery_diff": summary.get("differenceBodyBattery"),
            "start_lat": summary.get("startLatitude"),
            "start_lon": summary.get("startLongitude"),
            "end_lat": summary.get("endLatitude"),
            "end_lon": summary.get("endLongitude"),
            "raw_json": json_dumps(payload),
            "fetched_at": utc_now(),
        }
        cols = list(values)
        sql_cols = ",".join(cols)
        ph = ",".join("?" for _ in cols)
        updates = ",".join(f"{c}=COALESCE(excluded.{c},activities.{c})" for c in cols if c != "activity_id")
        self.con.execute(
            f"INSERT INTO activities(source,{sql_cols}) VALUES('garmin',{ph}) "
            f"ON CONFLICT(source,activity_id) DO UPDATE SET {updates}",
            [values[c] for c in cols],
        )

    def _sync_activity_summaries(
        self,
        summaries: list[dict[str, Any]],
        *,
        list_kind: str,
        list_key: str,
        progress_label: str,
    ):
        put_raw(self.con, "garmin", list_kind, list_key, summaries)
        self.con.commit()
        print(f"{progress_label}_LIST {len(summaries)}", flush=True)

        detail_calls = [
            ("activity", self.client.get_activity),
            ("activity_details", self.client.get_activity_details),
            ("activity_splits", self.client.get_activity_splits),
            ("activity_typed_splits", self.client.get_activity_typed_splits),
            ("activity_split_summaries", self.client.get_activity_split_summaries),
            ("activity_weather", self.client.get_activity_weather),
            ("activity_hr_timezones", self.client.get_activity_hr_in_timezones),
            ("activity_power_timezones", self.client.get_activity_power_in_timezones),
            ("activity_exercise_sets", self.client.get_activity_exercise_sets),
            ("activity_gear", self.client.get_activity_gear),
        ]
        total = len(summaries)
        for idx, summary in enumerate(summaries, 1):
            aid = summary.get("activityId")
            if not aid:
                continue
            key = str(aid)
            put_raw(self.con, "garmin", "activity_summary", key, summary, event_date=(summary.get("startTimeLocal") or "")[:10] or None)
            self.normalize_activity(summary)

            full = self.call("activity", key, self.client.get_activity, key)
            if full:
                self.normalize_activity(full)
            for kind, fn in detail_calls[1:]:
                self.call(kind, key, fn, key)

            fit_path = FIT_DIR / f"{key}.zip"
            if self.refresh or not fit_path.exists():
                try:
                    data = self.client.download_activity(key, self.client.ActivityDownloadFormat.ORIGINAL)
                    fit_path.write_bytes(data)
                    self.calls += 1
                    if self.delay:
                        time.sleep(self.delay)
                except GarminConnectTooManyRequestsError:
                    print(f"FIT_RATE_LIMIT {key}", flush=True)
                    time.sleep(30)
                except Exception as exc:
                    print(f"FIT_SKIP {key} {type(exc).__name__}: {exc}", flush=True)
            self.con.execute(
                "UPDATE activities SET fit_path=? WHERE source='garmin' AND activity_id=?",
                (str(fit_path.relative_to(USER_ROOT)) if fit_path.exists() else None, key),
            )
            self.con.commit()
            if idx == 1 or idx % 10 == 0 or idx == total:
                print(f"{progress_label} {idx}/{total} id={key} calls={self.calls}", flush=True)

    def sync_activities(self):
        summaries: list[dict[str, Any]] = []
        start = 0
        while True:
            page = self.client.get_activities(start, 100) or []
            if not page:
                break
            summaries.extend(page)
            start += len(page)
            if len(page) < 100:
                break
        self._sync_activity_summaries(
            summaries,
            list_kind="activity_list_all",
            list_key="all",
            progress_label="ACTIVITY",
        )

    def sync_recent_activities(self, limit: int = 20):
        summaries = self.client.get_activities(0, limit) or []
        self._sync_activity_summaries(
            summaries,
            list_kind="activity_list_recent",
            list_key="latest",
            progress_label="RECENT_ACTIVITY",
        )

    def run(self, start: date, end: date, phase: str):
        run_id = self.con.execute(
            "INSERT INTO sync_runs(source,kind,started_at,status,start_date,end_date) VALUES('garmin',? ,?,'running',?,?)",
            (phase, utc_now(), start.isoformat(), end.isoformat()),
        ).lastrowid
        self.con.commit()
        try:
            if phase == "refresh":
                previous_refresh = self.refresh
                try:
                    # A user-initiated pull-to-refresh must re-query recent Garmin
                    # wellness records even if an earlier same-day placeholder was
                    # already archived. Recent activities stay incremental so we do
                    # not redownload old activity details/FIT files on every pull.
                    self.refresh = True
                    self.sync_daily(start, end)
                    self.refresh = False
                    self.sync_recent_activities()
                finally:
                    self.refresh = previous_refresh
            else:
                if phase in {"all", "static"}:
                    self.sync_static(start, end)
                if phase in {"all", "daily"}:
                    self.sync_daily(start, end)
                if phase in {"all", "activities"}:
                    self.sync_activities()
            self.con.execute(
                "UPDATE sync_runs SET finished_at=?,status='completed',records_written=? WHERE id=?",
                (utc_now(), self.writes, run_id),
            )
            self.con.commit()
        except Exception as exc:
            self.con.execute(
                "UPDATE sync_runs SET finished_at=?,status='failed',records_written=?,note=? WHERE id=?",
                (utc_now(), self.writes, f"{type(exc).__name__}: {exc}", run_id),
            )
            self.con.commit()
            raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile")
    parser.add_argument("--user-id")
    parser.add_argument("--start", default=DEFAULT_START.isoformat())
    parser.add_argument("--end", default=date.today().isoformat())
    parser.add_argument("--phase", choices=("all", "static", "daily", "activities", "refresh"), default="all")
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--delay", type=float, default=0.12)
    args = parser.parse_args()
    user_id = resolve_profile(args.profile, args.user_id)
    configure_user(user_id)
    print(f"PROFILE {args.profile or user_id} -> {user_id}", flush=True)
    start = date.fromisoformat(args.start)
    end = date.fromisoformat(args.end)
    sync = Sync(refresh=args.refresh, delay=args.delay)
    try:
        sync.run(start, end, args.phase)
    finally:
        sync.close()


if __name__ == "__main__":
    main()

