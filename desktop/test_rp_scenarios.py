import unittest
from rp_scenarios import SCENARIOS, scenario_by_index


class ScenarioTests(unittest.TestCase):
    def test_presets_are_portable_and_bounded(self):
        self.assertEqual(len(SCENARIOS), 6)
        for index, (name, text) in enumerate(SCENARIOS):
            self.assertEqual(scenario_by_index(index), (name, text))
            self.assertTrue(name.strip())
            self.assertTrue(60 < len(text) <= 4000)
            self.assertNotIn("API_KEY", text)

    def test_unknown_preset_does_not_change_state(self):
        for index in (-1, 6, None, True, "0"):
            with self.assertRaises(ValueError):
                scenario_by_index(index)


if __name__ == "__main__":
    unittest.main()
