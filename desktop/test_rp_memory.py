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

if __name__ == "__main__":
    unittest.main()
