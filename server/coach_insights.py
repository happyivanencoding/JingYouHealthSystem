"""Validation and freshness labels for Android-provided sleep insights.

The Android client owns the local analysis snapshot.  The backend validates its
small, closed schema before storing it as message metadata and adds only
server-observed freshness/quality labels when preparing Coach context.
"""

from __future__ import annotations

import json
import math
import re
from datetime import date, datetime
from typing import Any, Literal, Mapping

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


MAX_SLEEP_ANALYSIS_BYTES = 64 * 1024
SLEEP_ANALYSIS_SOURCE = "android_personal_sleep_v1"

SleepOutcome = Literal["DURATION_HOURS", "DEEP_HOURS", "REM_HOURS", "DEEP_PERCENT", "REM_PERCENT"]
SleepStatus = Literal[
    "READY",
    "INSUFFICIENT_DATA",
    "INVALID_INPUT",
    "CONSTANT_FACTOR",
    "CONSTANT_OUTCOME",
    "NUMERICAL_FAILURE",
]
SleepFactor = Literal[
    "STRESS",
    "STEPS",
    "HRV",
    "RHR",
    "BEDTIME_DELAY",
    "HABITUAL_WAKE",
    "PRIOR_DAY_STRESS",
    "BATTERY_DRAINED",
    "BATTERY_CHARGED",
    "RECENT_SLEEP_3",
    "TRAINING_MINUTES",
    "TRAINING_LOAD",
    "TRAINING_AVG_HR",
]
SleepFeaturePack = Literal["BASIC", "ENRICHED"]

_TOKEN_RE = re.compile(r"^[A-Za-z0-9_.]+$")


class _StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class SleepFeatureImportance(_StrictModel):
    feature: str = Field(min_length=1, max_length=80, pattern=r"^[A-Za-z0-9_.]+$")
    mae_increase: float
    repeat_sd: float

    @field_validator("mae_increase", "repeat_sd", mode="after")
    @classmethod
    def finite_values(cls, value: float) -> float:
        if not math.isfinite(value):
            raise ValueError("importance values must be finite")
        if value < 0 and cls.model_fields.get("repeat_sd") is not None:
            # The field-specific validator below rejects negative repeat_sd;
            # mae_increase is intentionally allowed to be signed.
            pass
        return float(value)

    @field_validator("repeat_sd", mode="after")
    @classmethod
    def nonnegative_sd(cls, value: float) -> float:
        if value < 0:
            raise ValueError("repeat_sd must be nonnegative")
        return value


class SleepModelInsight(_StrictModel):
    outcome: SleepOutcome
    status: SleepStatus
    algorithm: Literal["RANDOM_FOREST"]
    factor_a: SleepFactor | None = None
    factor_b: SleepFactor | None = None
    feature_pack: SleepFeaturePack | None = None
    lag_days: int | None = Field(default=None, ge=0, le=3)
    train_n: int = Field(ge=0)
    validation_n: int = Field(ge=0)
    validation_start: date | None = None
    validation_end: date | None = None
    selection_mae: float | None = None
    selection_reference_mae: float | None = None
    mae: float | None = None
    reference_mae: float | None = None
    feature_importance: list[SleepFeatureImportance] = Field(default_factory=list, max_length=20)
    dropped_features: list[str] = Field(default_factory=list, max_length=30)

    @field_validator("selection_mae", "selection_reference_mae", "mae", "reference_mae", mode="after")
    @classmethod
    def finite_mae(cls, value: float | None) -> float | None:
        if value is None:
            return None
        if not math.isfinite(value) or value < 0:
            raise ValueError("MAE values must be finite and nonnegative")
        return float(value)

    @field_validator("dropped_features")
    @classmethod
    def token_features(cls, values: list[str]) -> list[str]:
        for value in values:
            if not isinstance(value, str) or not 1 <= len(value) <= 80 or _TOKEN_RE.fullmatch(value) is None:
                raise ValueError("dropped_features must contain token strings")
        return values


class SleepTimingInsight(_StrictModel):
    night_count: int = Field(ge=0)
    usual_bedtime_hour: float | None = None
    usual_wake_hour: float | None = None
    late_count: int = Field(ge=0)
    other_count: int = Field(ge=0)
    bedtime_shift_hours: float | None = None
    wake_shift_hours: float | None = None
    late_sleep_hours: float | None = None
    other_sleep_hours: float | None = None
    late_deep_hours: float | None = None
    other_deep_hours: float | None = None
    late_rem_hours: float | None = None
    other_rem_hours: float | None = None

    @field_validator(
        "usual_bedtime_hour",
        "usual_wake_hour",
        "bedtime_shift_hours",
        "wake_shift_hours",
        "late_sleep_hours",
        "other_sleep_hours",
        "late_deep_hours",
        "other_deep_hours",
        "late_rem_hours",
        "other_rem_hours",
        mode="after",
    )
    @classmethod
    def finite_timing(cls, value: float | None) -> float | None:
        if value is not None and not math.isfinite(value):
            raise ValueError("timing values must be finite")
        return float(value) if value is not None else None


class SleepAnalysis(_StrictModel):
    schema_version: Literal[1]
    source: Literal["android_personal_sleep_v1"]
    through_date: date
    generated_at: datetime
    french_holidays: bool
    models: list[SleepModelInsight] = Field(max_length=5)
    timing: SleepTimingInsight | None = None

    @model_validator(mode="after")
    def bounded_serialized_size(self) -> "SleepAnalysis":
        if len(self.model_dump_json().encode("utf-8")) > MAX_SLEEP_ANALYSIS_BYTES:
            raise ValueError("sleep_analysis is too large")
        return self


def parse_sleep_analysis(value: Any) -> SleepAnalysis:
    """Validate one client payload and raise Pydantic's validation error."""

    return SleepAnalysis.model_validate(value)


def sleep_analysis_from_metadata(metadata_json: str | None) -> SleepAnalysis | None:
    """Read only a valid sleep_analysis object from stored message metadata."""

    if not metadata_json:
        return None
    try:
        payload = json.loads(metadata_json)
    except (TypeError, ValueError, json.JSONDecodeError):
        return None
    if not isinstance(payload, Mapping) or "sleep_analysis" not in payload:
        return None
    try:
        return parse_sleep_analysis(payload["sleep_analysis"])
    except Exception:
        # Existing rows predate this contract; an invalid legacy metadata row
        # must not make an otherwise ordinary Coach question fail.
        return None


def _model_quality(model: SleepModelInsight, through_date: date) -> str:
    if (model.status != "READY" or model.train_n < 60 or model.validation_n < 10
            or model.mae is None or model.reference_mae is None
            or model.validation_start is None or model.validation_end is None
            or not model.validation_start <= model.validation_end <= through_date):
        return "insufficient"
    return "recent_validation_improved" if model.mae < model.reference_mae else "unstable"


def coach_sleep_analysis_context(analysis: SleepAnalysis, latest_sleep_date: str | None) -> dict[str, Any]:
    """Add server freshness and quality labels without changing client facts."""

    data = analysis.model_dump(mode="json")
    through_date = str(data["through_date"])
    latest = str(latest_sleep_date)[:10] if latest_sleep_date else None
    stale = latest is None or latest != through_date
    qualities = [_model_quality(model, analysis.through_date) for model in analysis.models]
    for model, quality in zip(data["models"], qualities):
        model["quality"] = quality
    unique_qualities = set(qualities)
    quality = next(iter(unique_qualities)) if len(unique_qualities) == 1 else "mixed" if qualities else "insufficient"
    data.update(
        {
            "quality": quality,
            "latest_sleep_date": latest,
            "as_of": through_date,
            "stale": stale,
            "freshness": "historical" if stale else "current",
        }
    )
    return data


def sleep_analysis_metadata(analysis: SleepAnalysis) -> str:
    """Serialize the validated payload for chat_messages.metadata_json."""

    return json.dumps(
        {"sleep_analysis": analysis.model_dump(mode="json")},
        ensure_ascii=False,
        separators=(",", ":"),
    )
