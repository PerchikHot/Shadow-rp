"""Headless tests for RP dialogue search and non-destructive rewind."""
import unittest
from rp_history import search_dialogue, rewind_to_user_turn, branch_from_user_turn
from rp_backup import validate_backup


def sample():
    return {"characters": {
        "Алиса": {"description": "Пилот", "scenario": "Космос",
                  "memory": "Старая сцена",
                  "messages": [
                      {"role": "user", "content": "Встреча у маяка"},
                      {"role": "assistant", "content": "Я помню маяк"},
                      {"role": "user", "content": "Отправляемся к звёздам"},
                      {"role": "assistant", "content": "Полетели!"}]},
        "Боб": {"description": "Друг", "messages": [
            {"role": "user", "content": "Не меняй меня"}]}}}


class HistoryTests(unittest.TestCase):
    def test_search_is_case_insensitive_recent_first_and_bounded(self):
        messages = sample()["characters"]["Алиса"]["messages"]
        self.assertEqual([x[0] for x in search_dialogue(messages, "МАЯК")], [1, 0])
        self.assertEqual([x[0] for x in search_dialogue(messages, "")], [3, 2, 1, 0])
        self.assertEqual(len(search_dialogue(messages, "", limit=2)), 2)
        self.assertEqual(search_dialogue(messages, "несуществующее"), [])

    def test_rewind_keeps_player_turn_and_other_characters(self):
        original = sample()
        updated = rewind_to_user_turn(original, "Алиса", 2)
        self.assertEqual(len(updated["characters"]["Алиса"]["messages"]), 3)
        self.assertEqual(updated["characters"]["Алиса"]["messages"][-1]["role"], "user")
        self.assertEqual(updated["characters"]["Алиса"]["memory"], "Старая сцена")
        self.assertEqual(updated["characters"]["Боб"], validate_backup(original)["characters"]["Боб"])
        self.assertEqual(len(original["characters"]["Алиса"]["messages"]), 4)

    def test_rejects_assistant_turn_missing_character_and_last_turn(self):
        for character, index in [("Алиса", 1), ("Алиса", -1),
                                 ("Алиса", 4), ("Неизвестный", 0),
                                 ("Боб", 0), ("Алиса", True)]:
            with self.subTest(character=character, index=index):
                with self.assertRaises(ValueError):
                    rewind_to_user_turn(sample(), character, index)

    def test_branch_keeps_original_and_clears_future_pinned_memory(self):
        original = sample()
        result, name = branch_from_user_turn(original, "Алиса", 2)
        self.assertEqual(name, "Алиса (ветка 2)")
        self.assertEqual(len(result["characters"][name]["messages"]), 3)
        self.assertEqual(result["characters"][name]["messages"][-1]["role"], "user")
        self.assertEqual(result["characters"][name]["scenario"], "Космос")
        self.assertEqual(result["characters"][name]["memory"], "")
        self.assertEqual(result["characters"]["Алиса"]["memory"], "Старая сцена")
        self.assertEqual(len(result["characters"]["Алиса"]["messages"]), 4)
        self.assertEqual(original, sample())
        self.assertEqual(validate_backup(result), result)

    def test_branch_names_unique_and_invalid_turns_rejected(self):
        data = sample()
        first, first_name = branch_from_user_turn(data, "Алиса", 0)
        second, second_name = branch_from_user_turn(first, "Алиса", 2)
        self.assertNotEqual(first_name, second_name)
        self.assertIn("Алиса (ветка 3)", second["characters"])
        for character, index in (("Алиса", 1), ("Алиса", -1),
                                 ("Алиса", True), ("Боб", 0),
                                 ("Неизвестный", 0)):
            with self.subTest(character=character, index=index):
                with self.assertRaises(ValueError):
                    branch_from_user_turn(data, character, index)

    def test_rejects_invalid_search_limits_and_input(self):
        for query, limit in [("x" * 201, 50), ("a", 0), ("a", 101)]:
            with self.assertRaises(ValueError):
                search_dialogue([], query, limit)
        with self.assertRaises(ValueError):
            search_dialogue(None, "test")


if __name__ == "__main__":
    unittest.main()
