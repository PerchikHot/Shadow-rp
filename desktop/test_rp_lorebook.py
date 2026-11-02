import unittest
from rp_lorebook import parse_lorebook, resolve_lorebook, MAX_CONTEXT


class LorebookTests(unittest.TestCase):
    def test_recent_word_triggers_and_no_false_matches(self):
        lore = "Портал => Ведёт в синий город\nМаяк => Выключен"
        self.assertEqual("Портал: Ведёт в синий город",
                         resolve_lorebook(lore, [{"role": "user", "content": "ПОРТАЛ!"}]))
        self.assertEqual("", resolve_lorebook(lore, [{"role": "user", "content": "Портальный"}]))
        self.assertEqual("", resolve_lorebook(lore, [{"role": "user", "content": "Маяк"}] +
                         [{"role": "assistant", "content": "тишина"}] * 8))

    def test_model_replies_cannot_self_activate_lore(self):
        lore = "Портал => Скрытый проход\nМаяк => Потух"
        self.assertEqual("", resolve_lorebook(lore, [
            {"role": "assistant", "content": "Портал находится у реки"}
        ]))
        self.assertEqual("Маяк: Потух", resolve_lorebook(lore, [
            {"role": "assistant", "content": "Портал находится у реки"},
            {"role": "user", "content": "Посмотрим на маяк"}
        ]))
        self.assertEqual("", resolve_lorebook(lore, [
            {"role": "user", "content": "Маяк"},
            *[{"role": "assistant", "content": "Маяк"} for _ in range(8)]
        ]))

    def test_invalid_and_ambiguous_rules(self):
        for raw in ("без разделителя", "А => текст",
                    "Портал => факт\nпортал => другой",
                    "Мир => " + "а" * 301, "x" * 8001):
            with self.assertRaises(ValueError):
                parse_lorebook(raw)

    def test_context_budget(self):
        lore = "\n".join(f"ключ{i} => " + "ф" * 200 for i in range(1, 31))
        recent = [{"role": "user", "content": " ".join(f"ключ{i}" for i in range(1, 31))}]
        result = resolve_lorebook(lore, recent)
        self.assertLessEqual(len(result), MAX_CONTEXT)
        self.assertIn("ключ1:", result)
        self.assertEqual(30, len(parse_lorebook(lore)))


if __name__ == "__main__":
    unittest.main()
