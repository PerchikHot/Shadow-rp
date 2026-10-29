"""Read-only Minecraft instance inspection tests."""
import tempfile
import unittest
import zipfile
from pathlib import Path

from shadow_launcher import inspect_instance, duplicate_mod_names


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

    def test_duplicate_mod_names_are_reported_without_deleting_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            base = root / "mods"
            versioned = base / "1.20.1"
            versioned.mkdir(parents=True)
            first = base / "Addon.jar"
            second = versioned / "addon.JAR"
            first.write_bytes(b"first")
            second.write_bytes(b"second")
            report = inspect_instance(root)
            self.assertEqual(report["duplicates"], ["addon.jar"])
            self.assertEqual(first.read_bytes(), b"first")
            self.assertEqual(second.read_bytes(), b"second")

    def test_distinct_mod_names_have_no_duplicate_warning(self):
        self.assertEqual(duplicate_mod_names(["a.jar", "1.20.1/b.jar"]), [])

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


    def test_non_zip_jar_warning_preserves_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / 'mods'
            folder.mkdir()
            broken = folder / 'download.jar'
            broken.write_bytes(b'<html>Not Found</html>')
            valid = folder / 'valid.jar'
            with zipfile.ZipFile(valid, 'w') as jar:
                jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0')
            report = inspect_instance(root)
            self.assertEqual(report['invalid_mods'], ['download.jar'])
            self.assertEqual(broken.read_bytes(), b'<html>Not Found</html>')
            self.assertTrue(zipfile.is_zipfile(valid))

    def test_truncated_pk_header_is_reported_as_corrupt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / 'mods'
            folder.mkdir()
            partial = folder / 'partial.jar'
            partial.write_bytes(b'PK\x03\x04unfinished')
            report = inspect_instance(root)
            self.assertEqual(report['invalid_mods'], ['partial.jar'])
            self.assertEqual(partial.read_bytes(), b'PK\x03\x04unfinished')

    def test_fabric_only_mod_is_flagged_but_dual_loader_is_not(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            fabric = mods / "fabric_only.jar"
            dual = mods / "dual.jar"
            with zipfile.ZipFile(fabric, "w") as jar:
                jar.writestr("fabric.mod.json", '{"id":"sample"}')
            with zipfile.ZipFile(dual, "w") as jar:
                jar.writestr("fabric.mod.json", '{"id":"sample"}')
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            report = inspect_instance(root)
            self.assertEqual(report["fabric_mods"], ["fabric_only.jar"])
            self.assertEqual(report["invalid_mods"], [])
            with zipfile.ZipFile(fabric) as jar:
                self.assertIn("fabric.mod.json", jar.namelist())

    def test_neoforge_only_metadata_is_flagged_without_touching_mods(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            neo = mods / "neoforge_only.jar"
            dual = mods / "dual_loader.jar"
            with zipfile.ZipFile(neo, "w") as jar:
                jar.writestr("META-INF/neoforge.mods.toml", 'modLoader="javafml"')
            with zipfile.ZipFile(dual, "w") as jar:
                jar.writestr("META-INF/neoforge.mods.toml", 'modLoader="javafml"')
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            original = neo.read_bytes()
            report = inspect_instance(root)
            self.assertEqual(report["neoforge_mods"], ["neoforge_only.jar"])
            self.assertEqual(report["invalid_mods"], [])
            self.assertEqual(neo.read_bytes(), original)

    def test_zero_byte_jar_warning_is_read_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "mods" / "1.20.1"
            folder.mkdir(parents=True)
            empty = folder / "unfinished.jar"
            empty.write_bytes(b"")
            valid = root / "mods" / "working.jar"
            valid.write_bytes(b"valid")
            report = inspect_instance(root)
            self.assertEqual(report["empty_mods"], ["1.20.1/unfinished.jar"])
            self.assertEqual(empty.read_bytes(), b"")
            self.assertEqual(valid.read_bytes(), b"valid")


if __name__ == "__main__":
    unittest.main()
