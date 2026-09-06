"""Pure derived metrics shared by the HTTP query layer and Coach context.

The functions in this module deliberately accept plain mappings and return
plain JSON-compatible values.  Database access, user scoping, and HTTP
validation stay in :mod:`health_queries` and :mod:`app` so the formulae can be
tested without a running server.
"""

from __future__ import annotations

import json
import math
import re
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone
from statistics import median
from typing import Any, Mapping, Sequence


VALID_CATEGORIES = frozenset({"easy_aerobic", "hard_aerobic", "anaerobic", "strength"})
DEFAULT_RPE = {
    "easy_aerobic": 3.0,
    "hard_aerobic": 6.0,
    "anaerobic": 8.0,
    "strength": 6.0,
}
RECOVERY_WEIGHTS = {
    "sleep": 0.4,
    "hrv": 0.3,
    "rhr": 0.2,
    "load": 0.1,
}
FORMULA_VERSION = "personal-v1"


def _finite(value: Any) -> float | None:
    """Return a finite float, preserving zero as a real observation."""

    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _coerce_date(value: Any) -> date | None:
    if isinstance(value, date) and not isinstance(value, datetime):
        return value
    if isinstance(value, datetime):
        return value.date()
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return date.fromisoformat(text[:10])
    except ValueError:
        return None


def _date_text(value: Any) -> str | None:
    parsed = _coerce_date(value)
    return parsed.isoformat() if parsed else None


def _payload_object(payload: Any) -> Mapping[str, Any] | None:
    if isinstance(payload, str):
        try:
            payload = json.loads(payload)
        except (TypeError, ValueError, json.JSONDecodeError):
            return None
    if not isinstance(payload, Mapping):
        return None
    dto = payload.get("dailySleepDTO")
    return dto if isinstance(dto, Mapping) else payload


def _epoch_millis(value: Any) -> float | None:
    number = _finite(value)
    if number is None:
        return None
    # Garmin's Local and GMT sleep timestamps are epoch milliseconds.  Keep
    # the raw numeric difference for offset comparison and decode the Local
    # value as UTC so its wall-clock fields remain timezone-naive.
    try:
        datetime.fromtimestamp(number / 1000.0, tz=timezone.utc)
    except (OverflowError, OSError, ValueError):
        return None
    return number


def _naive_wallclock(epoch_ms: float) -> str:
    return datetime.fromtimestamp(epoch_ms / 1000.0, tz=timezone.utc).replace(tzinfo=None).isoformat()


def sleep_clock(payload: Any) -> dict[str, Any]:
    """Parse only Garmin's explicit Local sleep timestamps.

    Garmin encodes ``*TimestampLocal`` as an epoch-millisecond number whose
    UTC-decoded representation is the local wall clock.  GMT timestamps are
    used only to detect an offset change; they never fill a missing Local
    endpoint.
    """

    missing = {
        "sleep_start_local": None,
        "sleep_end_local": None,
        "clock_source": None,
        "clock_offset_changed": None,
    }
    dto = _payload_object(payload)
    if dto is None:
        return missing

    start_local = _epoch_millis(dto.get("sleepStartTimestampLocal"))
    end_local = _epoch_millis(dto.get("sleepEndTimestampLocal"))
    if start_local is None or end_local is None:
        return missing
    span_ms = end_local - start_local
    if span_ms <= 0 or span_ms > 36 * 60 * 60 * 1000:
        return missing

    start_gmt = _epoch_millis(dto.get("sleepStartTimestampGMT"))
    end_gmt = _epoch_millis(dto.get("sleepEndTimestampGMT"))
    offset_changed: bool | None = None
    if start_gmt is not None and end_gmt is not None:
        start_offset = start_local - start_gmt
        end_offset = end_local - end_gmt
        # A sub-second tolerance handles JSON number/string round-tripping
        # without hiding a real DST or travel offset change.
        offset_changed = abs(start_offset - end_offset) > 1000.0

    return {
        "sleep_start_local": _naive_wallclock(start_local),
        "sleep_end_local": _naive_wallclock(end_local),
        "clock_source": "local",
        "clock_offset_changed": offset_changed,
    }


def _normalised_label(value: Any) -> str:
    text = str(value or "").strip().upper()
    return re.sub(r"[^A-Z0-9]+", "_", text).strip("_")


def _activity_type_is_strength(value: Any) -> bool:
    key = _normalised_label(value)
    return (
        key in {"STRENGTH", "STRENGTH_TRAINING", "WEIGHT_TRAINING", "WEIGHTTRAINING", "WEIGHTLIFTING", "WEIGHT_LIFTING"}
        or "STRENGTH" in key
        or "WEIGHT_TRAINING" in key
    )


def _te(value: Any) -> float:
    number = _finite(value)
    return number if number is not None else 0.0


def classify_activity(row: Mapping[str, Any], category_override: str | None = None) -> str:
    """Resolve the category in the documented priority order."""

    if category_override in VALID_CATEGORIES:
        return str(category_override)
    if _activity_type_is_strength(row.get("activity_type")):
        return "strength"

    label = _normalised_label(row.get("training_effect_label"))
    if "ANAEROBIC" in label or "SPEED" in label:
        return "anaerobic"
    if any(token in label for token in ("HIGH_AEROBIC", "VO2", "THRESHOLD", "TEMPO")):
        return "hard_aerobic"
    if "BASE" in label or "RECOVERY" in label:
        return "easy_aerobic"

    aerobic = _te(row.get("training_effect"))
    anaerobic = _te(row.get("anaerobic_training_effect"))
    if anaerobic >= 3.0 and anaerobic >= aerobic:
        return "anaerobic"
    if aerobic >= 3.0:
        return "hard_aerobic"
    return "easy_aerobic"


def enrich_activity(row: Mapping[str, Any], effort: Mapping[str, Any] | None = None) -> dict[str, Any]:
    """Add user-scoped effort/category fields to one activity row.

    ``effort_rpe`` is the effective RPE used for the AU calculation.  When no
    self-report exists it is the category default and ``effort_source`` is
    ``estimated``; consumers must use that source field before describing it
    as a user-reported value.
    """

    result = dict(row)
    effort_row = effort or row
    override = effort_row.get("category_override")
    if override not in VALID_CATEGORIES:
        override = None
    category = classify_activity(row, override)

    reported_rpe = _finite(effort_row.get("effort_rpe"))
    if reported_rpe is not None and 0.0 <= reported_rpe <= 10.0:
        rpe = reported_rpe
        source = "reported"
    else:
        rpe = DEFAULT_RPE[category]
        source = "estimated"

    duration_s = _finite(row.get("duration_s"))
    internal_load = None
    if duration_s is not None and duration_s >= 0:
        candidate_load = (duration_s / 60.0) * rpe
        internal_load = candidate_load if math.isfinite(candidate_load) else None

    result.update(
        {
            "category": category,
            "category_override": override,
            "effort_rpe": rpe,
            "effort_source": source,
            "internal_load": internal_load,
        }
    )
    return result


def _percentile(values: Sequence[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered:
        raise ValueError("percentile requires at least one value")
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def _mad(values: Sequence[float], centre: float) -> float:
    return median([abs(value - centre) for value in values]) if values else 0.0


def _clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def _row_by_date(rows: Sequence[Mapping[str, Any]]) -> dict[date, Mapping[str, Any]]:
    result: dict[date, Mapping[str, Any]] = {}
    for row in rows:
        parsed = _coerce_date(row.get("date"))
        if parsed is not None:
            result[parsed] = row
    return result


def _recovery_component(
    key: str,
    score: float | None,
    value: float | None,
    baseline: float | None,
    **extra: Any,
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "key": key,
        "score": score,
        "weight": RECOVERY_WEIGHTS[key],
        "value": value,
        "baseline": baseline,
    }
    result.update(extra)
    return result


def _empty_recovery(anchor: date | None, coverage: int = 0) -> dict[str, Any]:
    components = [
        _recovery_component(key, None, None, None)
        for key in ("sleep", "hrv", "rhr", "load")
    ]
    return {
        "source": "jingyou",
        "formula_version": FORMULA_VERSION,
        "date": anchor.isoformat() if anchor else None,
        "score": None,
        "level": None,
        "components": components,
        "coverage": coverage,
    }


def _activity_rows_with_load(rows: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for row in rows:
        if row.get("effort_source") in {"reported", "estimated"} and "internal_load" in row:
            result.append(dict(row))
        else:
            result.append(enrich_activity(row))
    return result


def _value_is_observed(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, bool):
        return True
    if isinstance(value, (int, float)):
        return _finite(value) is not None
    return bool(str(value).strip())


def _row_is_observed(row: Mapping[str, Any], keys: Sequence[str]) -> bool:
    return any(_value_is_observed(row.get(key)) for key in keys)


_SLEEP_OBSERVATION_KEYS = (
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
_DAILY_OBSERVATION_KEYS = (
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


def compute_recovery(
    *,
    sleep_rows: Sequence[Mapping[str, Any]],
    hrv_rows: Sequence[Mapping[str, Any]],
    daily_rows: Sequence[Mapping[str, Any]],
    activity_rows: Sequence[Mapping[str, Any]],
    anchor_date: str | date | None = None,
) -> dict[str, Any]:
    """Compute the JingYou personal recovery reference index for one date.

    All windows are calendar-date windows.  A supplied anchor is authoritative
    and every row after it is ignored, which prevents future imports from
    changing a historical result.
    """

    sleep_by_date = _row_by_date(sleep_rows)
    hrv_by_date = _row_by_date(hrv_rows)
    daily_by_date = _row_by_date(daily_rows)
    parsed_anchor = _coerce_date(anchor_date)
    if parsed_anchor is None:
        meaningful_sleep_dates = [
            day
            for day, row in sleep_by_date.items()
            if day <= date.today()
            if _finite(row.get("sleep_time_sec")) is not None
            or _finite(row.get("sleep_score")) is not None
        ]
        parsed_anchor = max(meaningful_sleep_dates, default=None)
    if parsed_anchor is None:
        return _empty_recovery(None)

    anchor = parsed_anchor
    start42 = anchor - timedelta(days=42)
    start7 = anchor - timedelta(days=6)
    start3 = anchor - timedelta(days=2)
    start28 = anchor - timedelta(days=28)

    def in_window(day: date, start: date, end: date = anchor) -> bool:
        return start <= day <= end

    sleep_values: dict[date, float] = {}
    for day, row in sleep_by_date.items():
        seconds = _finite(row.get("sleep_time_sec"))
        if day <= anchor and seconds is not None and seconds >= 0:
            sleep_values[day] = seconds / 3600.0
    sleep_baseline = [value for day, value in sleep_values.items() if start42 <= day < anchor]
    sleep_score: float | None = None
    sleep_value: float | None = sleep_values.get(anchor)
    sleep_target: float | None = None
    sleep_last3: float | None = None
    sleep_samples = 0
    # The rolling mean is over the three calendar dates ending at the anchor,
    # not the three most recent rows.  A missing night must stay missing
    # rather than pulling an older night into the window.
    last3 = [
        value
        for day, value in sorted(sleep_values.items(), reverse=True)
        if start3 <= day <= anchor
    ]
    if last3:
        sleep_last3 = sum(last3) / len(last3)
        sleep_samples = len(last3)
    if len(sleep_baseline) >= 7 and sleep_value is not None and sleep_last3 is not None:
        sleep_target = _clamp(_percentile(sleep_baseline, 0.75), 7.0, 9.0)
        sleep_score = 100.0 * (
            0.6 * min(sleep_value / sleep_target, 1.0) ** 2
            + 0.4 * min(sleep_last3 / sleep_target, 1.0) ** 2
        )
    components: dict[str, dict[str, Any]] = {
        "sleep": _recovery_component(
            "sleep", sleep_score, sleep_value, sleep_target,
            last3_mean_hours=sleep_last3,
            sample_count=sleep_samples,
            baseline_sample_count=len(sleep_baseline),
        )
    }

    hrv_values: dict[date, float] = {}
    hrv_raw: dict[date, float] = {}
    for day, row in hrv_by_date.items():
        value = _finite(row.get("last_night_avg"))
        if day <= anchor and value is not None and value > 0:
            hrv_raw[day] = value
            hrv_values[day] = math.log(value)
    hrv_baseline = [value for day, value in hrv_values.items() if start42 <= day < anchor]
    hrv_recent_days = [day for day in hrv_values if start7 <= day <= anchor]
    hrv_recent = [hrv_values[day] for day in hrv_recent_days]
    hrv_score: float | None = None
    hrv_value: float | None = None
    hrv_baseline_value: float | None = None
    if len(hrv_baseline) >= 7 and len(hrv_recent) >= 3:
        hrv_recent_mean = sum(hrv_recent) / len(hrv_recent)
        baseline_median = median(hrv_baseline)
        scale = max(1.4826 * _mad(hrv_baseline, baseline_median), 0.06)
        z = (hrv_recent_mean - baseline_median) / scale
        hrv_score = _clamp(80.0 + 10.0 * min(z, 1.0) - 22.0 * max(-z, 0.0), 0.0, 95.0)
        hrv_value = sum(hrv_raw[day] for day in hrv_recent_days) / len(hrv_recent_days)
        hrv_baseline_value = median([hrv_raw[day] for day in hrv_values if start42 <= day < anchor])
        components["hrv"] = _recovery_component(
            "hrv", hrv_score, hrv_value, hrv_baseline_value,
            z=z, scale=scale, sample_count=len(hrv_recent), baseline_sample_count=len(hrv_baseline),
        )
    else:
        components["hrv"] = _recovery_component(
            "hrv", None, None, None,
            sample_count=len(hrv_recent), baseline_sample_count=len(hrv_baseline),
        )

    rhr_values: dict[date, float] = {}
    for day, row in daily_by_date.items():
        value = _finite(row.get("resting_hr"))
        if day <= anchor and value is not None and value > 0:
            rhr_values[day] = value
    rhr_baseline = [value for day, value in rhr_values.items() if start42 <= day < anchor]
    rhr_recent = [value for day, value in rhr_values.items() if start3 <= day <= anchor]
    rhr_score: float | None = None
    rhr_value: float | None = None
    rhr_baseline_value: float | None = None
    if len(rhr_baseline) >= 7 and rhr_recent:
        rhr_value = sum(rhr_recent) / len(rhr_recent)
        rhr_baseline_value = median(rhr_baseline)
        scale = max(1.4826 * _mad(rhr_baseline, rhr_baseline_value), 1.5)
        z = (rhr_value - rhr_baseline_value) / scale
        rhr_score = _clamp(80.0 - 18.0 * max(z, 0.0) + 6.0 * min(max(-z, 0.0), 1.0), 0.0, 95.0)
        components["rhr"] = _recovery_component(
            "rhr", rhr_score, rhr_value, rhr_baseline_value,
            z=z, scale=scale, sample_count=len(rhr_recent), baseline_sample_count=len(rhr_baseline),
        )
    else:
        components["rhr"] = _recovery_component(
            "rhr", None, None, None,
            sample_count=len(rhr_recent), baseline_sample_count=len(rhr_baseline),
        )

    load_rows = _activity_rows_with_load(activity_rows)
    load_by_date: dict[date, float] = defaultdict(float)
    load_source_amounts = {"reported": 0.0, "estimated": 0.0}
    load_source_counts = {"reported": 0, "estimated": 0}
    covered_prior_dates: set[date] = {
        day
        for day, row in sleep_by_date.items()
        if start28 <= day < anchor and _row_is_observed(row, _SLEEP_OBSERVATION_KEYS)
    }
    covered_prior_dates.update(
        day
        for day, row in daily_by_date.items()
        if start28 <= day < anchor and _row_is_observed(row, _DAILY_OBSERVATION_KEYS)
    )
    for row in load_rows:
        day = _coerce_date(row.get("start_time") or row.get("date"))
        load = _finite(row.get("internal_load"))
        if day is None or day > anchor or load is None or load < 0:
            continue
        load_by_date[day] += load
        if day >= start28:
            source = row.get("effort_source") if row.get("effort_source") in load_source_amounts else "estimated"
            load_source_amounts[source] += load
            load_source_counts[source] += 1
        if start28 <= day < anchor:
            covered_prior_dates.add(day)
    prior28_total = sum(load_by_date.get(start28 + timedelta(days=index), 0.0) for index in range(28))
    # Only actual activity rows contribute to the recent-3-day total.  Missing
    # activity dates are unknown, not synthetic zero-load workouts.
    recent3_total = sum(load for day, load in load_by_date.items() if start3 <= day <= anchor)
    load_score: float | None = None
    load_baseline_value: float | None = None
    load_ratio: float | None = None
    if len(covered_prior_dates) >= 14:
        load_baseline_value = (prior28_total / len(covered_prior_dates)) * 3.0
        if load_baseline_value > 0:
            load_ratio = recent3_total / load_baseline_value
        else:
            # Keep the JSON response finite when the covered baseline is all
            # zero.  Any positive recent load is treated as an extreme ratio
            # and receives the lower clamp.
            load_ratio = 1_000_000.0 if recent3_total > 0 else 0.0
        load_score = _clamp(85.0 - 18.0 * max(load_ratio - 1.0, 0.0), 20.0, 95.0)
        total_source_load = sum(load_source_amounts.values())
        if total_source_load > 0:
            estimated_ratio = load_source_amounts["estimated"] / total_source_load
            reported_ratio = load_source_amounts["reported"] / total_source_load
        else:
            total_source_count = sum(load_source_counts.values())
            estimated_ratio = load_source_counts["estimated"] / total_source_count if total_source_count else 0.0
            reported_ratio = load_source_counts["reported"] / total_source_count if total_source_count else 0.0
        source = "mixed" if estimated_ratio > 0 and reported_ratio > 0 else ("estimated" if estimated_ratio > 0 else "reported")
        components["load"] = _recovery_component(
            "load", load_score, recent3_total, load_baseline_value,
            ratio=load_ratio, source=source, estimated_ratio=estimated_ratio,
            reported_ratio=reported_ratio, covered_days=len(covered_prior_dates),
        )
    else:
        components["load"] = _recovery_component(
            "load", None, None, None, covered_days=len(covered_prior_dates),
        )

    ordered_components = [components[key] for key in ("sleep", "hrv", "rhr", "load")]
    available = [component for component in ordered_components if component["score"] is not None]
    coverage = len(available)
    score: float | None = None
    if components["sleep"]["score"] is not None and coverage >= 2:
        denominator = sum(float(component["weight"]) for component in available)
        if denominator > 0:
            score = sum(float(component["score"]) * float(component["weight"]) for component in available) / denominator
    level = None
    if score is not None:
        level = "high" if score >= 80 else ("moderate" if score >= 60 else "low")

    return {
        "source": "jingyou",
        "formula_version": FORMULA_VERSION,
        "date": anchor.isoformat(),
        "score": score,
        "level": level,
        "components": ordered_components,
        "coverage": coverage,
    }


def recovery_history(
    *,
    dates: Sequence[str | date],
    sleep_rows: Sequence[Mapping[str, Any]],
    hrv_rows: Sequence[Mapping[str, Any]],
    daily_rows: Sequence[Mapping[str, Any]],
    activity_rows: Sequence[Mapping[str, Any]],
) -> list[dict[str, Any]]:
    """Compute history from one in-memory snapshot, without per-date SQL."""

    result: list[dict[str, Any]] = []
    for value in dates:
        recovery = compute_recovery(
            sleep_rows=sleep_rows,
            hrv_rows=hrv_rows,
            daily_rows=daily_rows,
            activity_rows=activity_rows,
            anchor_date=value,
        )
        result.append({"date": recovery["date"], "score": recovery["score"], "value": recovery["score"]})
    return result
