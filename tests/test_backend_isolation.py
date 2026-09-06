from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "server"), str(ROOT / "src")]

import app  # noqa: E402
import auth  # noqa: E402
from fastapi import HTTPException  # noqa: E402
from health_store import connect, utc_now  # noqa: E402
from health_queries import dashboard, trends  # noqa: E402


class BackendIsolationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        app_data = self.root / "data" / "app"
        app_data.mkdir(parents=True)
        profiles = {
            "owner-local": {
                "user_id": "usr_owner_test",
                "display_name": "Owner Person",
                "role": "OWNER",
                "auth_email": "owner@example.test",
            },
            "member-local": {
                "user_id": "usr_member_test",
                "display_name": "Member Person",
                "role": "MEMBER",
                "auth_email": "member@example.test",
            },
        }
        (app_data / "profiles.json").write_text(json.dumps(profiles), encoding="utf-8")

        self.old_auth = (auth.ROOT, auth.APP_DATA, auth.PROFILES_PATH, auth.APP_DB)
        self.old_app_root = app.ROOT
        auth.ROOT = self.root
        auth.APP_DATA = app_data
        auth.PROFILES_PATH = app_data / "profiles.json"
        auth.APP_DB = app_data / "app.db"
        app.ROOT = self.root

        self.owner = auth.user_by_dev_alias("owner")
        self.member = auth.user_by_dev_alias("member")
        assert self.owner is not None and self.member is not None
        self._seed_health(self.owner, 51, 61, 88, "owner-activity")
        self._seed_health(self.member, 50, 72, 89, "member-activity")

    def tearDown(self) -> None:
        auth.ROOT, auth.APP_DATA, auth.PROFILES_PATH, auth.APP_DB = self.old_auth
        app.ROOT = self.old_app_root
        self.tempdir.cleanup()

    def _seed_health(self, user, resting_hr: int, hrv: int, sleep_score: int, activity_id: str) -> None:
        con = connect(user.health_db)
        try:
            con.execute(
                "INSERT INTO daily_metrics(source,date,resting_hr,steps) VALUES('garmin','2026-09-05',?,5000)",
                (resting_hr,),
            )
            # A newer placeholder row must not replace the latest meaningful measurements.
            con.execute("INSERT INTO daily_metrics(source,date) VALUES('garmin','2026-09-06')")
            con.execute(
                "INSERT INTO hrv_daily(source,date,status,weekly_avg,last_night_avg,last_night_5min_high) VALUES('garmin','2026-09-05','BALANCED',60,?,90)",
                (hrv,),
            )
            con.execute(
                "INSERT INTO sleep_sessions(source,date,sleep_score,sleep_time_sec) VALUES('garmin','2026-09-05',?,28800)",
                (sleep_score,),
            )
            con.execute("INSERT INTO sleep_sessions(source,date) VALUES('garmin','2026-09-06')")
            con.execute(
                "INSERT INTO raw_records(source,kind,record_key,event_date,payload_json,fetched_at) VALUES('garmin','training_readiness_raw','2026-09-05','2026-09-05',?,?)",
                (json.dumps([{"score": 77, "level": "HIGH", "sleepScore": sleep_score, "hrvWeeklyAverage": hrv}]), utc_now()),
            )
            con.execute(
                "INSERT INTO raw_records(source,kind,record_key,event_date,payload_json,fetched_at) VALUES('garmin','training_readiness_raw','2026-09-06','2026-09-06','[]',?)",
                (utc_now(),),
            )
            con.execute(
                "INSERT INTO time_series_samples(source,metric,timestamp_key,timestamp_local,value) VALUES('garmin','body_battery','2026-09-05T20:00:00','2026-09-05T20:00:00',42)",
            )
            con.execute(
                "INSERT INTO activities(source,activity_id,activity_name,start_time) VALUES('garmin',?,?,?)",
                (activity_id, activity_id, "2026-09-05T18:00:00"),
            )
            con.commit()
        finally:
            con.close()

    def test_dev_login_uses_role_alias_only(self) -> None:
        self.assertEqual(auth.user_by_dev_alias("owner").user_id, self.owner.user_id)
        self.assertEqual(auth.user_by_dev_alias("MEMBER").user_id, self.member.user_id)
        self.assertIsNone(auth.user_by_dev_alias("Owner Person"))
        self.assertIsNone(auth.user_by_dev_alias("Member Person"))

        local_request = SimpleNamespace(client=SimpleNamespace(host="127.0.0.1"), headers={})
        proxied_request = SimpleNamespace(
            client=SimpleNamespace(host="127.0.0.1"),
            headers={"cf-ray": "test-ray", "cf-connecting-ip": "203.0.113.10"},
        )
        self.assertTrue(app._loopback(local_request))
        self.assertFalse(app._loopback(proxied_request))
        with self.assertRaises(HTTPException) as exc:
            app.dev_login("owner", proxied_request)
        self.assertEqual(exc.exception.status_code, 404)

    def test_sessions_and_cloudflare_identity_map_to_distinct_users(self) -> None:
        owner_token = auth.issue_session(self.owner, "test")
        member_token = auth.issue_session(self.member, "test")
        self.assertNotEqual(owner_token, member_token)
        self.assertEqual(auth.resolve_session(owner_token).user_id, self.owner.user_id)
        self.assertEqual(auth.resolve_session(member_token).user_id, self.member.user_id)
        auth.revoke_session(owner_token)
        self.assertIsNone(auth.resolve_session(owner_token))
        self.assertEqual(auth.resolve_session(member_token).user_id, self.member.user_id)

        with patch.object(auth, "validate_cloudflare_access_jwt", return_value={"email": "owner@example.test"}):
            self.assertEqual(auth.cloudflare_user("token").user_id, self.owner.user_id)
        with patch.object(auth, "validate_cloudflare_access_jwt", return_value={"email": "member@example.test"}):
            self.assertEqual(auth.cloudflare_user("token").user_id, self.member.user_id)

    def test_dashboard_skips_newer_empty_placeholder_rows(self) -> None:
        owner = dashboard(self.owner)
        member = dashboard(self.member)
        self.assertEqual(owner["daily"]["date"], "2026-09-05")
        self.assertEqual(owner["daily"]["resting_hr"], 51)
        self.assertEqual(owner["sleep"]["sleep_score"], 88)
        self.assertEqual(owner["hrv"]["last_night_avg"], 61)
        self.assertEqual(owner["readiness"]["score"], 77)
        self.assertEqual(owner["freshness"]["daily"], "2026-09-05")
        self.assertEqual(member["daily"]["resting_hr"], 50)
        self.assertEqual(member["hrv"]["last_night_avg"], 72)
        self.assertNotEqual(owner["recent_activities"][0]["activity_id"], member["recent_activities"][0]["activity_id"])
        self.assertEqual(len(trends(self.owner, days=7)["daily"]), 1)

    def test_pull_refresh_uses_live_refresh_phase(self) -> None:
        completed = SimpleNamespace(returncode=0, stdout="ok", stderr="")
        with (
            patch.object(app.subprocess, "run", return_value=completed) as run,
            patch.object(app, "dashboard", return_value={"date": "2026-09-06"}),
        ):
            result = app.refresh(self.owner)

        command = run.call_args.args[0]
        self.assertEqual(result["ok"], True)
        self.assertIn("--user-id", command)
        self.assertEqual(command[command.index("--user-id") + 1], self.owner.user_id)
        self.assertEqual(command[command.index("--phase") + 1], "refresh")

    def test_chat_threads_and_agent_context_are_physically_user_scoped(self) -> None:
        owner_thread = app.create_thread(app.ChatCreate(title="Owner thread"), self.owner)
        member_thread = app.create_thread(app.ChatCreate(title="Member thread"), self.member)
        app.post_message(owner_thread["id"], app.MessageCreate(content="owner question"), self.owner)
        app.post_message(member_thread["id"], app.MessageCreate(content="member question"), self.member)

        with self.assertRaises(HTTPException) as exc:
            app.thread_messages(owner_thread["id"], self.member)
        self.assertEqual(exc.exception.status_code, 404)
        self.assertEqual([row["id"] for row in app.chat_threads(self.owner)], [owner_thread["id"]])
        self.assertEqual([row["id"] for row in app.chat_threads(self.member)], [member_thread["id"]])

        captured: list[dict] = []

        def fake_run(command, **kwargs):
            cwd = Path(command[command.index("-Cwd") + 1])
            output = Path(command[command.index("-OutputPath") + 1])
            context = json.loads((cwd / "context.json").read_text(encoding="utf-8"))
            captured.append(context)
            expected_hrv = 61 if context["profile"]["display_name"] == "Owner Person" else 72
            self.assertEqual(context["health"]["today"]["hrv"]["last_night_avg"], expected_hrv)
            output.write_text(f"answer for {context['profile']['display_name']} hrv={expected_hrv}", encoding="utf-8")
            return SimpleNamespace(returncode=0, stdout="", stderr="")

        with patch.object(app.subprocess, "run", side_effect=fake_run):
            owner_answer = app.answer_thread(owner_thread["id"], self.owner)
            member_answer = app.answer_thread(member_thread["id"], self.member)

        self.assertIn("Owner Person", owner_answer["content"])
        self.assertIn("hrv=61", owner_answer["content"])
        self.assertIn("Member Person", member_answer["content"])
        self.assertIn("hrv=72", member_answer["content"])
        self.assertEqual(captured[0]["conversation"][-1]["content"], "owner question")
        self.assertEqual(captured[1]["conversation"][-1]["content"], "member question")
        self.assertNotIn("Member Person", json.dumps(captured[0]))
        self.assertNotIn("Owner Person", json.dumps(captured[1]))


if __name__ == "__main__":
    unittest.main()
