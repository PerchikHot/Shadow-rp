"""Read-only Minecraft instance inspection tests."""
import tempfile
import unittest
from pathlib import Path

from shadow_launcher import inspect_instance


class InstanceTests(unittest.TestCase):
    def test_missing_directory_is_not_created(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / ".minecraft"
            result = inspect_instance(target)
            self.assertFalse(result["exists"])
            self.assertFalse(target.exists())

    def test_detects_forge_1201_and_jar_mods(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "versions" / "1.20.1-forge-47.3.0").mkdir(parents=True)
            (root / "mods").mkdir()
            (root / "mods" / "sample.jar").write_bytes(b"test")
            (root / "mods" / "notes.txt").write_text("not a mod")
            result = inspect_instance(root)
            self.assertTrue(result["forge"])
            self.assertEqual(result["mods"], ["sample.jar"])
            self.assertTrue((root / "mods" / "notes.txt").exists())

    def test_versioned_mods_are_listed_without_editing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "mods" / "1.20.1"
            folder.mkdir(parents=True)
            jar = folder / "addon.jar"
            jar.write_bytes(b"original")
            (folder / "readme.txt").write_text("notes")
            result = inspect_instance(root)
            self.assertEqual(result["mods"], ["1.20.1/addon.jar"])
            self.assertEqual(jar.read_bytes(), b"original")

    def test_similar_versions_do_not_trigger_false_forge_detection(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "versions" / "1.20.10-forge-47.3.0").mkdir(parents=True)
            (root / "versions" / "1.20.1.1-forge-47.3.0").mkdir(parents=True)
            self.assertFalse(inspect_instance(root)["forge"])

    def test_other_versions_do_not_count_as_forge_1201(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "versions" / "1.19.2-forge-43.2.0").mkdir(parents=True)
            self.assertFalse(inspect_instance(root)["forge"])


if __name__ == "__main__":
    unittest.main()
