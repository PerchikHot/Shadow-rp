import unittest
from rp_backup import encode_backup, decode_backup


class BackupTests(unittest.TestCase):
    def test_round_trip(self):
        data = {"characters": {"Алиса": {"description": "Героиня", "scenario": "Лес", "memory": "Встреча", "messages": [{"role": "user", "content": "Привет"}, {"role": "assistant", "content": "Здравствуй"}]}}}
        self.assertEqual(decode_backup(encode_backup(data)), data)

    def test_android_character_fields_survive_windows_round_trip(self):
        data = {"characters": {"Алиса": {
            "description": "Детектив", "scenario": "Поезд", "memory": "Встреча",
            "appearance": "Синий плащ", "greeting": "Привет",
            "protocol": "Сохраняй характер",
            "messages": [{"role": "user", "content": "Здравствуйте"}]}}}
        self.assertEqual(decode_backup(encode_backup(data)), data)

    def test_old_windows_archive_stays_compatible(self):
        old = {"characters": {"Алиса": {"description": "Героиня",
            "scenario": "", "memory": "", "messages": []}}}
        self.assertEqual(decode_backup(encode_backup(old)), old)

    def test_reject_bad_optional_fields(self):
        with self.assertRaises(ValueError):
            encode_backup({"characters": {"A": {"appearance": ["invalid"]}}})

    def test_reject_bad_role(self):
        with self.assertRaises(ValueError):
            encode_backup({"characters": {"A": {"messages": [{"role": "system", "content": "hack"}]}}})

    def test_reject_large_file(self):
        with self.assertRaises(ValueError):
            decode_backup(b"x" * (8 * 1024 * 1024 + 1))

    def test_reject_bad_structure(self):
        with self.assertRaises(ValueError):
            decode_backup(b'{"characters": []}')


if __name__ == "__main__":
    unittest.main()
