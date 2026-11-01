"""Headless regression tests for the Windows character library search."""
import unittest
from rp_chat import filter_character_names


class CharacterSearchTests(unittest.TestCase):
    def test_casefold_and_whitespace(self):
        names = ["Алиса", "Эрза", "ALPHA", "Альфа"]
        self.assertEqual(filter_character_names(names, "  альф  "), ["Альфа"])
        self.assertEqual(filter_character_names(names, "alpha"), ["ALPHA"])
        self.assertEqual(filter_character_names(names, "ЭР"), ["Эрза"])

    def test_empty_query_preserves_order_without_mutating(self):
        names = ["Ямато", "Риас"]
        self.assertEqual(filter_character_names(names, " "), names)
        self.assertEqual(names, ["Ямато", "Риас"])
        self.assertEqual(filter_character_names(names, "нет"), [])

    def test_invalid_query_is_rejected(self):
        with self.assertRaises(ValueError):
            filter_character_names(["Алиса"], None)


if __name__ == "__main__":
    unittest.main()
