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

    def test_valid_reply_still_works(self):
        self.assertEqual(
            _extract_text({"choices": [{"message": {
                "role": "assistant", "content": "Привет!"
            }, "finish_reason": "stop"}]}),
            "Привет!",
        )


if __name__ == "__main__":
    unittest.main()
