from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "server"), str(ROOT / "src")]

import app  # noqa: E402
import auth  # noqa: E402
from health_store import connect  # noqa: E402
from health_queries import activity_coverage_dates  # noqa: E402
from training_method import compute_training_status  # noqa: E402


class TrainingMethodTests(unittest.TestCase):
    anchor = date(2026, 9, 6)

    def _health_rows(self, *, reference_days: int = 24, short_days: int = 6):
        sleep_rows = []
        daily_rows = []
        for offset in range(1, reference_days + 1):
            day = self.anchor - timedelta(days=offset)
            sleep_rows.append({"date": day.isoformat(), "sleep_time_sec": 8 * 3600})
            daily_rows.append({"date": day.isoformat(), "resting_hr": 60})
        for offset in range(0, short_days):
            day = self.anchor - timedelta(days=offset)
            sleep_rows.append({"date": day.isoformat(), "sleep_time_sec": 8 * 3600})
            daily_rows.append({"date": day.isoformat(), "resting_hr": 60})
        return sleep_rows, daily_rows

    def _activity(self, day: date, category: str, load: float, *, source: str = "reported", minutes: float = 30.0):
        return {
            "start_time": f"{day.isoformat()}T10:00:00",
            "category": category,
            "effort_source": source,
            "internal_load": load,
            "duration_s": minutes * 60,
        }

    def _coverage(self, days: int = 56) -> set[date]:
        return {self.anchor - timedelta(days=offset) for offset in range(days)}

    def test_unknown_dates_do_not_become_zero_and_zero_reference_is_building(self) -> None:
        sleep, daily = self._health_rows(reference_days=30, short_days=6)
        unknown = compute_training_status(sleep, daily, [], [], None, self.anchor)
        self.assertEqual(unknown["short_term"]["coverage"], "insufficient")
        self.assertIsNone(unknown["relative_ratio"])
        result = compute_training_status(sleep, daily, [], [], None, self.anchor, activity_coverage_dates=self._coverage())
        self.assertEqual(result["short_term"]["observed_days"], 7)
        self.assertEqual(result["reference"]["observed_days"], 28)
        self.assertEqual(result["reference"]["weekly_equivalent_au"], 0.0)
        self.assertIsNone(result["relative_ratio"])
        self.assertEqual(result["load_trend"], "building")
        self.assertEqual(result["short_term"]["trend"], "building")

        six_day = compute_training_status(
            sleep,
            daily,
            [],
            [],
            None,
            self.anchor,
            activity_coverage_dates=self._coverage(6),
        )
        self.assertEqual(six_day["short_term"]["trend"], "insufficient")
        self.assertIsNone(six_day["short_term"]["relative_ratio"])

    def test_boundaries_and_recent_load_use_only_target_window(self) -> None:
        sleep, daily = self._health_rows(reference_days=30, short_days=6)
        activities = [
            self._activity(self.anchor - timedelta(days=6), "easy_aerobic", 60),
            self._activity(self.anchor, "strength", 120),
            self._activity(self.anchor + timedelta(days=1), "anaerobic", 999),
        ]
        result = compute_training_status(sleep, daily, [], activities, None, self.anchor, activity_coverage_dates=self._coverage())
        self.assertEqual(result["short_term"]["au"], 180.0)
        self.assertEqual(result["long_term"]["au"], 180.0)
        self.assertEqual(result["categories"][3]["key"], "strength")
        self.assertEqual(result["categories"][3]["au_7"], 120.0)

    def test_estimated_effort_is_labeled_but_does_not_block_goal_focus(self) -> None:
        sleep, daily = self._health_rows(reference_days=30, short_days=6)
        activities = [self._activity(self.anchor - timedelta(days=3), "strength", 180, source="estimated")]
        result = compute_training_status(sleep, daily, [], activities, {"score": 85, "components": [{"key": "hrv", "score": 80}]}, self.anchor, goal="strength", activity_coverage_dates=self._coverage())
        self.assertEqual(result["effort_confidence"], "estimated")
        self.assertGreaterEqual(result["estimated_load_ratio"], 0.5)
        self.assertEqual(result["direction"]["intensity"], "conservative")
        self.assertEqual(result["direction"]["focus"], "strength")
        self.assertEqual(result["direction"]["reason_key"], "record_effort_needed")

    def test_recovery_and_tired_checkin_take_priority(self) -> None:
        sleep, daily = self._health_rows(reference_days=24, short_days=6)
        activities = [self._activity(self.anchor - timedelta(days=1), "strength", 180)]
        recovery = {"score": 55, "components": []}
        result = compute_training_status(
            sleep,
            daily,
            [],
            activities,
            recovery,
            self.anchor,
            goal="strength",
            checkin={"date": self.anchor.isoformat(), "feeling": "tired"},
            activity_coverage_dates=self._coverage(),
        )
        self.assertEqual(result["direction"]["intensity"], "recover")
        self.assertEqual(result["direction"]["focus"], "easy_aerobic")
        self.assertEqual(result["direction"]["reason_key"], "checkin_tired")

    def test_partial_recovery_and_dense_intensity_do_not_claim_low_load_is_a_license_to_add(self) -> None:
        sleep, daily = self._health_rows(reference_days=35, short_days=7)
        coverage = [self.anchor - timedelta(days=i) for i in range(35)]
        result = compute_training_status(sleep, daily, [], [], {"score": 90, "components": [{"key": "sleep", "score": 90}, {"key": "load", "score": 90}]}, self.anchor, goal="strength", activity_coverage_dates=coverage)
        self.assertEqual(result["direction"]["focus"], "base")
        self.assertEqual(result["direction"]["reason_key"], "recovery_signals_partial")
        activities = [self._activity(self.anchor - timedelta(days=8), "easy_aerobic", 4000),
                      self._activity(self.anchor, "hard_aerobic", 100),
                      self._activity(self.anchor - timedelta(days=1), "anaerobic", 100)]
        result = compute_training_status(sleep, daily, [], activities, {"score": 85, "components": [{"key": "hrv", "score": 80}]}, self.anchor, activity_coverage_dates=coverage)
        self.assertEqual(result["load_trend"], "lighter")
        self.assertEqual(result["direction"]["intensity"], "consolidate")
        self.assertEqual(result["direction"]["reason_key"], "recent_intensity_dense")

    def test_goal_and_checkin_preferences_are_per_user(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            app_data = root / "data" / "app"
            app_data.mkdir(parents=True)
            (app_data / "profiles.json").write_text(
                json.dumps(
                    {
                        "owner": {"user_id": "usr_training_owner", "display_name": "Owner", "role": "OWNER", "auth_email": "owner@test"},
                        "member": {"user_id": "usr_training_member", "display_name": "Member", "role": "MEMBER", "auth_email": "member@test"},
                    }
                ),
                encoding="utf-8",
            )
            old = (auth.ROOT, auth.APP_DATA, auth.PROFILES_PATH, auth.APP_DB)
            old_app_root = app.ROOT
            auth.ROOT, auth.APP_DATA, auth.PROFILES_PATH, auth.APP_DB = root, app_data, app_data / "profiles.json", app_data / "app.db"
            app.ROOT = root
            try:
                owner = auth.user_by_dev_alias("owner")
                member = auth.user_by_dev_alias("member")
                assert owner is not None and member is not None
                for user in (owner, member):
                    con = connect(user.health_db)
                    con.execute("INSERT INTO sleep_sessions(source,date,sleep_time_sec) VALUES('garmin','2026-09-06',28800)")
                    con.commit()
                    con.close()
                app.put_training_preferences(app.TrainingPreferenceUpdate(goal="strength"), owner)
                app.put_training_preferences(app.TrainingPreferenceUpdate(goal="endurance"), member)
                app.put_training_checkin(app.TrainingCheckinUpdate(feeling="tired"), owner)
                app.put_training_checkin(app.TrainingCheckinUpdate(feeling="fresh"), member)
                self.assertEqual(app.get_training(user=owner)["training"]["goal"], "strength")
                self.assertEqual(app.get_training(user=member)["training"]["goal"], "endurance")
                self.assertEqual(app.get_training(user=owner)["training"]["feeling"], "tired")
                self.assertEqual(app.get_training(user=member)["training"]["feeling"], "fresh")
            finally:
                auth.ROOT, auth.APP_DATA, auth.PROFILES_PATH, auth.APP_DB = old
                app.ROOT = old_app_root

    def test_activity_coverage_helper_uses_completed_sync_and_refresh_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            con = connect(Path(temp) / "health.db")
            con.execute(
                """INSERT INTO sync_runs(source,kind,started_at,finished_at,status,start_date,end_date)
                   VALUES('garmin','activities','2026-09-01T00:00:00+00:00','2026-09-01T01:00:00+00:00','completed','2026-08-01','2026-09-05')"""
            )
            con.execute(
                """INSERT INTO sync_runs(source,kind,started_at,finished_at,status,start_date,end_date)
                   VALUES('garmin','refresh','2026-09-06T09:00:00+00:00','2026-09-06T09:01:00+00:00','completed','2026-09-04','2026-09-06')"""
            )
            payload = json.dumps([{"startTimeLocal": "2026-09-06T10:00:00"}])
            con.execute(
                """INSERT INTO raw_records(source,kind,record_key,payload_json,fetched_at)
                   VALUES('garmin','activity_list_recent','latest',?,'2026-09-06T09:00:30+00:00')""",
                (payload,),
            )
            con.commit()
            covered = activity_coverage_dates(con, self.anchor)
            con.close()
        self.assertIn(date(2026, 9, 5), covered)
        self.assertIn(date(2026, 9, 6), covered)
        self.assertEqual(min(covered), date(2026, 8, 3))


if __name__ == "__main__":
    unittest.main()
