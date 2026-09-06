"""JingYou Rhythm training-load and direction method.

This module is intentionally pure.  It consumes already user-scoped activity
rows and normalized health rows, keeps unknown dates unknown, and never changes
the personal-v1 recovery calculation.
"""

from __future__ import annotations

import math
from collections import defaultdict
from datetime import date, timedelta
from typing import Any, Literal, Mapping, Sequence


GOALS = frozenset({"balanced", "endurance", "strength"})
CATEGORIES = ("easy_aerobic", "hard_aerobic", "anaerobic", "strength")
METHODOLOGY_VERSION = "jingyou-rhythm-v1"


def _finite(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _day(value: Any) -> date | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return date.fromisoformat(text[:10])
    except ValueError:
        return None


def _present(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return _finite(value) is not None
    return bool(str(value).strip())


def _observed(row: Mapping[str, Any], keys: Sequence[str]) -> bool:
    return any(_present(row.get(key)) for key in keys)


_SLEEP_KEYS = (
    "sleep_time_sec",
    "sleep_score",
    "deep_sleep_sec",
    "rem_sleep_sec",
    "light_sleep_sec",
    "awake_sleep_sec",
    "sleep_start",
    "sleep_end",
    "raw_json",
)
_DAILY_KEYS = (
    "resting_hr",
    "avg_stress",
    "steps",
    "sleep_sec",
    "calories",
    "active_min",
    "body_battery_charged",
    "body_battery_drained",
    "floors",
    "intensity_min",
)
_HRV_KEYS = ("last_night_avg", "weekly_avg", "status")


def _window(start: date, end: date) -> list[date]:
    return [start + timedelta(days=index) for index in range((end - start).days + 1)]


def _empty_category() -> dict[str, Any]:
    return {
        "au_7": 0.0,
        "au_28": 0.0,
        "minutes_7": 0.0,
        "minutes_28": 0.0,
        "sessions_7": 0,
        "sessions_28": 0,
        "days_7": 0,
        "days_28": 0,
        "last_date": None,
    }


def _empty_period(days: int, required: int) -> dict[str, Any]:
    return {
        "window_days": days,
        "au": 0.0,
        "minutes": 0.0,
        "sessions": 0,
        "activity_days": 0,
        "observed_days": 0,
        "coverage": "insufficient",
        "reference_au": None,
        "relative_ratio": None,
        "trend": "insufficient",
        "required_observed_days": required,
    }


def _quality_ratio(estimated_au: float, reported_au: float, estimated_sessions: int, reported_sessions: int) -> tuple[float, float]:
    total = estimated_au + reported_au
    if total > 0:
        return estimated_au / total, reported_au / total
    count = estimated_sessions + reported_sessions
    if count > 0:
        return estimated_sessions / count, reported_sessions / count
    return 0.0, 0.0


def _category_totals(rows: Sequence[dict[str, Any]], short_start: date, long_start: date, target: date) -> tuple[dict[str, dict[str, Any]], dict[str, float]]:
    categories = {category: _empty_category() for category in CATEGORIES}
    source = {"estimated_au": 0.0, "reported_au": 0.0, "estimated_sessions": 0, "reported_sessions": 0}
    for row in rows:
        day = row["day"]
        category = row.get("category") if row.get("category") in categories else "easy_aerobic"
        load = row.get("load")
        minutes = row.get("minutes")
        if load is None or minutes is None:
            continue
        item = categories[category]
        in_short = short_start <= day <= target
        in_long = long_start <= day <= target
        if in_short:
            item["au_7"] += load
            item["minutes_7"] += minutes
            item["sessions_7"] += 1
        if in_long:
            item["au_28"] += load
            item["minutes_28"] += minutes
            item["sessions_28"] += 1
            item["last_date"] = max(item["last_date"] or "", day.isoformat())
        if in_long:
            source_key = "estimated" if row.get("effort_source") == "estimated" else "reported"
            source[f"{source_key}_au"] += load
            source[f"{source_key}_sessions"] += 1
    for category in CATEGORIES:
        days7 = {row["day"] for row in rows if row.get("category") == category and short_start <= row["day"] <= target and row.get("load") is not None}
        days28 = {row["day"] for row in rows if row.get("category") == category and long_start <= row["day"] <= target and row.get("load") is not None}
        categories[category]["days_7"] = len(days7)
        categories[category]["days_28"] = len(days28)
    return categories, source


def _period(rows: Sequence[dict[str, Any]], start: date, end: date, observed: set[date], required: int, reference_au: float | None, ratio: float | None, trend: str) -> dict[str, Any]:
    selected = [row for row in rows if start <= row["day"] <= end]
    load_rows = [row for row in selected if row.get("load") is not None and row.get("minutes") is not None]
    activity_days = {row["day"] for row in load_rows}
    return {
        "window_days": (end - start).days + 1,
        "au": sum(row["load"] for row in load_rows),
        "minutes": sum(row["minutes"] for row in load_rows),
        "sessions": len(load_rows),
        "activity_days": len(activity_days),
        "observed_days": len(observed),
        "coverage": "complete" if len(observed) >= required else "insufficient",
        "reference_au": reference_au,
        "relative_ratio": ratio,
        "trend": trend,
        "required_observed_days": required,
    }


def _direction(
    *,
    target: date,
    goal: str,
    short: Mapping[str, Any],
    long: Mapping[str, Any],
    categories: Mapping[str, Mapping[str, Any]],
    aerobic_days7: int,
    recent_hard_days: int,
    recent_anaerobic_days: int,
    estimated_ratio: float,
    recovery: Mapping[str, Any] | None,
    checkin_feeling: str | None,
    chronic_trend: str,
) -> dict[str, Any]:
    recovery_score = _finite((recovery or {}).get("score"))
    physiological_signals = sum(
        1 for component in (recovery or {}).get("components", []) or []
        if isinstance(component, Mapping) and component.get("key") in {"hrv", "rhr"}
        and _finite(component.get("score")) is not None
    )
    sleep_value = None
    sleep_target = None
    for component in (recovery or {}).get("components", []) or []:
        if isinstance(component, Mapping) and component.get("key") == "sleep":
            sleep_value = _finite(component.get("value"))
            sleep_target = _finite(component.get("baseline"))
            break
    sleep_low = sleep_value is not None and sleep_target is not None and sleep_target > 0 and sleep_value < sleep_target * 0.85
    coverage_ok = (
        short.get("coverage") == "complete"
        and long.get("coverage") == "complete"
        and short.get("reference_coverage") == "complete"
        and long.get("reference_coverage") == "complete"
    )
    intensity = "balanced"
    reason_key = "steady_rhythm"
    if checkin_feeling == "tired":
        intensity, reason_key = "recover", "checkin_tired"
    elif recovery_score is not None and recovery_score < 60:
        intensity, reason_key = "recover", "recovery_low"
    elif sleep_low:
        intensity, reason_key = "recover", "sleep_below_recovery_target"
    elif short.get("trend") == "rising":
        intensity, reason_key = "consolidate", "recent_load_rising"
    elif recent_hard_days + recent_anaerobic_days >= 2:
        intensity, reason_key = "consolidate", "recent_intensity_dense"
    elif chronic_trend == "rising":
        intensity, reason_key = "consolidate", "chronic_load_rising"
    elif recovery_score is None:
        intensity, reason_key = "conservative", "recovery_missing"
    elif physiological_signals == 0:
        intensity, reason_key = "conservative", "recovery_signals_partial"
    elif not coverage_ok:
        intensity, reason_key = "conservative", "coverage_insufficient"
    elif estimated_ratio >= 0.5:
        intensity, reason_key = "conservative", "record_effort_needed"

    focus_reason = "goal_maintain"
    if checkin_feeling == "tired" or recovery_score is not None and recovery_score < 60 or sleep_low:
        focus = "easy_aerobic"
        focus_reason = "recovery_first"
    elif recovery_score is None or physiological_signals == 0 or not coverage_ok:
        focus = "base"
        focus_reason = "coverage_or_recovery_missing"
    else:
        strength_gap = categories["strength"]["days_7"] < 2 and (
            categories["strength"]["last_date"] is None
            or (target - date.fromisoformat(categories["strength"]["last_date"])).days >= 2
        )
        aerobic_gap = aerobic_days7 < 2
        if goal == "strength":
            focus = "strength" if strength_gap else "maintain"
            focus_reason = "goal_strength_gap" if strength_gap else "recent_strength"
        elif goal == "endurance":
            focus = "easy_aerobic" if aerobic_gap else "maintain"
            focus_reason = "goal_aerobic_gap" if aerobic_gap else "goal_maintain"
        else:
            focus = "strength" if strength_gap else ("easy_aerobic" if aerobic_gap else "maintain")
            focus_reason = "goal_strength_gap" if strength_gap else ("goal_aerobic_gap" if aerobic_gap else "goal_maintain")
    confidence = "estimated" if estimated_ratio >= 0.5 else "recorded"
    reasons = [reason_key]
    if focus_reason != reason_key:
        reasons.append(focus_reason)
    return {
        "intensity": intensity,
        "physiological_signal_count": physiological_signals,
        "focus": focus,
        "confidence": confidence,
        "reason_key": reason_key,
        "focus_reason": focus_reason,
        "reasons": reasons,
    }


def compute_training_status(
    sleep_rows: Sequence[Mapping[str, Any]],
    daily_rows: Sequence[Mapping[str, Any]],
    hrv_rows: Sequence[Mapping[str, Any]],
    enriched_activity_rows: Sequence[Mapping[str, Any]],
    recovery: Mapping[str, Any] | None,
    target_date: str | date,
    goal: str = "balanced",
    checkin: Mapping[str, Any] | None = None,
    activity_coverage_dates: Sequence[str | date] | None = None,
) -> dict[str, Any]:
    """Return short/long recorded load and a conservative next-direction label."""

    target = _day(target_date)
    if target is None:
        raise ValueError("target_date must be an ISO date")
    goal = goal if goal in GOALS else "balanced"
    short_start = target - timedelta(days=6)
    long_start = target - timedelta(days=27)
    ref_start = target - timedelta(days=34)
    ref_end = target - timedelta(days=7)
    chronic_ref_start = target - timedelta(days=55)
    chronic_ref_end = target - timedelta(days=28)

    observed_dates: set[date] = set()
    for rows, keys in ((sleep_rows, _SLEEP_KEYS), (daily_rows, _DAILY_KEYS), (hrv_rows, _HRV_KEYS)):
        for row in rows:
            day = _day(row.get("date"))
            if day is not None and day <= target and _observed(row, keys):
                observed_dates.add(day)

    activities: list[dict[str, Any]] = []
    for row in enriched_activity_rows:
        day = _day(row.get("start_time") or row.get("date"))
        if day is None or day > target:
            continue
        observed_dates.add(day)
        minutes = _finite(row.get("duration_s"))
        minutes = minutes / 60.0 if minutes is not None and minutes >= 0 else None
        load = _finite(row.get("internal_load"))
        load = load if load is not None and load >= 0 else None
        activities.append(
            {
                "day": day,
                "category": row.get("category"),
                "effort_source": row.get("effort_source"),
                "minutes": minutes,
                "load": load,
            }
        )

    actual_activity_dates = {row["day"] for row in activities}
    if activity_coverage_dates is None:
        # No sync metadata means no activity-list date is known to be complete.
        known_activity_dates = set()
    else:
        known_activity_dates = {
            parsed
            for value in activity_coverage_dates
            if (parsed := _day(value)) is not None and parsed <= target
        }
    observed_short = {day for day in known_activity_dates if short_start <= day <= target}
    observed_long = {day for day in known_activity_dates if long_start <= day <= target}
    observed_ref = {day for day in known_activity_dates if ref_start <= day <= ref_end}
    observed_chronic_ref = {day for day in known_activity_dates if chronic_ref_start <= day <= chronic_ref_end}
    ref_rows = [row for row in activities if row["day"] in observed_ref and row.get("load") is not None]
    ref_au = sum(row["load"] for row in ref_rows)
    ref_weekly = (ref_au / len(observed_ref) * 7.0) if observed_ref else None
    ref_complete = len(observed_ref) >= 24
    chronic_ref_complete = len(observed_chronic_ref) >= 24
    chronic_ref_rows = [row for row in activities if row["day"] in observed_chronic_ref and row.get("load") is not None]
    chronic_ref_au = sum(row["load"] for row in chronic_ref_rows)
    chronic_ref_equivalent = (chronic_ref_au / len(observed_chronic_ref) * 28.0) if observed_chronic_ref else None
    short_complete = len(observed_short) >= 7
    long_complete = len(observed_long) >= 24

    short_raw = sum(row["load"] for row in activities if short_start <= row["day"] <= target and row.get("load") is not None)
    long_raw = sum(row["load"] for row in activities if long_start <= row["day"] <= target and row.get("load") is not None)
    short_ratio = short_raw / ref_weekly if short_complete and ref_complete and ref_weekly and ref_weekly > 0 else None
    long_comparable = len(observed_long) == 28
    long_ratio = long_raw / chronic_ref_equivalent if long_comparable and chronic_ref_complete and chronic_ref_equivalent and chronic_ref_equivalent > 0 else None

    def trend(ratio: float | None, ref_total: float | None, enough: bool) -> str:
        if not enough:
            return "insufficient"
        if ref_total is not None and ref_total <= 0:
            return "building"
        if ratio is None:
            return "insufficient"
        if ratio > 1.25:
            return "rising"
        if ratio < 0.75:
            return "lighter"
        return "usual"

    short = _period(activities, short_start, target, observed_short, 7, ref_weekly, short_ratio, trend(short_ratio, ref_weekly, short_complete and ref_complete))
    chronic_trend = trend(long_ratio, chronic_ref_equivalent, long_comparable and chronic_ref_complete)
    long = _period(activities, long_start, target, observed_long, 24, chronic_ref_equivalent, long_ratio, chronic_trend)
    short["reference_coverage"] = "complete" if ref_complete else "insufficient"
    long["reference_coverage"] = "complete" if chronic_ref_complete else "insufficient"
    categories, source = _category_totals(activities, short_start, long_start, target)
    estimated_ratio, reported_ratio = _quality_ratio(source["estimated_au"], source["reported_au"], source["estimated_sessions"], source["reported_sessions"])
    aerobic_days7 = len({row["day"] for row in activities if short_start <= row["day"] <= target and row.get("category") in {"easy_aerobic", "hard_aerobic"} and row.get("load") is not None})
    aerobic_days28 = len({row["day"] for row in activities if long_start <= row["day"] <= target and row.get("category") in {"easy_aerobic", "hard_aerobic"} and row.get("load") is not None})
    recent_days = {category: {row["day"] for row in activities if short_start <= row["day"] <= target and row.get("category") == category and row.get("load") is not None} for category in CATEGORIES}
    last_three_start = target - timedelta(days=2)
    recent_hard_day_set = {row["day"] for row in activities if last_three_start <= row["day"] <= target and row.get("category") == "hard_aerobic" and row.get("load") is not None}
    recent_anaerobic_day_set = {row["day"] for row in activities if last_three_start <= row["day"] <= target and row.get("category") == "anaerobic" and row.get("load") is not None}
    recent_hard_days = len(recent_hard_day_set)
    recent_anaerobic_days = len(recent_anaerobic_day_set)
    recent_high_days = len(recent_hard_day_set | recent_anaerobic_day_set)
    feeling = (checkin or {}).get("feeling") if checkin else None
    checkin_date = _day((checkin or {}).get("date")) if checkin else None
    if checkin_date != target:
        feeling = None
    direction = _direction(
        target=target,
        goal=goal,
        short=short,
        long=long,
        categories=categories,
        aerobic_days7=aerobic_days7,
        recent_hard_days=recent_high_days,
        recent_anaerobic_days=0,
        estimated_ratio=estimated_ratio,
        recovery=recovery,
        checkin_feeling=feeling,
        chronic_trend=chronic_trend,
    )
    category_list = [
        {"key": category, **categories[category]}
        for category in CATEGORIES
    ]
    acute = {
        "total_au": short["au"],
        "active_days": short["activity_days"],
        "coverage_days": short["observed_days"],
        "window_days": 7,
        "minutes": short["minutes"],
        "sessions": short["sessions"],
        "coverage": short["coverage"],
    }
    chronic = {
        "total_au": long["au"],
        "active_days": long["activity_days"],
        "coverage_days": long["observed_days"],
        "window_days": 28,
        "minutes": long["minutes"],
        "sessions": long["sessions"],
        "coverage": long["coverage"],
    }
    effort_confidence = "estimated" if estimated_ratio >= 0.5 else "recorded"
    return {
        "methodology_version": METHODOLOGY_VERSION,
        "date": target.isoformat(),
        "goal": goal,
        "feeling": feeling,
        "load_trend": short["trend"],
        "relative_ratio": short["relative_ratio"],
        "chronic_relative_ratio": long["relative_ratio"],
        "chronic_trend": chronic_trend,
        "confidence": effort_confidence,
        "mode": direction["intensity"],
        "focus": direction["focus"],
        "intensity": direction["intensity"],
        "reasons": direction["reasons"],
        "acute": acute,
        "chronic": chronic,
        "short_term": short,
        "long_term": long,
        "reference": {
            "window_days": 28,
            "from": ref_start.isoformat(),
            "through": ref_end.isoformat(),
            "au": ref_au,
            "total_au": ref_au,
            "observed_days": len(observed_ref),
            "coverage_days": len(observed_ref),
            "coverage": "complete" if ref_complete else "insufficient",
            "weekly_equivalent_au": ref_weekly,
            "scaled_for_coverage": bool(ref_weekly is not None and len(observed_ref) < 28),
        },
        "chronic_reference": {
            "from": chronic_ref_start.isoformat(),
            "through": chronic_ref_end.isoformat(),
            "total_au": chronic_ref_au,
            "coverage_days": len(observed_chronic_ref),
            "equivalent_au": chronic_ref_equivalent,
            "scaled_for_coverage": bool(chronic_ref_equivalent is not None and len(observed_chronic_ref) < 28),
        },
        "categories": category_list,
        "category_totals": categories,
        "strength_days_7": categories["strength"]["days_7"],
        "strength_days_28": categories["strength"]["days_28"],
        "aerobic_days_7": aerobic_days7,
        "aerobic_days_28": aerobic_days28,
        "recent_hard_days_3": recent_hard_days,
        "recent_anaerobic_days_3": recent_anaerobic_days,
        "hard_days3": recent_hard_days,
        "anaerobic_days3": recent_anaerobic_days,
        "hard_days_3": recent_hard_days,
        "anaerobic_days_3": recent_anaerobic_days,
        "estimated_load_ratio": estimated_ratio,
        "reported_load_ratio": reported_ratio,
        "estimated_ratio": estimated_ratio,
        "reported_ratio": reported_ratio,
        "effort_confidence": effort_confidence,
        "checkin": {"date": target.isoformat(), "feeling": feeling},
        "direction": direction,
        "metadata": {
            "au_definition": "duration_minutes * effective_rpe",
            "short_window": "[D-6,D]",
            "long_window": "[D-27,D]",
            "reference_window": "[D-34,D-7]",
            "chronic_reference_window": "[D-55,D-28]",
            "coverage_rule": "completed activity sync/refresh metadata plus actual activity dates; wellness dates alone are not activity coverage and unknown dates are not zero",
            "default_rpe": {"easy_aerobic": 3, "hard_aerobic": 6, "anaerobic": 8, "strength": 6},
            "thresholds": {"rising": 1.25, "lighter": 0.75, "min_short_observed": 7, "min_reference_observed": 24},
            "interpretation": "engineering rhythm labels; not a diagnosis of overtraining, undertraining, or injury risk",
        },
    }
