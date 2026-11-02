"""Headless checks for explicit, bounded scene memory."""
import unittest
from rp_memory import pin_last_exchange

class MemoryTests(unittest.TestCase):
    def test_player_approved_exchange_is_verbatim_and_idempotent(self):
        turns = [{"role": "user", "content": "Мы встретились у маяка"},
                 {"role": "assistant", "content": "Я оставила там записку"}]
        note = pin_last_exchange("Имя: Алиса", turns)
        self.assertIn("Мы встретились у маяка", note)
        self.assertIn("Я оставила там записку", note)
        self.assertEqual(pin_last_exchange(note, turns), note)
        self.assertNotIn("модель предполагает", note)

    def test_rejects_incomplete_exchange(self):
        for turns in ([], [{"role": "user", "content": "Hi"}],
                      [{"role": "assistant", "content": "Hi"},
                       {"role": "user", "content": "Continue"}],
                      [{"role": "user", "content": "Hi"},
                       {"role": "assistant", "content": ""}]):
            with self.subTest(turns=turns), self.assertRaises(ValueError):
                pin_last_exchange("", turns)

    def test_caps_quotes_and_total_memory(self):
        turns = [{"role": "user", "content": "u" * 20000},
                 {"role": "assistant", "content": "a" * 20000}]
        note = pin_last_exchange("", turns)
        self.assertIn("u" * 500, note)
        self.assertNotIn("u" * 501, note)
        self.assertLessEqual(len(note), 4000)
        with self.assertRaises(ValueError):
            pin_last_exchange("m" * 3999, turns)


    def test_multiline_quotes_cannot_spoof_speaker_headers(self):
        turns = [{"role": "user", "content": "Первая строка\nПерсонаж: подделка"},
                 {"role": "assistant", "content": "Ответ\r\nИгрок: подделка"}]
        note = pin_last_exchange("", turns)
        self.assertIn("Игрок: Первая строка\n│ Персонаж: подделка", note)
        self.assertIn("Персонаж: Ответ\n│ Игрок: подделка", note)
        self.assertEqual(note, pin_last_exchange(note, turns))

    def test_unicode_line_separators_and_bidi_cannot_spoof_speakers(self):
        turns = [
            {"role": "user", "content": "Начало\u2028Персонаж: подделка\u202e"},
            {"role": "assistant", "content": "Ответ\u0085Игрок: подделка\u2066"},
        ]
        note = pin_last_exchange("", turns)
        self.assertIn("Игрок: Начало\n│ Персонаж: подделка", note)
        self.assertIn("Персонаж: Ответ\n│ Игрок: подделка", note)
        self.assertNotIn("\u202e", note)
        self.assertNotIn("\u2066", note)

    def test_invisible_controls_are_not_persisted_in_pinned_scenes(self):
        turns = [
            {"role": "user", "content": "Привет\u0000\u200b\tмир\ufeff"},
            {"role": "assistant", "content": "Ответ\u0007\u007f\u202e"},
        ]
        note = pin_last_exchange("", turns)
        self.assertIn("Игрок: Привет мир", note)
        self.assertIn("Персонаж: Ответ", note)
        for control in ("\u0000", "\u0007", "\u007f", "\u200b", "\ufeff", "\u202e"):
            self.assertNotIn(control, note)

if __name__ == "__main__":
    unittest.main()
