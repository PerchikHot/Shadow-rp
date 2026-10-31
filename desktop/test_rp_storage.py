"""Windows persistence tests: recovery, atomic save, and input limits."""
import tempfile
import unittest
from pathlib import Path
from rp_storage import load_data, save_data
from rp_backup import MAX_BYTES


def data(text):
    return {"characters": {"Алиса": {
        "description": "RP", "scenario": "Город", "memory": "Встреча",
        "messages": [{"role": "user", "content": text}]}}}


class StorageTests(unittest.TestCase):
    def test_round_trip_and_backup_previous_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "chats.json"
            save_data(path, data("first"))
            save_data(path, data("second"))
            current, recovered = load_data(path)
            self.assertFalse(recovered)
            self.assertEqual(current["characters"]["Алиса"]["messages"][0]["content"], "second")
            self.assertTrue(path.with_suffix(".bak").is_file())

    def test_recover_corrupted_main_without_overwriting_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "chats.json"
            save_data(path, data("original"))
            save_data(path, data("new"))
            path.write_text("{bad json", encoding="utf-8")
            restored, recovered = load_data(path)
            self.assertTrue(recovered)
            self.assertEqual(restored["characters"]["Алиса"]["messages"][0]["content"], "original")
            old_backup = path.with_suffix(".bak").read_bytes()
            save_data(path, data("repaired"))
            self.assertEqual(path.with_suffix(".bak").read_bytes(), old_backup)
            self.assertEqual(load_data(path)[0]["characters"]["Алиса"]["messages"][0]["content"], "repaired")

    def test_reject_invalid_save_without_touching_primary(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "chats.json"
            save_data(path, data("safe"))
            old = path.read_bytes()
            with self.assertRaises(ValueError):
                save_data(path, {"characters": {"bad": {"messages": [
                    {"role": "system", "content": "invalid"}]}}})
            self.assertEqual(path.read_bytes(), old)
            self.assertFalse(path.with_name("chats.json.tmp").exists())

    def test_oversized_primary_falls_back_to_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "chats.json"
            save_data(path, data("one"))
            save_data(path, data("two"))
            with path.open("wb") as stream:
                stream.truncate(MAX_BYTES + 1)
            restored, recovered = load_data(path)
            self.assertTrue(recovered)
            self.assertEqual(restored["characters"]["Алиса"]["messages"][0]["content"], "one")


if __name__ == "__main__":
    unittest.main()
