from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "server"), str(ROOT / "src")]

import auth  # noqa: E402
from coach_memory import (  # noqa: E402
    MemoryCapacityError,
    MemoryUpdate,
    apply_memory_updates,
    forget_memory_item,
    list_memory_items,
    memory_context,
)
from health_store import connect  # noqa: E402


class CoachMemoryTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.old_root = auth.ROOT
        auth.ROOT = self.root
        self.owner = auth.UserContext("usr_owner_memory", "Owner", "OWNER", "owner@example.test")
        self.member = auth.UserContext("usr_member_memory", "Member", "MEMBER", "member@example.test")
        self._seed_messages(self.owner)
        self._seed_messages(self.member)

    def tearDown(self) -> None:
        auth.ROOT = self.old_root
        self.tempdir.cleanup()

    def _seed_messages(self, user: auth.UserContext) -> None:
        con = connect(user.health_db)
        prefix = user.user_id
        try:
            con.executemany(
                "INSERT INTO chat_threads(id,title,created_at,updated_at) VALUES(?,?,?,?)",
                [
                    (f"{prefix}-current-thread", "Current", "2026-09-06T10:00:00+00:00", "2026-09-06T10:00:00+00:00"),
                    (f"{prefix}-old-thread", "Old", "2026-09-05T10:00:00+00:00", "2026-09-05T10:00:00+00:00"),
                ],
            )
            con.executemany(
                "INSERT INTO chat_messages(id,thread_id,role,content,created_at,status) VALUES(?,?,?,?,?,?)",
                [
                    (f"{prefix}-current", f"{prefix}-current-thread", "user", "I want a calmer training routine", "2026-09-06T10:01:00+00:00", "complete"),
                    (f"{prefix}-old", f"{prefix}-old-thread", "user", "I prefer short morning runs", "2026-09-05T10:01:00+00:00", "complete"),
                    (f"{prefix}-assistant", f"{prefix}-old-thread", "assistant", "You prefer short morning runs.", "2026-09-05T10:02:00+00:00", "complete"),
                ],
            )
            con.commit()
        finally:
            con.close()

    def _update(
        self,
        user: auth.UserContext,
        *,
        key: str,
        text: str,
        current: str | None = None,
        source_ids: list[str] | None = None,
        category: str = "goals",
        confidence: str = "user_stated",
    ) -> dict:
        prefix = user.user_id
        current_id = current or f"{prefix}-current"
        ids = source_ids or [current_id]
        return apply_memory_updates(
            user,
            [MemoryUpdate(action="upsert", key=key, category=category, text=text, confidence=confidence, source_message_ids=ids)],
            f"{prefix}-current-thread",
            current_id,
        )

    def _insert_thread_messages(self, user: auth.UserContext, thread_id: str, messages: list[tuple[str, str, str, str]]) -> None:
        con = connect(user.health_db)
        try:
            con.execute(
                "INSERT INTO chat_threads(id,title,created_at,updated_at) VALUES(?,?,?,?)",
                (thread_id, thread_id, "2026-09-01T00:00:00+00:00", "2026-09-01T00:00:00+00:00"),
            )
            con.executemany(
                "INSERT INTO chat_messages(id,thread_id,role,content,created_at,status) VALUES(?,?,?,?,?,?)",
                [(message_id, thread_id, role, content, created_at, "complete") for message_id, role, content, created_at in messages],
            )
            con.commit()
        finally:
            con.close()

    def test_users_and_sources_are_isolated(self) -> None:
        self._update(self.owner, key="goal.training", text="Prefer gentle training")
        self.assertEqual(list_memory_items(self.owner)[0]["text"], "Prefer gentle training")
        self.assertEqual(list_memory_items(self.member), [])

        with self.assertRaises(ValueError):
            self._update(
                self.member,
                key="goal.leak",
                text="must fail",
                source_ids=[f"{self.owner.user_id}-current", f"{self.member.user_id}-current"],
            )

    def test_overwrite_delete_tombstone_and_reopen(self) -> None:
        self._update(self.owner, key="routine.morning", text="Short morning run", source_ids=[f"{self.owner.user_id}-current", f"{self.owner.user_id}-old"])
        self._update(self.owner, key="routine.morning", text="Short morning walk", current=f"{self.owner.user_id}-current")
        self.assertEqual(list_memory_items(self.owner)[0]["text"], "Short morning walk")

        self.assertTrue(forget_memory_item(self.owner, "routine.morning"))
        self.assertEqual(list_memory_items(self.owner), [])
        con = connect(self.owner.health_db)
        try:
            audit = con.execute(
                "SELECT action,source_kind,current_user_message_id FROM coach_memory_history WHERE key=? ORDER BY id DESC LIMIT 1",
                ("routine.morning",),
            ).fetchone()
            self.assertEqual(dict(audit), {"action": "delete", "source_kind": "user_action", "current_user_message_id": None})
        finally:
            con.close()
        context = memory_context(self.owner, "morning run", exclude_thread_id=f"{self.owner.user_id}-current-thread")
        self.assertIn("routine.morning", context["forgotten_keys"])
        self.assertEqual(context["items"], [])
        self.assertFalse(any("morning run" in row["content"] for row in context["related_history"]))

        self._update(self.owner, key="routine.morning", text="New morning preference")
        context = memory_context(self.owner, "morning", exclude_thread_id=f"{self.owner.user_id}-current-thread")
        self.assertNotIn("routine.morning", context["forgotten_keys"])
        self.assertEqual(context["items"][0]["text"], "New morning preference")

    def test_assistant_text_cannot_become_memory_fact(self) -> None:
        with self.assertRaises(ValueError):
            self._update(
                self.owner,
                key="preference.assistant_claim",
                text="assistant claim",
                source_ids=[f"{self.owner.user_id}-current", f"{self.owner.user_id}-assistant"],
            )
        self.assertEqual(memory_context(self.owner, "morning")["items"], [])

    def test_related_history_is_cross_thread_and_user_scoped(self) -> None:
        owner_context = memory_context(self.owner, "morning runs", exclude_thread_id=f"{self.owner.user_id}-current-thread")
        self.assertTrue(owner_context["related_history"])
        self.assertTrue(all(row["thread_id"] != f"{self.owner.user_id}-current-thread" for row in owner_context["related_history"]))
        self.assertTrue(any(row["role"] == "user" for row in owner_context["related_history"]))

        member_context = memory_context(self.member, "morning runs")
        self.assertFalse(any(self.owner.user_id in row["thread_id"] for row in member_context["related_history"]))

    def test_unicode_language_overlap_beats_recency(self) -> None:
        prefix = self.owner.user_id
        self._insert_thread_messages(
            self.owner,
            f"{prefix}-fr-relevant",
            [(f"{prefix}-fr-relevant-msg", "user", "Je préfère une récupération douce", "2026-09-01T01:00:00+00:00")],
        )
        self._insert_thread_messages(
            self.owner,
            f"{prefix}-fr-noise",
            [(f"{prefix}-fr-noise-msg", "user", "Le marché monte aujourd'hui", "2026-09-06T09:00:00+00:00")],
        )
        self._insert_thread_messages(
            self.owner,
            f"{prefix}-ar-relevant",
            [(f"{prefix}-ar-relevant-msg", "user", "أفضل روتين تعافي هادئ", "2026-09-01T02:00:00+00:00")],
        )
        self._insert_thread_messages(
            self.owner,
            f"{prefix}-ar-noise",
            [(f"{prefix}-ar-noise-msg", "user", "الطقس جميل اليوم", "2026-09-06T09:30:00+00:00")],
        )

        french = memory_context(self.owner, "préférence récupération", exclude_thread_id=f"{prefix}-current-thread")
        arabic = memory_context(self.owner, "روتين التعافي", exclude_thread_id=f"{prefix}-current-thread")
        self.assertEqual(french["related_history"][0]["content"], "Je préfère une récupération douce")
        self.assertEqual(arabic["related_history"][0]["content"], "أفضل روتين تعافي هادئ")

    def test_forget_after_cross_thread_overwrites_filters_all_historical_sources(self) -> None:
        prefix = self.owner.user_id
        thread_a = f"{prefix}-style-a"
        thread_b = f"{prefix}-style-b"
        message_a = f"{prefix}-style-a-msg"
        message_b = f"{prefix}-style-b-msg"
        self._insert_thread_messages(
            self.owner,
            thread_a,
            [(message_a, "user", "I prefer long answers", "2026-08-01T01:00:00+00:00")],
        )
        self._insert_thread_messages(
            self.owner,
            thread_b,
            [(message_b, "user", "I prefer short answers", "2026-09-05T01:00:00+00:00")],
        )
        apply_memory_updates(
            self.owner,
            [MemoryUpdate(action="upsert", key="response_style.length", category="response_style", text="Long answers", confidence="user_stated", source_message_ids=[message_a])],
            thread_a,
            message_a,
        )
        apply_memory_updates(
            self.owner,
            [MemoryUpdate(action="upsert", key="response_style.length", category="response_style", text="Short answers", confidence="user_stated", source_message_ids=[message_b])],
            thread_b,
            message_b,
        )
        self.assertTrue(forget_memory_item(self.owner, "response_style.length"))
        context = memory_context(self.owner, "answer length", exclude_thread_id=f"{prefix}-current-thread")
        self.assertIn("response_style.length", context["forgotten_keys"])
        self.assertFalse(any("answers" in row["content"] for row in context["related_history"]))

    def test_length_update_and_capacity_limits(self) -> None:
        with self.assertRaises(Exception):
            MemoryUpdate(action="upsert", key="Bad Key", category="goals", text="x", confidence="user_stated", source_message_ids=[f"{self.owner.user_id}-current"])
        with self.assertRaises(Exception):
            MemoryUpdate(action="upsert", key="goal.too_long", category="goals", text="x" * 501, confidence="user_stated", source_message_ids=[f"{self.owner.user_id}-current"])
        with self.assertRaises(ValueError):
            apply_memory_updates(
                self.owner,
                [
                    MemoryUpdate(action="upsert", key=f"goal.batch{i}", category="goals", text="x", confidence="user_stated", source_message_ids=[f"{self.owner.user_id}-current"])
                    for i in range(9)
                ],
                f"{self.owner.user_id}-current-thread",
                f"{self.owner.user_id}-current",
            )

        for start in range(0, 120, 8):
            updates = [
                MemoryUpdate(action="upsert", key=f"goal.capacity{i}", category="goals", text=str(i), confidence="tentative", source_message_ids=[f"{self.owner.user_id}-current"])
                for i in range(start, min(start + 8, 120))
            ]
            apply_memory_updates(self.owner, updates, f"{self.owner.user_id}-current-thread", f"{self.owner.user_id}-current")
        with self.assertRaises(MemoryCapacityError):
            self._update(self.owner, key="goal.overflow", text="no silent eviction")
        self.assertEqual(len(list_memory_items(self.owner)), 120)


if __name__ == "__main__":
    unittest.main()
