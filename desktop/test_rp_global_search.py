"""Cross-character story search should preserve original histories."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from rp_chat import search_all_stories


class GlobalStorySearchTests(unittest.TestCase):
    def setUp(self):
        self.characters = {
            "Альфа": {"messages": [
                {"role": "user", "content": "Встречаемся у портала"},
                {"role": "assistant", "content": "Портал открыт\nИди за мной"},
            ]},
            "Бета": {"messages": [
                {"role": "assistant", "content": "Древний ПОРТАЛ запечатан"},
            ]},
        }

    def test_search_across_characters_and_case(self):
        hits = search_all_stories(self.characters, "портал")
        self.assertEqual([(name, index) for name, index, _, _ in hits],
                         [("Альфа", 0), ("Альфа", 1), ("Бета", 0)])

    def test_blank_query_does_not_show_private_history(self):
        self.assertEqual(search_all_stories(self.characters, "  "), [])

    def test_search_is_read_only_and_bounded(self):
        import copy
        before = copy.deepcopy(self.characters)
        self.assertEqual(len(search_all_stories(self.characters, "портал", limit=2)), 2)
        self.assertEqual(self.characters, before)

    def test_ignores_malformed_legacy_turns(self):
        self.characters["Альфа"]["messages"].append({"role": "user", "content": None})
        self.assertEqual(len(search_all_stories(self.characters, "портал")), 3)


if __name__ == "__main__":
    unittest.main()
