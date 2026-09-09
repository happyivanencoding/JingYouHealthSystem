from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

import model_transport  # noqa: E402


class ModelTransportTests(unittest.TestCase):
    def test_transport_workspace_is_neutral_and_disables_model_tools(self) -> None:
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"LOCALAPPDATA": temp}, clear=False):
            workspace = model_transport._transport_workspace()
            self.assertTrue(str(workspace).startswith(temp))
            self.assertNotIn(str(ROOT).lower(), str(workspace).lower())
            config = (workspace / ".codex" / "config.toml").read_text(encoding="utf-8")
            for expected in (
                'web_search = "disabled"',
                "project_doc_max_bytes = 0",
                "project_doc_fallback_filenames = []",
                "project_root_markers = []",
                "memories = false",
                "apps = false",
                "browser_use = false",
                "computer_use = false",
                "multi_agent = false",
                "plugins = false",
                "shell_tool = false",
                "unified_exec = false",
                "skill_search = false",
                "skip_host_skill_discovery = true",
                "hooks = false",
                "request_permissions_tool = false",
            ):
                self.assertIn(expected, config)
            for name in ("AGENTS.md", "CLAUDE.md", "PROJECT_RULES.md", "DEEP_CONTEXT_HANDOFF.md"):
                self.assertFalse((workspace / name).exists())

    def test_generate_text_keeps_acp_details_inside_adapter(self) -> None:
        captured: dict[str, object] = {}

        def fake_run(command, **kwargs):
            captured["command"] = command
            captured["cwd"] = kwargs["cwd"]
            captured["prompt"] = kwargs["input"].decode("utf-8")
            envelope = {
                "text": "model answer",
                "audit": {
                    "event_types": ["agent_message_chunk", "completed", "usage_update"],
                    "tool_event_count": 0,
                    "permission_interaction_count": 0,
                },
            }
            return SimpleNamespace(returncode=0, stdout=json.dumps(envelope).encode("utf-8"), stderr=b"")

        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"LOCALAPPDATA": temp}, clear=False), patch.object(
            model_transport.subprocess, "run", side_effect=fake_run
        ):
            result = model_transport.generate_text(prompt="payload only", model="gpt-test", reasoning="low", timeout_seconds=30)

        command = captured["command"]
        self.assertIn("-Cwd", command)
        self.assertNotIn("-Mode", command)
        self.assertNotIn("-PromptPath", command)
        self.assertNotIn("-OutputPath", command)
        self.assertNotIn(str(ROOT), str(captured["cwd"]))
        self.assertTrue(str(captured["prompt"]).startswith("JINGYOU MODEL TRANSPORT ONLY."))
        self.assertTrue(str(captured["prompt"]).endswith("payload only"))
        self.assertEqual(result.text, "model answer")
        self.assertTrue(result.metadata["transport_only"])
        self.assertEqual(result.metadata["tool_event_count"], 0)
        self.assertEqual(result.metadata["permission_interaction_count"], 0)

    def test_generate_text_preserves_utf8_model_text(self) -> None:
        envelope = {
            "text": "中文正常",
            "audit": {"tool_event_count": 0, "permission_interaction_count": 0},
        }
        completed = SimpleNamespace(
            returncode=0,
            stdout=json.dumps(envelope, ensure_ascii=False).encode("utf-8"),
            stderr=b"",
        )
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"LOCALAPPDATA": temp}, clear=False), patch.object(
            model_transport.subprocess, "run", return_value=completed
        ):
            result = model_transport.generate_text(prompt="请只返回中文", model="gpt-test", reasoning="low", timeout_seconds=30)
        self.assertEqual(result.text, "中文正常")

    def test_generate_text_rejects_non_utf8_stdout(self) -> None:
        completed = SimpleNamespace(returncode=0, stdout=b'{"text":"\xff"}', stderr=b"")
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"LOCALAPPDATA": temp}, clear=False), patch.object(
            model_transport.subprocess, "run", return_value=completed
        ):
            with self.assertRaisesRegex(model_transport.ModelTransportError, "non-UTF-8 stdout"):
                model_transport.generate_text(prompt="payload only", model="gpt-test", reasoning="low", timeout_seconds=30)


if __name__ == "__main__":
    unittest.main()
