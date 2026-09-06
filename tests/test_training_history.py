from __future__ import annotations

import sys
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "server"), str(ROOT / "src")]

from training_history import compute_training_load_history  # noqa: E402
from training_method import compute_training_status  # noqa: E402
from health_queries import trends  # noqa: E402
from health_store import connect  # noqa: E402
from auth import UserContext  # noqa: E402


class TrainingHistoryTests(unittest.TestCase):
    anchor = date(2026, 9, 6)

    def _activity(self, day: date, category: str, load: float) -> dict:
        return {
            "start_time": f"{day.isoformat()}T10:00:00",
            "category": category,
            "internal_load": load,
            "duration_s": 60,
            "effort_source": "reported",
        }

    def test_prefix_history_matches_rhythm_windows_and_category_sums(self) -> None:
        activities = [
            self._activity(self.anchor - timedelta(days=1), "strength", 60),
            self._activity(self.anchor - timedelta(days=3), "easy_aerobic", 30),
            self._activity(self.anchor - timedelta(days=10), "hard_aerobic", 90),
        ]
        coverage = {self.anchor - timedelta(days=offset) for offset in range(56)}
        history = compute_training_load_history(activities, coverage, end_date=self.anchor, days=28)
        point = next(item for item in history["points"] if item["date"] == self.anchor.isoformat())
        sleep = [{"date": (self.anchor - timedelta(days=offset)).isoformat(), "sleep_time_sec": 28800} for offset in range(35)]
        daily = [{"date": (self.anchor - timedelta(days=offset)).isoformat(), "resting_hr": 60} for offset in range(35)]
        rhythm = compute_training_status(
            sleep,
            daily,
            [],
            activities,
            {"score": 80, "components": []},
            self.anchor,
            activity_coverage_dates=coverage,
        )
        self.assertEqual(point["all"]["load_7"], rhythm["acute"]["total_au"])
        self.assertEqual(point["all"]["load_28"], rhythm["chronic"]["total_au"])
        self.assertEqual(point["all"]["reference_weekly"], rhythm["reference"]["weekly_equivalent_au"])
        self.assertIsNotNone(point["all"]["reference_28"])
        self.assertEqual(point["categories"]["strength"]["load_7"], 60.0)
        self.assertEqual(point["categories"]["easy_aerobic"]["load_7"], 30.0)
        self.assertEqual(point["categories"]["hard_aerobic"]["load_28"], 90.0)

    def test_future_is_excluded_and_partial_coverage_keeps_recorded_sum(self) -> None:
        activities = [
            self._activity(self.anchor, "strength", 20),
            self._activity(self.anchor + timedelta(days=1), "anaerobic", 900),
        ]
        partial = {self.anchor - timedelta(days=offset) for offset in range(6)}
        history = compute_training_load_history(activities, partial, end_date=self.anchor, days=28)
        point = next(item for item in history["points"] if item["date"] == self.anchor.isoformat())
        self.assertEqual(point["coverage_7"], 6)
        self.assertIsNone(point["all"]["load_7"])
        self.assertEqual(point["all"]["recorded_7"], 20.0)
        self.assertNotIn("2026-09-07", [item["date"] for item in history["points"]])

    def test_first_output_point_has_no_fabricated_reference_or_missing_days(self) -> None:
        first = self.anchor - timedelta(days=27)
        activities = [self._activity(first, "easy_aerobic", 10)]
        coverage = {first + timedelta(days=index) for index in range(28)}
        history = compute_training_load_history(activities, coverage, end_date=self.anchor, days=28)
        point = history["points"][0]
        self.assertEqual(point["date"], first.isoformat())
        self.assertIsNone(point["all"]["reference_weekly"])
        self.assertEqual(point["all"]["recorded_7"], 10.0)

    def test_trends_training_history_is_opt_in(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            user = UserContext("training-history", "Training", "OWNER", "training@test")
            # UserContext resolves its root through auth.ROOT; use a temporary
            # direct context only for this read-only empty-database contract.
            import auth
            old_root = auth.ROOT
            auth.ROOT = Path(temp)
            try:
                connect(user.health_db).close()
                legacy = trends(user, days=7)
                extended = trends(user, days=7, training_days=28)
            finally:
                auth.ROOT = old_root
        self.assertNotIn("training_load", legacy)
        self.assertIn("training_load", extended)


if __name__ == "__main__":
    unittest.main()
