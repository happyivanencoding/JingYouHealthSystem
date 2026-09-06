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
from coach_insights import SleepAnalysis, coach_sleep_analysis_context  # noqa: E402
from health_store import connect  # noqa: E402
from pydantic import ValidationError  # noqa: E402


def analysis_payload(*, through_date: str = "2026-09-06", mae: float = 1.0, reference_mae: float = 1.2) -> dict:
    return {
        "schema_version": 1,
        "source": "android_personal_sleep_v1",
        "through_date": through_date,
        "generated_at": "2026-09-06T10:00:00Z",
        "french_holidays": True,
        "models": [
            {
                "outcome": "DURATION_HOURS",
                "status": "READY",
                "algorithm": "RANDOM_FOREST",
                "factor_a": "STRESS",
                "factor_b": "TRAINING_LOAD",
                "feature_pack": "ENRICHED",
                "lag_days": 1,
                "train_n": 60,
                "validation_n": 10,
                "validation_start": "2026-08-01",
                "validation_end": "2026-08-10",
                "selection_mae": 1.1,
                "selection_reference_mae": 1.2,
                "mae": mae,
                "reference_mae": reference_mae,
                "feature_importance": [
                    {"feature": "factor_a", "mae_increase": -0.15, "repeat_sd": 0.2},
                ],
                "dropped_features": [],
            }
        ],
        "timing": {
            "night_count": 30,
            "usual_bedtime_hour": 23.5,
            "usual_wake_hour": 7.2,
            "late_count": 8,
            "other_count": 22,
            "bedtime_shift_hours": 1.1,
            "wake_shift_hours": 0.2,
            "late_sleep_hours": 6.8,
            "other_sleep_hours": 7.7,
            "late_deep_hours": 1.0,
            "other_deep_hours": 1.2,
            "late_rem_hours": 1.4,
            "other_rem_hours": 1.6,
        },
    }


class CoachInsightsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        app_data = self.root / "data" / "app"
        app_data.mkdir(parents=True)
        profiles = {
            "owner-local": {
                "user_id": "usr_owner_coach",
                "display_name": "Owner Coach",
                "role": "OWNER",
                "auth_email": "owner-coach@example.test",
            },
            "member-local": {
                "user_id": "usr_member_coach",
                "display_name": "Member Coach",
                "role": "MEMBER",
                "auth_email": "member-coach@example.test",
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

    def tearDown(self) -> None:
        auth.ROOT, auth.APP_DATA, auth.PROFILES_PATH, auth.APP_DB = self.old_auth
        app.ROOT = self.old_app_root
        self.tempdir.cleanup()

    def test_message_stores_validated_snapshot_without_mixing_content(self) -> None:
        thread = app.create_thread(app.ChatCreate(title="Insights"), self.owner)
        body = app.MessageCreate(content="我昨晚为什么睡得短？", sleep_analysis=analysis_payload())
        result = app.post_message(thread["id"], body, self.owner)
        self.assertEqual(result["content"], body.content)
        self.assertNotIn("sleep_analysis", result["content"])
        con = connect(self.owner.health_db)
        try:
            row = con.execute("SELECT content,metadata_json FROM chat_messages WHERE id=?", (result["id"],)).fetchone()
        finally:
            con.close()
        self.assertEqual(row["content"], body.content)
        metadata = json.loads(row["metadata_json"])
        self.assertEqual(metadata["sleep_analysis"]["source"], "android_personal_sleep_v1")
        self.assertNotIn("user_id", metadata)
        self.assertNotIn("narrative", metadata)

    def test_strict_schema_rejects_extra_oversized_and_invalid_values(self) -> None:
        extra = analysis_payload()
        extra["narrative"] = "do something"
        with self.assertRaises(ValidationError):
            SleepAnalysis.model_validate(extra)

        too_many = analysis_payload()
        too_many["models"] = [analysis_payload()["models"][0] for _ in range(6)]
        with self.assertRaises(ValidationError):
            SleepAnalysis.model_validate(too_many)

        bad_importance = analysis_payload()
        bad_importance["models"][0]["feature_importance"][0]["repeat_sd"] = -0.1
        with self.assertRaises(ValidationError):
            SleepAnalysis.model_validate(bad_importance)

        bad_token = analysis_payload()
        bad_token["models"][0]["feature_importance"][0]["feature"] = "x" * 81
        with self.assertRaises(ValidationError):
            SleepAnalysis.model_validate(bad_token)

    def _fake_runner(self, captured: list[dict], answer_payload: object | str):
        def run(command, **kwargs):
            cwd = Path(command[command.index("-Cwd") + 1])
            output = Path(command[command.index("-OutputPath") + 1])
            captured.append(json.loads((cwd / "context.json").read_text(encoding="utf-8")))
            if isinstance(answer_payload, str):
                text = answer_payload
            else:
                text = json.dumps(answer_payload, ensure_ascii=False)
            output.write_text(text, encoding="utf-8")
            return SimpleNamespace(returncode=0, stdout=b"", stderr=b"")

        return run

    def test_latest_user_without_snapshot_does_not_reuse_old_snapshot(self) -> None:
        thread = app.create_thread(app.ChatCreate(title="Insights"), self.owner)
        app.post_message(thread["id"], app.MessageCreate(content="old", sleep_analysis=analysis_payload()), self.owner)
        app.post_message(thread["id"], app.MessageCreate(content="new ordinary question"), self.owner)
        captured: list[dict] = []
        with patch.object(app.subprocess, "run", side_effect=self._fake_runner(captured, {"answer": "  ok  ", "memory_updates": []})):
            result = app.answer_thread(thread["id"], self.owner)
        self.assertEqual(result["content"], "ok")
        self.assertNotIn("sleep_analysis", captured[-1])

    def test_weak_model_is_passed_and_marked_historical_unstable_with_negative_importance(self) -> None:
        thread = app.create_thread(app.ChatCreate(title="Insights"), self.owner)
        payload = analysis_payload(through_date="2026-09-05", mae=1.3, reference_mae=1.0)
        app.post_message(thread["id"], app.MessageCreate(content="普通问题", sleep_analysis=payload), self.owner)
        captured: list[dict] = []
        fake_health = {"today": {"sleep": {"date": "2026-09-06"}}, "trends": {}, "recent_activities": []}
        with (
            patch.object(app, "agent_context", return_value=fake_health),
            patch.object(app.subprocess, "run", side_effect=self._fake_runner(captured, {"answer": "好的", "memory_updates": []})),
        ):
            app.answer_thread(thread["id"], self.owner)
        insight = captured[-1]["sleep_analysis"]
        self.assertEqual(insight["quality"], "unstable")
        self.assertTrue(insight["stale"])
        self.assertEqual(insight["freshness"], "historical")
        self.assertEqual(insight["models"][0]["feature_importance"][0]["mae_increase"], -0.15)

    def test_owner_and_member_snapshots_are_read_from_their_own_databases(self) -> None:
        owner_thread = app.create_thread(app.ChatCreate(title="Owner"), self.owner)
        member_thread = app.create_thread(app.ChatCreate(title="Member"), self.member)
        app.post_message(owner_thread["id"], app.MessageCreate(content="owner", sleep_analysis=analysis_payload(through_date="2026-09-01")), self.owner)
        app.post_message(member_thread["id"], app.MessageCreate(content="member", sleep_analysis=analysis_payload(through_date="2026-09-02")), self.member)
        captured: list[dict] = []
        with patch.object(app.subprocess, "run", side_effect=self._fake_runner(captured, "plain text fallback")):
            app.answer_thread(owner_thread["id"], self.owner)
            app.answer_thread(member_thread["id"], self.member)
        self.assertEqual(captured[0]["sleep_analysis"]["through_date"], "2026-09-01")
        self.assertEqual(captured[1]["sleep_analysis"]["through_date"], "2026-09-02")
        self.assertNotEqual(captured[0]["sleep_analysis"]["through_date"], captured[1]["sleep_analysis"]["through_date"])

    def test_context_labels_current_snapshot_and_envelope_strips_answer(self) -> None:
        analysis = SleepAnalysis.model_validate(analysis_payload())
        context = coach_sleep_analysis_context(analysis, "2026-09-06")
        self.assertFalse(context["stale"])
        self.assertEqual(context["freshness"], "current")
        self.assertEqual(context["as_of"], "2026-09-06")
        self.assertEqual(app._decode_coach_output('{"answer":"  hello  ","memory_updates":[]}'), ("hello", []))
        self.assertEqual(app._decode_coach_output('```json\n{"answer":"  fenced  ","memory_updates":{}}\n```'), ("fenced", []))
        self.assertEqual(app._decode_coach_output('{"answer":123,"memory_updates":[]}'), ("", []))
        self.assertEqual(app._decode_coach_output('{"answer":"  ","memory_updates":[]}'), ("", []))
        self.assertEqual(app._decode_coach_output('{"answer":"incomplete'), ("", []))
        self.assertEqual(app._decode_coach_output("plain answer"), ("plain answer", []))

    def test_quality_is_specific_to_each_sleep_outcome(self) -> None:
        payload = analysis_payload(mae=1.4, reference_mae=1.0)
        payload["models"].append(dict(payload["models"][0], outcome="DEEP_HOURS", mae=0.2, reference_mae=0.4))
        context = coach_sleep_analysis_context(SleepAnalysis.model_validate(payload), "2026-09-06")
        self.assertEqual(context["quality"], "mixed")
        self.assertEqual([model["quality"] for model in context["models"]], ["unstable", "recent_validation_improved"])
        payload["models"][1]["train_n"] = 0
        context = coach_sleep_analysis_context(SleepAnalysis.model_validate(payload), "2026-09-06")
        self.assertEqual(context["models"][1]["quality"], "insufficient")

    def test_prompt_prioritizes_new_question_and_avoids_repeating_recent_answers(self) -> None:
        thread = app.create_thread(app.ChatCreate(title="Focus"), self.owner)
        app.post_message(thread["id"], app.MessageCreate(content="换个问题：法国节假日特征怎么计算？"), self.owner)
        prompts: list[str] = []

        def run(command, **kwargs):
            cwd = Path(command[command.index("-Cwd") + 1])
            output = Path(command[command.index("-OutputPath") + 1])
            context = json.loads((cwd / "context.json").read_text(encoding="utf-8"))
            prompts.append((cwd / "prompt.txt").read_text(encoding="utf-8"))
            self.assertTrue(context["response_focus"]["avoid_repeating_recent_answers"])
            self.assertEqual(context["current_question"], "换个问题：法国节假日特征怎么计算？")
            output.write_text(json.dumps({"answer": "节假日特征使用法国全国法定节假日。", "memory_updates": []}), encoding="utf-8")
            return SimpleNamespace(returncode=0, stdout=b"", stderr=b"")

        with patch.object(app.subprocess, "run", side_effect=run):
            answer = app.answer_thread(thread["id"], self.owner)
        self.assertEqual(answer["content"], "节假日特征使用法国全国法定节假日。")
        self.assertIn("Answer that question first", prompts[0])
        self.assertIn("do not repeat it", prompts[0])
        self.assertIn("do not append a recommendation on every turn", prompts[0])

    def test_memory_envelope_applies_only_current_user_source_and_keeps_answer(self) -> None:
        thread = app.create_thread(app.ChatCreate(title="Memory"), self.owner)
        user_message = app.post_message(thread["id"], app.MessageCreate(content="我每周一做力量训练，请记住。"), self.owner)
        valid_update = {
            "action": "upsert",
            "key": "routine.monday_strength",
            "category": "routines",
            "text": "每周一做力量训练",
            "confidence": "user_stated",
            "source_message_ids": [user_message["id"]],
        }
        captured: list[dict] = []
        with patch.object(
            app.subprocess,
            "run",
            side_effect=self._fake_runner(captured, {"answer": "记住了。", "memory_updates": [valid_update]}),
        ):
            result = app.answer_thread(thread["id"], self.owner)
        self.assertEqual(result["content"], "记住了。")
        listed = app.get_coach_memory(self.owner)["items"]
        self.assertEqual([item["key"] for item in listed], ["routine.monday_strength"])

        bad_thread = app.create_thread(app.ChatCreate(title="Bad memory"), self.owner)
        bad_message = app.post_message(bad_thread["id"], app.MessageCreate(content="普通问题"), self.owner)
        invalid_update = dict(valid_update, key="routine.invalid_source", source_message_ids=["assistant-not-user"])
        with patch.object(
            app.subprocess,
            "run",
            side_effect=self._fake_runner([], {"answer": "仍然回答。", "memory_updates": [invalid_update]}),
        ):
            bad_result = app.answer_thread(bad_thread["id"], self.owner)
        self.assertEqual(bad_result["content"], "仍然回答。")
        self.assertEqual(
            [item["key"] for item in app.get_coach_memory(self.owner)["items"]],
            ["routine.monday_strength"],
        )
        self.assertTrue(app.delete_coach_memory("routine.monday_strength", self.owner)["deleted"])


if __name__ == "__main__":
    unittest.main()
