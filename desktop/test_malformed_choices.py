"""Regression: malformed HTTP 200 model replies must not crash the chat worker."""
import unittest
from rp_transport import _extract_text


class MalformedChoicesTests(unittest.TestCase):
    def test_bad_choices_are_user_facing_errors(self):
        for choices in (None, {}, "", [], [None], [7], ["text"], [{}],
                        [{"message": None}], [{"message": []}],
                        [{"message": {}}]):
            with self.subTest(choices=choices):
                with self.assertRaises(ValueError):
                    _extract_text({"choices": choices})

    def test_legacy_function_calls_are_not_saved_as_character_dialogue(self):
        for function_call in ({"name": "run", "arguments": "{}"}, {}):
            with self.subTest(function_call=function_call):
                with self.assertRaises(ValueError):
                    _extract_text({"choices": [{"finish_reason": "stop", "message": {
                        "role": "assistant", "content": "Calling a function",
                        "function_call": function_call}}]})

    def test_invalid_unicode_and_control_characters_are_not_persisted(self):
        for content in ("Сцена\\x00", "Текст\\x1b[31m", "Текст\\x7f", "Сцена\\ud800"):
            with self.subTest(content=repr(content)), self.assertRaises(ValueError):
                _extract_text({"choices": [{"message": {"role": "assistant",
                    "content": content}, "finish_reason": "stop"}]})

    def test_multiline_dialogue_remains_supported(self):
        dialogue = "Первая строка\\n\\tВторая строка"
        self.assertEqual(_extract_text({"choices": [{"message": {
            "role": "assistant", "content": dialogue}, "finish_reason": "stop"}]}), dialogue)

    def test_valid_reply_still_works(self):
        self.assertEqual(
            _extract_text({"choices": [{"message": {
                "role": "assistant", "content": "Привет!"
            }, "finish_reason": "stop"}]}),
            "Привет!",
        )


if __name__ == "__main__":
    unittest.main()
