from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "server"), str(ROOT / "src")]

import app  # noqa: E402
import auth  # noqa: E402
from derived_metrics import classify_activity, compute_recovery, enrich_activity, sleep_clock  # noqa: E402
from health_queries import dashboard, trends  # noqa: E402
from health_store import connect, utc_now  # noqa: E402
from fastapi import HTTPException  # noqa: E402


def _epoch_ms(value: datetime) -> int:
    return int(value.replace(tzinfo=timezone.utc).timestamp() * 1000)


class DerivedMetricTests(unittest.TestCase):
    def test_sleep_local_clock_uses_local_only_and_detects_offset_change(self) -> None:
        start = datetime(2026, 9, 5, 22, 0)
        end = start + timedelta(hours=8)
        payload = {
            "dailySleepDTO": {
                "sleepStartTimestampLocal": _epoch_ms(start),
                "sleepEndTimestampLocal": _epoch_ms(end),
                "sleepStartTimestampGMT": _epoch_ms(start - timedelta(hours=2)),
                "sleepEndTimestampGMT": _epoch_ms(end - timedelta(hours=1)),
            }
        }
        result = sleep_clock(payload)
        self.assertEqual(result["sleep_start_local"], "2026-09-05T22:00:00")
        self.assertEqual(result["sleep_end_local"], "2026-09-06T06:00:00")
        self.assertEqual(result["clock_source"], "local")
        self.assertTrue(result["clock_offset_changed"])

        missing_local = sleep_clock(
            {"dailySleepDTO": {"sleepStartTimestampGMT": _epoch_ms(start), "sleepEndTimestampGMT": _epoch_ms(end)}}
        )
        self.assertIsNone(missing_local["sleep_start_local"])
        self.assertIsNone(missing_local["clock_source"])
        self.assertIsNone(missing_local["clock_offset_changed"])

        invalid = sleep_clock(
            {"dailySleepDTO": {"sleepStartTimestampLocal": _epoch_ms(end), "sleepEndTimestampLocal": _epoch_ms(start)}}
        )
        self.assertIsNone(invalid["sleep_end_local"])

    def test_activity_category_priority_and_rpe_estimate(self) -> None:
        base = {
            "activity_type": "running",
            "duration_s": 1800,
            "training_effect": 1.0,
            "anaerobic_training_effect": 0.0,
            "training_effect_label": "BASE",
        }
        self.assertEqual(classify_activity(base), "easy_aerobic")
        self.assertEqual(classify_activity({**base, "activity_type": "strength_training"}), "strength")
        self.assertEqual(classify_activity(base, "hard_aerobic"), "hard_aerobic")

        estimated = enrich_activity(base)
        self.assertEqual(estimated["effort_rpe"], 3.0)
        self.assertEqual(estimated["effort_source"], "estimated")
        self.assertEqual(estimated["internal_load"], 90.0)
        reported = enrich_activity(base, {"effort_rpe": 7.0, "category_override": "anaerobic"})
        self.assertEqual(reported["category"], "anaerobic")
        self.assertEqual(reported["effort_source"], "reported")
        self.assertEqual(reported["internal_load"], 210.0)

    def test_effort_put_is_user_scoped_and_clearable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            owner = auth.UserContext("owner", "Owner", "OWNER", "owner@example.test")
            member = auth.UserContext("member", "Member", "MEMBER", "member@example.test")
            old_root = auth.ROOT
            auth.ROOT = root
            try:
                for user in (owner, member):
                    con = connect(user.health_db)
                    con.execute(
                        """INSERT INTO activities(source,activity_id,activity_name,activity_type,
                           start_time,duration_s,training_effect_label)
                           VALUES('garmin',?,?,?,?,?,?)""",
                        ("a1" if user is owner else "m1", "run", "running", "2026-09-05T10:00:00", 600, "BASE"),
                    )
                    con.commit()
                    con.close()

                saved = app.put_activity_effort("a1", app.ActivityEffortUpdate(rpe=8, category="anaerobic"), owner)
                self.assertEqual(saved["effort_rpe"], 8.0)
                self.assertEqual(saved["category_override"], "anaerobic")
                self.assertEqual(saved["category"], "anaerobic")
                cleared = app.put_activity_effort("a1", app.ActivityEffortUpdate(rpe=None, category=None), owner)
                self.assertEqual(cleared["category"], "easy_aerobic")
                self.assertEqual(cleared["effort_source"], "estimated")
                with self.assertRaises(HTTPException) as exc:
                    app.put_activity_effort("a1", app.ActivityEffortUpdate(rpe=5), member)
                self.assertEqual(exc.exception.status_code, 404)
            finally:
                auth.ROOT = old_root

    def _recovery_fixture(self, anchor: date = date(2026, 9, 6)) -> tuple[list[dict], list[dict], list[dict], list[dict]]:
        sleeps: list[dict] = []
        hrvs: list[dict] = []
        daily: list[dict] = []
        activities: list[dict] = []
        for offset in range(42, 0, -1):
            day = anchor - timedelta(days=offset)
            text = day.isoformat()
            sleeps.append({"date": text, "sleep_time_sec": 8 * 3600, "sleep_score": 80})
            hrvs.append({"date": text, "last_night_avg": 50})
            daily.append({"date": text, "resting_hr": 60})
            if offset <= 14:
                activities.append({"activity_id": f"a{offset}", "start_time": f"{text}T10:00:00", "duration_s": 60, "activity_type": "running"})
        sleeps.extend(
            [
                {"date": (anchor - timedelta(days=2)).isoformat(), "sleep_time_sec": 8 * 3600, "sleep_score": 80},
                {"date": (anchor - timedelta(days=1)).isoformat(), "sleep_time_sec": 8 * 3600, "sleep_score": 80},
                {"date": anchor.isoformat(), "sleep_time_sec": 8 * 3600, "sleep_score": 80},
            ]
        )
        hrvs.extend(
            [{"date": (anchor - timedelta(days=offset)).isoformat(), "last_night_avg": 50} for offset in range(0, 7)]
        )
        daily.extend(
            [{"date": (anchor - timedelta(days=offset)).isoformat(), "resting_hr": 60} for offset in range(0, 3)]
        )
        activities.append({"activity_id": "today", "start_time": f"{anchor.isoformat()}T10:00:00", "duration_s": 60, "activity_type": "running"})
        return sleeps, hrvs, daily, activities

    def test_recovery_sleep_shortening_zero_missing_and_future_isolated(self) -> None:
        sleeps, hrvs, daily, activities = self._recovery_fixture()
        normal = compute_recovery(sleep_rows=sleeps, hrv_rows=hrvs, daily_rows=daily, activity_rows=activities, anchor_date="2026-09-06")
        self.assertEqual(normal["components"][0]["sample_count"], 3)
        sparse_sleep = [
            row
            for row in sleeps
            if row["date"] not in {"2026-09-05", "2026-09-04"}
        ]
        sparse = compute_recovery(
            sleep_rows=sparse_sleep,
            hrv_rows=hrvs,
            daily_rows=daily,
            activity_rows=activities,
            anchor_date="2026-09-06",
        )
        # The older 2026-09-03 night is outside the strict three-calendar-day
        # window and must not be pulled into last3validMean.
        self.assertEqual(sparse["components"][0]["sample_count"], 1)

        short_sleeps = [row for row in sleeps if row["date"] != "2026-09-06"] + [{"date": "2026-09-06", "sleep_time_sec": 0, "sleep_score": 0}]
        zero = compute_recovery(sleep_rows=short_sleeps, hrv_rows=hrvs, daily_rows=daily, activity_rows=activities, anchor_date="2026-09-06")
        self.assertIsNotNone(normal["score"])
        self.assertIsNotNone(zero["score"])
        self.assertLess(zero["score"], normal["score"])
        missing = compute_recovery(
            sleep_rows=[row for row in sleeps if row["date"] != "2026-09-06"],
            hrv_rows=hrvs,
            daily_rows=daily,
            activity_rows=activities,
            anchor_date="2026-09-06",
        )
        self.assertIsNone(missing["components"][0]["score"])
        self.assertIsNone(missing["score"])
        future = compute_recovery(
            sleep_rows=sleeps + [{"date": "2026-09-07", "sleep_time_sec": 0, "sleep_score": 0}],
            hrv_rows=hrvs + [{"date": "2026-09-07", "last_night_avg": 1}],
            daily_rows=daily + [{"date": "2026-09-07", "resting_hr": 200}],
            activity_rows=activities,
            anchor_date="2026-09-06",
        )
        self.assertEqual(future["score"], normal["score"])

        zero_rhr = [
            row
            for row in daily
            if row["date"] not in {"2026-09-06", "2026-09-05", "2026-09-04"}
        ] + [{"date": "2026-09-06", "resting_hr": 0}]
        zero_rhr_result = compute_recovery(
            sleep_rows=sleeps,
            hrv_rows=hrvs,
            daily_rows=zero_rhr,
            activity_rows=activities,
            anchor_date="2026-09-06",
        )
        self.assertIsNone(zero_rhr_result["components"][2]["score"])
        self.assertIsNone(zero_rhr_result["components"][2]["value"])

    def test_load_coverage_counts_recorded_rest_and_keeps_unknown_days_unknown(self) -> None:
        anchor = date(2026, 9, 6)
        sleep_rows = [
            {"date": (anchor - timedelta(days=offset)).isoformat(), "sleep_time_sec": 8 * 3600}
            for offset in range(1, 8)
        ] + [{"date": anchor.isoformat(), "sleep_time_sec": 8 * 3600}]
        # Seven recorded rest/sleep dates and seven recorded daily dates form
        # fourteen observed prior dates. The other fourteen calendar dates
        # have no record and must not be silently treated as zero load.
        daily_rows = [
            {"date": (anchor - timedelta(days=offset)).isoformat(), "resting_hr": 60}
            for offset in range(8, 15)
        ]
        activity_rows = [
            {
                "activity_id": "observed-workout",
                "start_time": (anchor - timedelta(days=1)).isoformat() + "T10:00:00",
                "duration_s": 60,
                "activity_type": "running",
            }
        ]
        result = compute_recovery(
            sleep_rows=sleep_rows,
            hrv_rows=[],
            daily_rows=daily_rows,
            activity_rows=activity_rows,
            anchor_date=anchor,
        )
        load = result["components"][3]
        self.assertEqual(load["covered_days"], 14)
        self.assertIsNotNone(load["score"])
        # One easy-aerobic minute is 3 AU.  It is averaged over the 14 known
        # dates and then scaled to the recent three-day comparison window;
        # unknown dates never enter the denominator as zeros.
        self.assertAlmostEqual(load["baseline"], (3.0 / 14.0) * 3.0)

    def test_dashboard_and_trends_use_same_recovery_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            user = auth.UserContext("owner", "Owner", "OWNER", "owner@example.test")
            old_root = auth.ROOT
            auth.ROOT = root
            try:
                con = connect(user.health_db)
                for offset in range(42, -1, -1):
                    day = date(2026, 9, 6) - timedelta(days=offset)
                    text = day.isoformat()
                    raw = json.dumps({"dailySleepDTO": {"sleepStartTimestampLocal": _epoch_ms(datetime(day.year, day.month, day.day, 22)), "sleepEndTimestampLocal": _epoch_ms(datetime(day.year, day.month, day.day, 6) + timedelta(days=1))}})
                    con.execute("INSERT INTO sleep_sessions(source,date,sleep_time_sec,raw_json) VALUES('garmin',?,?,?)", (text, 8 * 3600, raw))
                    con.execute("INSERT INTO hrv_daily(source,date,last_night_avg) VALUES('garmin',?,50)", (text,))
                    con.execute("INSERT INTO daily_metrics(source,date,resting_hr,steps) VALUES('garmin',?,60,1)", (text,))
                for offset in range(1, 15):
                    day = date(2026, 9, 6) - timedelta(days=offset)
                    con.execute("INSERT INTO activities(source,activity_id,start_time,duration_s,activity_type) VALUES('garmin',?,?,60,'running')", (f"a{offset}", f"{day.isoformat()}T10:00:00"))
                con.commit()
                con.close()
                today = dashboard(user)
                history = trends(user, days=7)
                self.assertEqual(today["date"], "2026-09-06")
                self.assertEqual(today["readiness"]["score"], history["readiness"][-1]["score"])
                self.assertNotIn("raw_json", json.dumps(history))
                self.assertEqual(history["sleep"][-1]["sleep_start_local"], "2026-09-06T22:00:00")
            finally:
                auth.ROOT = old_root


if __name__ == "__main__":
    unittest.main()
