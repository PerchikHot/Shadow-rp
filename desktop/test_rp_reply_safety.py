"""Desktop reply race regressions: never overwrite edited or imported RP scenes."""
import unittest
from rp_reply_safety import can_apply_reply, card_context


class ReplySafetyTests(unittest.TestCase):
    def setUp(self):
        self.card = {"description": "Детектив", "memory": "Встреча"}
        self.snapshot = [{"role": "user", "content": "Продолжай"}]
        self.context = card_context(self.card)

    def test_unchanged_pending_turn_accepts_reply(self):
        self.assertTrue(can_apply_reply(self.snapshot, list(self.snapshot),
                                        self.context, card_context(self.card)))

    def test_imported_or_replaced_history_discards_stale_reply(self):
        for current in (None, [], [{"role": "user", "content": "Новая история"}],
                        self.snapshot + [{"role": "assistant", "content": "Другой ответ"}]):
            self.assertFalse(can_apply_reply(self.snapshot, current,
                                             self.context, card_context(self.card)))

    def test_edited_character_context_discards_stale_reply(self):
        edited = dict(self.card, memory="Сцена изменена")
        self.assertFalse(can_apply_reply(self.snapshot, list(self.snapshot),
                                         self.context, card_context(edited)))
        self.assertFalse(can_apply_reply([{"role": "assistant", "content": "Привет"}],
                                         [{"role": "assistant", "content": "Привет"}],
                                         self.context, self.context))


if __name__ == "__main__":
    unittest.main()
