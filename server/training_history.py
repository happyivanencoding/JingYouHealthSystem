"""Calendar training-load history using the JingYou Rhythm AU definition."""

from __future__ import annotations

from collections import defaultdict
from datetime import date, timedelta
from typing import Any, Mapping, Sequence

from training_method import CATEGORIES, METHODOLOGY_VERSION, _day, _finite


def _calendar(start: date, end: date) -> list[date]:
    return [start + timedelta(days=index) for index in range((end - start).days + 1)]


def _empty_metric() -> dict[str, float | None]:
    return {
        "load_7": None,
        "load_28": None,
        "reference_weekly": None,
        "reference_28": None,
        "recorded_7": 0.0,
        "recorded_28": 0.0,
    }


def _metric(
    prefix: list[float],
    covered_prefix: list[float],
    index: dict[date, int],
    day: date,
    coverage_prefix: list[int],
) -> dict[str, float | None]:
    def total(metric_prefix: list[float], first: date, last: date) -> float:
        if first > last:
            return 0.0
        end_index = index.get(last)
        before_index = index.get(first - timedelta(days=1))
        if end_index is None:
            return 0.0
        return metric_prefix[end_index + 1] - (metric_prefix[before_index + 1] if before_index is not None else 0.0)

    def covered(first: date, last: date) -> int:
        end_index = index.get(last)
        before_index = index.get(first - timedelta(days=1))
        if end_index is None:
            return 0
        return coverage_prefix[end_index + 1] - (coverage_prefix[before_index + 1] if before_index is not None else 0)

    current7 = total(prefix, day - timedelta(days=6), day)
    current28 = total(prefix, day - timedelta(days=27), day)
    reference = total(covered_prefix, day - timedelta(days=34), day - timedelta(days=7))
    reference28 = total(covered_prefix, day - timedelta(days=55), day - timedelta(days=28))
    covered7 = covered(day - timedelta(days=6), day)
    covered28 = covered(day - timedelta(days=27), day)
    covered_ref = covered(day - timedelta(days=34), day - timedelta(days=7))
    covered_ref28 = covered(day - timedelta(days=55), day - timedelta(days=28))
    return {
        "load_7": current7 if covered7 == 7 else None,
        "load_28": current28 if covered28 == 28 else None,
        "reference_weekly": reference / covered_ref * 7.0 if covered_ref >= 24 else None,
        "reference_28": reference28 / covered_ref28 * 28.0 if covered_ref28 >= 24 else None,
        "recorded_7": current7,
        "recorded_28": current28,
    }


def compute_training_load_history(
    enriched_activity_rows: Sequence[Mapping[str, Any]],
    activity_coverage_dates: Sequence[str | date] | None,
    *,
    end_date: str | date | None = None,
    days: int = 180,
) -> dict[str, Any]:
    """Compute rolling load points with prefix sums and explicit coverage."""

    days = max(28, min(int(days), 730))
    end = _day(end_date) if end_date is not None else None
    normalized: list[dict[str, Any]] = []
    actual_dates: set[date] = set()
    for row in enriched_activity_rows:
        day = _day(row.get("start_time") or row.get("date"))
        if day is None or (end is not None and day > end):
            continue
        actual_dates.add(day)
        load = _finite(row.get("internal_load"))
        load = load if load is not None and load >= 0 else None
        duration = _finite(row.get("duration_s"))
        minutes = duration / 60.0 if duration is not None and duration >= 0 else None
        normalized.append({"day": day, "category": row.get("category"), "load": load, "minutes": minutes})
    known_coverage = {
        parsed
        for value in (activity_coverage_dates or [])
        if (parsed := _day(value)) is not None and (end is None or parsed <= end)
    }
    if end is None:
        end = max(known_coverage | actual_dates, default=None)
    if end is None or not (known_coverage or actual_dates):
        return {"methodology_version": METHODOLOGY_VERSION, "points": []}
    first_output = min(known_coverage | actual_dates)
    output_start = max(end - timedelta(days=days - 1), first_output)
    output_dates = sorted(day for day in (known_coverage | actual_dates) if output_start <= day <= end)
    if not output_dates:
        return {"methodology_version": METHODOLOGY_VERSION, "points": []}

    input_start = output_dates[0] - timedelta(days=55)
    prefix_dates = _calendar(input_start, end)
    index = {day: position for position, day in enumerate(prefix_dates)}
    all_daily = defaultdict(float)
    covered_all_daily = defaultdict(float)
    category_daily = {category: defaultdict(float) for category in CATEGORIES}
    covered_category_daily = {category: defaultdict(float) for category in CATEGORIES}
    for row in normalized:
        if row["day"] < input_start or row["day"] > end or row["load"] is None:
            continue
        all_daily[row["day"]] += row["load"]
        category = row["category"] if row["category"] in category_daily else "easy_aerobic"
        category_daily[category][row["day"]] += row["load"]
        if row["day"] in known_coverage:
            covered_all_daily[row["day"]] += row["load"]
            covered_category_daily[category][row["day"]] += row["load"]

    def prefix(values: Mapping[date, float]) -> list[float]:
        result = [0.0]
        for day in prefix_dates:
            result.append(result[-1] + values.get(day, 0.0))
        return result

    all_prefix = prefix(all_daily)
    covered_all_prefix = prefix(covered_all_daily)
    category_prefix = {category: prefix(values) for category, values in category_daily.items()}
    covered_category_prefix = {category: prefix(values) for category, values in covered_category_daily.items()}
    coverage_prefix = [0.0]
    for day in prefix_dates:
        coverage_prefix.append(coverage_prefix[-1] + (1 if day in known_coverage else 0))
    points: list[dict[str, Any]] = []
    for day in output_dates:
        def covered(first: date, last: date) -> int:
            end_index = index.get(last)
            before_index = index.get(first - timedelta(days=1))
            if end_index is None:
                return 0
            return int(coverage_prefix[end_index + 1] - (coverage_prefix[before_index + 1] if before_index is not None else 0))

        coverage7 = covered(day - timedelta(days=6), day)
        coverage28 = covered(day - timedelta(days=27), day)
        all_metric = _metric(all_prefix, covered_all_prefix, index, day, coverage_prefix)
        categories = {
            category: _metric(category_prefix[category], covered_category_prefix[category], index, day, coverage_prefix)
            for category in CATEGORIES
        }
        points.append(
            {
                "date": day.isoformat(),
                "coverage_7": coverage7,
                "coverage_28": coverage28,
                "all": all_metric,
                "categories": categories,
            }
        )
    return {"methodology_version": METHODOLOGY_VERSION, "points": points}
