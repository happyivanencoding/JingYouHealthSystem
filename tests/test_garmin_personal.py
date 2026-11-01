from __future__ import annotations

import importlib.util
import sqlite3
import tempfile
import unittest
from contextlib import closing
from datetime import date, datetime, timezone
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "scripts" / "sync_garmin_history.py"
SPEC = importlib.util.spec_from_file_location("jingyou_sync_personal_test", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
sync_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sync_module)


class FakePersonalClient:
    def __init__(self, steps: int):
        self.steps = steps
        self.closed = False

    def get_daily_summaries(self, start, end):
        return [{"calendarDate": start, "totalSteps": self.steps, "restingHeartRate": 58}]

    def get_daily_steps(self, start, end):
        return [{"date": start, "steps": self.steps}]

    def get_daily_heart_rate(self, day):
        return {"restingHeartRate": 58, "heartRateValues": [[1793350800000, 62]]}

    def get_daily_stress(self, start, end):
        return [{"date": start, "avgStressLevel": 28, "stressValuesArray": [[1793350800000, 28]]}]

    def get_daily_spo2(self, day):
        return {"spO2SingleValues": [[1793350800000, 97]]}

    def get_fitness_age(self, day):
        return {"fitnessAge": 31}

    def get_daily_hydration(self, start, end):
        return {"calendarDate": start, "valueInML": 1000}

    def get_daily_sleep(self, day):
        return {"dailySleepDTO": {
            "sleepScore": 75,
            "sleepTimeSeconds": 27000,
            "deepSleepSeconds": 4800,
            "remSleepSeconds": 6300,
            "lightSleepSeconds": 15900,
            "sleepStartTimestampLocal": "2026-10-29T23:30:00",
            "sleepEndTimestampLocal": "2026-10-30T07:00:00",
        }}

    def get_daily_hrv(self, day):
        return {"hrvSummary": {"status": "BALANCED", "weeklyAvg": 42, "lastNightAvg": 44}}

    def get_daily_body_battery(self, start, end):
        return [{"date": start, "charged": 60, "drained": 35,
                 "bodyBatteryValuesArray": [[1793350800000, 40]]}]

    def search_activities(self, *, start, limit):
        return [{
            "activityId": 100000 + self.steps,
            "activityName": "Test walk",
            "activityType": {"typeKey": "walking"},
            "startTimeLocal": "2026-10-30T10:00:00",
            "duration": 1200,
        }] if start == 0 else []

    def get_activity(self, aid):
        return {"activityId": int(aid), "summaryDTO": {
            "startTimeLocal": "2026-10-30T10:00:00", "duration": 1200}}

    def download_activity(self, aid):
        return b"PK\\x03\\x04"

    def close(self):
        self.closed = True


class PersonalApiSyncTests(unittest.TestCase):
    def test_one_day_with_two_isolated_users_and_activity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for user_id, steps in (("u_owner", 1234), ("u_member", 5678)):
                user_root = root / user_id
                sync_module.USER_ROOT = user_root
                sync_module.DB_PATH = user_root / "health.db"
                sync_module.FIT_DIR = user_root / "activities" / "fit"
                client = FakePersonalClient(steps)
                job = sync_module.PersonalSync(refresh=True, delay=0, client=client)
                try:
                    job.run(date(2026, 10, 30), date(2026, 10, 30), "refresh")
                finally:
                    job.close()
                self.assertTrue(client.closed)
                with closing(sqlite3.connect(user_root / "health.db")) as con:
                    self.assertEqual(
                        con.execute("SELECT steps FROM daily_metrics").fetchone()[0], steps
                    )
                    self.assertEqual(
                        con.execute("SELECT last_night_avg FROM hrv_daily").fetchone()[0], 44
                    )
                    self.assertEqual(
                        con.execute("SELECT sleep_time_sec FROM sleep_sessions").fetchone()[0], 27000
                    )
                    self.assertEqual(
                        con.execute("SELECT COUNT(*) FROM time_series_samples").fetchone()[0], 4
                    )
                    self.assertEqual(
                        con.execute("SELECT COUNT(*) FROM activities").fetchone()[0], 1
                    )
                    self.assertEqual(
                        con.execute("SELECT status FROM sync_runs").fetchone()[0], "completed"
                    )
            with closing(sqlite3.connect(root / "u_owner" / "health.db")) as con:
                self.assertEqual(con.execute("SELECT steps FROM daily_metrics").fetchone()[0], 1234)

    def test_date_selection_rejects_other_day_and_handles_wrappers(self):
        choose = sync_module.PersonalSync._day
        self.assertEqual(
            choose({"dailySummaries": [{"calendarDate": "2026-10-29", "steps": 1},
                                       {"calendarDate": "2026-10-30", "steps": 2}]},
                   "2026-10-30")["steps"], 2
        )
        self.assertIsNone(choose([{"calendarDate": "2026-10-29", "steps": 1},
                                  {"calendarDate": "2026-10-28", "steps": 2}],
                                 "2026-10-30"))

    def test_personal_v2_field_mapping(self):
        row = sync_module.PersonalSync._daily_stats({
            "calendarDate": "2026-10-30",
            "movement": {"steps": {"value": 0}, "floorsAscended": {"value": 4}},
            "calories": {"burnedTotal": 1930},
            "heartRate": {"restingValue": 59},
            "stress": {"avgLevel": 23},
        })
        self.assertEqual(row["totalSteps"], 0)
        self.assertEqual(row["floorsAscended"], 4)
        self.assertEqual(row["totalKilocalories"], 1930)
        self.assertEqual(row["restingHeartRate"], 59)
        self.assertEqual(row["avgStressLevel"], 23)

    def test_official_sleep_local_clock_and_zero_rem(self):
        stamp = 1793314800000
        data = sync_module.PersonalSync._sleep_payload({
            "calendarDate": "2026-10-30",
            "sleepTimeSeconds": 21000,
            "remSleepSeconds": 0,
            "sleepStartTimestampLocal": stamp,
        })["dailySleepDTO"]
        self.assertEqual(data["remSleepSeconds"], 0)
        self.assertEqual(
            data["sleepStartTimestampLocal"],
            datetime.fromtimestamp(stamp / 1000, tz=timezone.utc)
            .strftime("%Y-%m-%dT%H:%M:%S"),
        )


if __name__ == "__main__":
    unittest.main()
