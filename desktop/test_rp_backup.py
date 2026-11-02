import unittest
from rp_backup import (encode_backup, decode_backup, merge_backups,
                       merge_backups_as_copies, export_character)


class BackupTests(unittest.TestCase):
    def test_rejects_duplicate_json_keys_before_import(self):
        # A repeated key must never silently discard an earlier history.
        for raw in (
            b'{"characters":{"Alice":{"messages":[]},"Alice":{"messages":[]}}}',
            b'{"characters":{"Alice":{"messages":[{"role":"user","content":"one","content":"two"}]}}}',
            b'{"characters":{},"characters":{"Bob":{}}}',
        ):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                decode_backup(raw)

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

    def test_portable_names_reject_control_characters_on_both_platforms(self):
        # Android RpBackup.checkName rejects these before writing preferences.
        # Windows must not export an archive Android will refuse to import.
        for name in ("Алиса" + chr(10) + "Бета", "Герой" + chr(0),
                     "Персонаж" + chr(9) + "2", "Ник" + chr(127)):
            with self.subTest(name=repr(name)):
                payload = {"characters": {name: {"messages": []}}}
                with self.assertRaises(ValueError):
                    encode_backup(payload)
                import json
                with self.assertRaises(ValueError):
                    decode_backup(json.dumps(payload).encode("utf-8"))

    def test_reject_bad_optional_fields(self):
        with self.assertRaises(ValueError):
            encode_backup({"characters": {"A": {"appearance": ["invalid"]}}})

    def test_case_only_name_collision_requires_explicit_copy_import(self):
        existing = {"characters": {"Алиса": {"messages": [
            {"role": "user", "content": "Оригинальная история"}]}}}
        incoming = {"characters": {"АЛИСА": {"messages": [
            {"role": "assistant", "content": "Другая история"}]}}}
        with self.assertRaises(ValueError):
            merge_backups(existing, incoming)
        self.assertIn("Алиса", existing["characters"])
        copies = merge_backups_as_copies(existing, incoming)
        self.assertEqual(len(copies["characters"]), 2)
        self.assertIn("Оригинальная история",
            copies["characters"]["Алиса"]["messages"][0]["content"])

    def test_import_rejects_case_only_duplicates_inside_source_archive(self):
        existing = {"characters": {"Боб": {"messages": []}}}
        incoming = {"characters": {
            "Алиса": {"messages": [{"role": "user", "content": "first"}]},
            "АЛИСА": {"messages": [{"role": "user", "content": "second"}]},
        }}
        for importer in (merge_backups, merge_backups_as_copies):
            with self.subTest(importer=importer.__name__), self.assertRaises(ValueError):
                importer(existing, incoming)
        self.assertEqual(set(existing["characters"]), {"Боб"})

    def test_windows_export_rejects_case_only_names_before_android_transfer(self):
        ambiguous = {"characters": {
            "Алиса": {"messages": []}, "АЛИСА": {"messages": []},
        }}
        with self.assertRaises(ValueError):
            encode_backup(ambiguous)
        import json
        with self.assertRaises(ValueError):
            decode_backup(json.dumps(ambiguous, ensure_ascii=False).encode("utf-8"))

    def test_merge_preserves_unrelated_characters(self):
        old = {"characters": {
            "Алиса": {"description": "old", "messages": []},
            "Бета": {"description": "keep", "messages": []}}}
        incoming = {"characters": {
            "Алиса": {"description": "new", "messages": [
                {"role": "assistant", "content": "Hello"}]},
            "Гамма": {"description": "added", "messages": []}}}
        result = merge_backups(old, incoming)
        self.assertEqual(set(result["characters"]), {"Алиса", "Бета", "Гамма"})
        self.assertEqual(result["characters"]["Алиса"]["description"], "new")
        self.assertEqual(result["characters"]["Бета"]["description"], "keep")
        self.assertEqual(len(result["characters"]["Алиса"]["messages"]), 1)
        self.assertEqual(old["characters"]["Алиса"]["description"], "old")

    def test_merge_rejects_oversized_catalog(self):
        old = {"characters": {str(i): {"messages": []} for i in range(150)}}
        incoming = {"characters": {"new" + str(i): {"messages": []} for i in range(60)}}
        with self.assertRaises(ValueError):
            merge_backups(old, incoming)

    def test_import_as_copies_preserves_existing_history(self):
        old = {"characters": {"Алиса": {"messages": [
            {"role": "user", "content": "Личная история"}]}}}
        incoming = {"characters": {
            "Алиса": {"messages": [{"role": "assistant", "content": "Новая история"}]},
            "Алиса (импорт 2)": {"messages": []},
        }}
        merged = merge_backups_as_copies(old, incoming)
        self.assertEqual(set(merged["characters"]),
                         {"Алиса", "Алиса (импорт 2)", "Алиса (импорт 3)"})
        self.assertEqual(merged["characters"]["Алиса"]["messages"][0]["content"],
                         "Личная история")
        self.assertEqual(merged["characters"]["Алиса (импорт 3)"]["messages"][0]["content"],
                         "Новая история")
        self.assertEqual(old["characters"]["Алиса"]["messages"][0]["content"],
                         "Личная история")
        self.assertEqual(decode_backup(encode_backup(merged)), merged)

    def test_import_as_copies_truncates_long_names_safely(self):
        name = "Я" * 100
        old = {"characters": {name: {"messages": []}}}
        new = {"characters": {name: {"messages": []}}}
        merged = merge_backups_as_copies(old, new)
        self.assertEqual(len(merged["characters"]), 2)
        self.assertTrue(all(len(n) <= 100 for n in merged["characters"]))

    def test_import_as_copies_rejects_too_many_characters(self):
        old = {"characters": {str(i): {"messages": []} for i in range(200)}}
        with self.assertRaises(ValueError):
            merge_backups_as_copies(old, {"characters": {"0": {"messages": []}}})

    def test_export_only_selected_character_preserves_portable_fields(self):
        data = {"characters": {
            "Алиса": {"description": "Пилот", "appearance": "Синий плащ",
                      "scenario": "Космос", "memory": "Маяк",
                      "greeting": "Привет", "protocol": "Не ломай характер",
                      "messages": [{"role": "user", "content": "В путь"}]},
            "Боб": {"description": "Личная переписка",
                    "messages": [{"role": "user", "content": "Секрет"}]}
        }}
        raw = export_character(data, "Алиса")
        restored = decode_backup(raw)
        self.assertEqual(set(restored["characters"]), {"Алиса"})
        self.assertEqual(restored["characters"]["Алиса"],
                         decode_backup(encode_backup(data))["characters"]["Алиса"])
        self.assertNotIn("Секрет", raw.decode("utf-8"))
        # Existing chats on the destination survive a one-character import.
        merged = merge_backups({"characters": {"Боб": data["characters"]["Боб"]}},
                               restored)
        self.assertEqual(set(merged["characters"]), {"Алиса", "Боб"})
        self.assertEqual(len(data["characters"]), 2)

    def test_export_missing_character_never_produces_archive(self):
        for invalid in ("Неизвестный", "", None):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    export_character({"characters": {"Алиса": {"messages": []}}},
                                     invalid)

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
