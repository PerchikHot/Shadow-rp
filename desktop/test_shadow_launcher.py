"""Read-only Minecraft instance inspection tests."""
import tempfile
import warnings
import unittest
import zipfile
from pathlib import Path

from shadow_launcher import save_report_text, inspect_instance, format_instance_report, duplicate_mod_names, forge_1201_versions, suspicious_jar_compression, ambiguous_jar_members


class UnreadableModTests(unittest.TestCase):
    def test_one_unreadable_jar_does_not_hide_other_mods(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            for name in ("working.jar", "blocked.jar"):
                with zipfile.ZipFile(mods / name, "w") as jar:
                    jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            original = zipfile.ZipFile

            def open_jar(path, *args, **kwargs):
                if str(path).endswith("blocked.jar"):
                    raise PermissionError("simulated access denied")
                return original(path, *args, **kwargs)

            with patch("shadow_launcher.zipfile.ZipFile", side_effect=open_jar):
                report = inspect_instance(root)
            self.assertEqual(report["unreadable_mods"], ["blocked.jar"])
            self.assertNotIn("working.jar", report["invalid_mods"])
            self.assertEqual(len(report["mods"]), 2)
            self.assertIn("Нет доступа к JAR", format_instance_report(report))


class PartialModSizeReportTests(unittest.TestCase):
    def test_unreadable_jar_size_is_explicitly_marked_incomplete(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            with zipfile.ZipFile(mods / "good.jar", "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            with zipfile.ZipFile(mods / "locked.jar", "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            from unittest.mock import patch
            original_stat = Path.stat

            def stat_with_failure(path, *args, **kwargs):
                if path.name == "locked.jar":
                    raise PermissionError("locked")
                return original_stat(path, *args, **kwargs)

            with patch.object(Path, "stat", stat_with_failure):
                report = inspect_instance(root)
            output = format_instance_report(report)
            self.assertIn("неполный подсчёт", output)
            self.assertIn("locked.jar", output)


class ReportExportTests(unittest.TestCase):
    def test_export_is_utf8_local_and_does_not_touch_mod_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            jar = mods / "тест.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            before = jar.read_bytes()
            report = inspect_instance(root)
            target = root / "diagnostics.txt"
            save_report_text(target, report)
            self.assertEqual(target.read_text(encoding="utf-8").strip(),
                             format_instance_report(report))
            self.assertEqual(jar.read_bytes(), before)
            with self.assertRaises(FileExistsError):
                save_report_text(target, report)
            self.assertEqual(target.read_text(encoding="utf-8").strip(),
                             format_instance_report(report))


class ActiveModSizeTests(unittest.TestCase):
    def test_reports_total_active_jar_bytes_without_reading_or_changing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            jar_path = mods / "sample.jar"
            with zipfile.ZipFile(jar_path, "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            disabled = mods / "old.jar.disabled"
            disabled.write_bytes(b"unused backup")
            before = jar_path.read_bytes()
            report = inspect_instance(root)
            self.assertEqual(report["mods_bytes"], len(before))
            self.assertIn("Размер активных JAR:", format_instance_report(report))
            self.assertIn(str(len(before)), format_instance_report(report))
            self.assertEqual(jar_path.read_bytes(), before)
            self.assertEqual(disabled.read_bytes(), b"unused backup")


class InaccessibleModSizeTests(unittest.TestCase):
    def test_stat_failure_isolated_from_other_mods(self):
        from unittest.mock import patch
        from shadow_launcher import scan_mod_sizes
        with tempfile.TemporaryDirectory() as tmp:
            mods = Path(tmp)
            (mods / "working.jar").write_bytes(b"valid")
            (mods / "empty.jar").write_bytes(b"")
            (mods / "blocked.jar").write_bytes(b"private")
            original_stat = Path.stat

            def stat_with_blocked_file(path, *args, **kwargs):
                if path.name == "blocked.jar":
                    raise PermissionError("simulated locked JAR")
                return original_stat(path, *args, **kwargs)

            with patch.object(Path, "stat", stat_with_blocked_file):
                total, empty, unreadable = scan_mod_sizes(
                    mods, ["working.jar", "blocked.jar", "empty.jar"])
            self.assertEqual(total, 5)
            self.assertEqual(empty, ["empty.jar"])
            self.assertEqual(unreadable, ["blocked.jar"])
            self.assertEqual((mods / "blocked.jar").read_bytes(), b"private")


class ForgeDescriptorCountTests(unittest.TestCase):
    def test_reports_forge_metadata_count_without_claiming_compatibility(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            with zipfile.ZipFile(mods / "forge.jar", "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            with zipfile.ZipFile(mods / "fabric.jar", "w") as jar:
                jar.writestr("fabric.mod.json", '{"id":"test"}')
            report = inspect_instance(root)
            self.assertEqual(report["forge_mods"], ["forge.jar"])
            self.assertIn("JAR с Forge mods.toml: 1", format_instance_report(report))
            self.assertEqual(len(report["mods"]), 2)


class DisabledModTests(unittest.TestCase):
    def test_disabled_jar_is_reported_but_not_scanned_as_active(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            versioned = mods / "1.20.1"
            versioned.mkdir(parents=True)
            disabled = mods / "addon.jar.disabled"
            disabled.write_bytes(b"not a jar")
            other = versioned / "old.jar.bak"
            other.write_bytes(b"backup")
            active = mods / "active.jar"
            with zipfile.ZipFile(active, "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            report = inspect_instance(root)
            self.assertEqual(report["mods"], ["active.jar"])
            self.assertEqual(report["disabled_mods"],
                             ["1.20.1/old.jar.bak", "addon.jar.disabled"])
            self.assertIn("Отключённые JAR", format_instance_report(report))
            self.assertNotIn("addon.jar.disabled", report["invalid_mods"])
            self.assertEqual(disabled.read_bytes(), b"not a jar")
            self.assertEqual(other.read_bytes(), b"backup")


class LinkedInstanceRootTests(unittest.TestCase):
    def test_symlinked_instance_root_is_not_scanned(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            external = base / "private-minecraft"
            (external / "mods").mkdir(parents=True)
            with zipfile.ZipFile(external / "mods" / "private.jar", "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            alias = base / "minecraft-alias"
            try:
                alias.symlink_to(external, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("Directory symlinks unavailable on this runner")
            report = inspect_instance(alias)
            self.assertTrue(report["linked_instance"])
            self.assertEqual(report["mods"], [])
            summary = format_instance_report(report)
            self.assertIn("является ссылкой", summary)
            self.assertNotIn("private.jar", summary)
            self.assertNotIn(str(external), summary)


class LinkedModsFolderTests(unittest.TestCase):
    def test_symlinked_mods_folder_is_not_scanned_or_misreported_as_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            external = root / 'external'
            external.mkdir()
            with zipfile.ZipFile(external / 'private.jar', 'w') as jar:
                jar.writestr('META-INF/mods.toml', 'modLoader="javafml"')
            instance = root / 'instance'
            instance.mkdir()
            try:
                (instance / 'mods').symlink_to(external, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest('Directory symlinks unavailable on this runner')
            result = inspect_instance(instance)
            self.assertTrue(result['linked_mods'])
            self.assertEqual(result['mods'], [])
            report = format_instance_report(result)
            self.assertIn('является ссылкой', report)
            self.assertNotIn('Папка mods отсутствует', report)
            self.assertNotIn(str(external), report)


class DuplicateJarMemberTests(unittest.TestCase):
    def test_case_and_separator_collisions_are_reported(self):
        self.assertTrue(ambiguous_jar_members([
            "assets/Logo.PNG", "assets/logo.png",
        ]))
        self.assertTrue(ambiguous_jar_members([
            "assets\\icon.png", "assets/icon.png",
        ]))
        self.assertFalse(ambiguous_jar_members([
            "assets/one.png", "assets/two.png",
        ]))

    def test_windows_case_collision_in_real_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            jar_path = mods / "case-collision.jar"
            with zipfile.ZipFile(jar_path, "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
                jar.writestr("assets/ICON.png", b"a")
                jar.writestr("assets/icon.png", b"b")
            original = jar_path.read_bytes()
            result = inspect_instance(root)
            self.assertIn("case-collision.jar", result["duplicate_members"])
            self.assertEqual(jar_path.read_bytes(), original)
            self.assertNotIn(str(root), format_instance_report(result))


    def test_warns_about_duplicate_zip_members_without_modifying_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            suspect = mods / "duplicate-entries.jar"
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(suspect, "w") as jar:
                    jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
                    jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
            original = suspect.read_bytes()
            result = inspect_instance(root)
            self.assertEqual(result["duplicate_members"], ["duplicate-entries.jar"])
            self.assertIn("Повторяющиеся или неоднозначные пути внутри JAR", format_instance_report(result))
            self.assertEqual(suspect.read_bytes(), original)

    def test_distinct_zip_members_do_not_trigger_warning(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            with zipfile.ZipFile(mods / "ordinary.jar", "w") as jar:
                jar.writestr("META-INF/mods.toml", 'modLoader="javafml"')
                jar.writestr("assets/icon.png", b"test")
            self.assertEqual(inspect_instance(root)["duplicate_members"], [])


class JarCompressionTests(unittest.TestCase):
    def test_flags_oversized_or_extreme_compression_without_reading_payload(self):
        ordinary = zipfile.ZipInfo('META-INF/mods.toml')
        ordinary.file_size = 1200
        ordinary.compress_size = 800
        self.assertFalse(suspicious_jar_compression([ordinary]))

        oversized = zipfile.ZipInfo('large.bin')
        oversized.file_size = 513 * 1024 * 1024
        oversized.compress_size = 500 * 1024 * 1024
        self.assertTrue(suspicious_jar_compression([oversized]))

        compressed = zipfile.ZipInfo('payload.bin')
        compressed.file_size = 5 * 1024 * 1024
        compressed.compress_size = 1000
        self.assertTrue(suspicious_jar_compression([compressed]))

    def test_report_includes_compression_warning_without_absolute_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / 'mods'
            mods.mkdir()
            jar_path = mods / 'very-compressed.jar'
            with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as jar:
                jar.writestr('META-INF/mods.toml', 'modLoader="javafml"')
                jar.writestr('data/payload.bin', b'0' * (5 * 1024 * 1024))
            result = inspect_instance(root)
            self.assertIn('very-compressed.jar', result['large_mods'])
            report = format_instance_report(result)
            self.assertIn('сильно сжатый JAR', report)
            self.assertNotIn(str(root), report)
            self.assertEqual(jar_path.stat().st_size > 0, True)


class EncryptedJarMetadataTests(unittest.TestCase):
    def test_encrypted_entries_are_flagged_without_reading_data(self):
        from shadow_launcher import encrypted_jar_members
        plain = zipfile.ZipInfo('META-INF/mods.toml')
        secret = zipfile.ZipInfo('assets/locked.bin')
        secret.flag_bits |= 0x1
        self.assertFalse(encrypted_jar_members([plain]))
        self.assertTrue(encrypted_jar_members([plain, secret]))
        self.assertFalse(encrypted_jar_members([]))

    def test_encryption_warning_appears_in_diagnostic_report(self):
        report = {'exists': True, 'message': 'Forge 1.20.1 обнаружен',
                  'versions': [], 'mods': ['locked.jar'],
                  'mods_folder_exists': True, 'encrypted_mods': ['locked.jar']}
        self.assertIn('Зашифрованные записи JAR', format_instance_report(report))


class UnsupportedJarCompressionTests(unittest.TestCase):
    def test_java_supported_methods_and_unsupported_methods(self):
        from shadow_launcher import unsupported_jar_compression
        plain = zipfile.ZipInfo('assets/plain.txt')
        plain.compress_type = zipfile.ZIP_STORED
        packed = zipfile.ZipInfo('assets/packed.txt')
        packed.compress_type = zipfile.ZIP_DEFLATED
        bzip = zipfile.ZipInfo('assets/bzip.txt')
        bzip.compress_type = zipfile.ZIP_BZIP2
        self.assertFalse(unsupported_jar_compression([plain, packed]))
        self.assertTrue(unsupported_jar_compression([plain, bzip]))

    def test_bzip2_jar_is_reported_without_extracting_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / 'mods'
            mods.mkdir()
            jar_path = mods / 'bzip2.jar'
            with zipfile.ZipFile(jar_path, 'w', compression=zipfile.ZIP_BZIP2) as jar:
                jar.writestr('META-INF/mods.toml', 'modLoader="javafml"')
            original = jar_path.read_bytes()
            report = inspect_instance(root)
            self.assertEqual(report['unsupported_compression_mods'], ['bzip2.jar'])
            self.assertIn('неподдерживаемым Java сжатием', format_instance_report(report))
            self.assertEqual(jar_path.read_bytes(), original)


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

    def test_linked_jar_outside_instance_is_not_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "minecraft"
            mods = root / "mods"
            mods.mkdir(parents=True)
            outside = Path(tmp) / "private.jar"
            outside.write_bytes(b"outside")
            try:
                (mods / "linked.jar").symlink_to(outside)
            except (OSError, NotImplementedError):
                self.skipTest("Windows symlinks require additional privileges")
            report = inspect_instance(root)
            self.assertEqual(report["mods"], [])
            self.assertEqual(outside.read_bytes(), b"outside")

    def test_linked_versioned_mod_folder_is_not_scanned(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "minecraft"
            mods = root / "mods"
            mods.mkdir(parents=True)
            outside = Path(tmp) / "outside"
            outside.mkdir()
            (outside / "external.jar").write_bytes(b"outside")
            try:
                (mods / "1.20.1").symlink_to(outside, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("Windows directory symlinks require additional privileges")
            report = inspect_instance(root)
            self.assertEqual(report["mods"], [])
            self.assertEqual((outside / "external.jar").read_bytes(), b"outside")

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

    def test_forge_version_report_lists_matching_installations_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            versions = root / "versions"
            for name in ("1.20.1-forge-47.3.0", "forge-1.20.1-47.4.0",
                         "1.20.10-forge-47.3.0", "1.20.1.1-forge-47.3.0"):
                (versions / name).mkdir(parents=True)
            result = inspect_instance(root)
            matching = forge_1201_versions(result["versions"])
            self.assertEqual(matching, ["1.20.1-forge-47.3.0", "forge-1.20.1-47.4.0"])
            summary = format_instance_report(result)
            self.assertIn("Forge 1.20.1: 1.20.1-forge-47.3.0, forge-1.20.1-47.4.0", summary)
            self.assertNotIn("1.20.10-forge", summary)
            self.assertNotIn(str(root), summary)

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

    def test_quilt_only_mod_is_flagged_without_modifying_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / 'mods'
            mods.mkdir()
            quilt = mods / 'quilt.jar'
            dual = mods / 'dual.jar'
            with zipfile.ZipFile(quilt, 'w') as jar:
                jar.writestr('quilt.mod.json', '{"quilt_loader":{"id":"sample"}}')
            with zipfile.ZipFile(dual, 'w') as jar:
                jar.writestr('quilt.mod.json', '{}')
                jar.writestr('META-INF/mods.toml', 'modLoader="javafml"')
            before = quilt.read_bytes()
            report = inspect_instance(root)
            self.assertEqual(report['quilt_mods'], ['quilt.jar'])
            self.assertIn('Quilt: quilt.jar', format_instance_report(report))
            self.assertEqual(quilt.read_bytes(), before)

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



class UnknownModDescriptorTests(unittest.TestCase):
    def test_valid_zip_without_mod_descriptor_gets_advisory_warning(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / 'mods'
            mods.mkdir()
            unknown = mods / 'resources.jar'
            forge = mods / 'forge.jar'
            legacy = mods / 'legacy.jar'
            with zipfile.ZipFile(unknown, 'w') as jar:
                jar.writestr('assets/example/texture.png', b'pixels')
            with zipfile.ZipFile(forge, 'w') as jar:
                jar.writestr('META-INF/mods.toml', 'modLoader="javafml"')
            with zipfile.ZipFile(legacy, 'w') as jar:
                jar.writestr('mcmod.info', '{}')
            original = unknown.read_bytes()
            result = inspect_instance(root)
            self.assertEqual(result['unknown_mods'], ['resources.jar'])
            self.assertIn('JAR без распознанных метаданных мода',
                          format_instance_report(result))
            self.assertEqual(result['invalid_mods'], [])
            self.assertEqual(unknown.read_bytes(), original)

    def test_missing_folder_has_no_unknown_mods(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = inspect_instance(Path(tmp) / 'missing')
            self.assertEqual(result['unknown_mods'], [])


class UnsafeJarPathTests(unittest.TestCase):
    def test_warns_about_traversal_without_extracting_or_editing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            jar = mods / "suspicious.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("../outside.txt", "do not extract")
                archive.writestr("META-INF/mods.toml", "")
            original = jar.read_bytes()
            result = inspect_instance(root)
            self.assertEqual(result["unsafe_mods"], ["suspicious.jar"])
            self.assertIn("Подозрительные пути внутри JAR", format_instance_report(result))
            self.assertFalse((root / "outside.txt").exists())
            self.assertEqual(jar.read_bytes(), original)

    def test_windows_reserved_and_stream_paths_are_reported_read_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            jar = mods / "windows-ambiguous.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/mods.toml", "")
                archive.writestr("assets/CON.txt", "reserved device")
                archive.writestr("assets/file.txt:stream", "alternate stream")
            original = jar.read_bytes()
            result = inspect_instance(root)
            self.assertEqual(result["unsafe_mods"], ["windows-ambiguous.jar"])
            self.assertEqual(jar.read_bytes(), original)

    def test_normal_jar_paths_are_not_flagged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            with zipfile.ZipFile(mods / "safe.jar", "w") as archive:
                archive.writestr("META-INF/mods.toml", "")
                archive.writestr("assets/example/icon.png", "ok")
            self.assertEqual(inspect_instance(root)["unsafe_mods"], [])


class DiagnosticReportTests(unittest.TestCase):
    def test_report_contains_warnings_but_not_absolute_instance_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / 'mods'
            mods.mkdir()
            bad = mods / 'broken.jar'
            bad.write_bytes(b'not a jar')
            report = format_instance_report(inspect_instance(root))
            self.assertIn('Повреждённые JAR: broken.jar', report)
            self.assertIn('JAR-модов: 1', report)
            self.assertNotIn(str(root), report)
            self.assertEqual(bad.read_bytes(), b'not a jar')

    def test_missing_mods_folder_has_distinct_hint_without_creating_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'versions' / '1.20.1-forge-47.3.0').mkdir(parents=True)
            report = inspect_instance(root)
            self.assertTrue(report['forge'])
            self.assertFalse(report['mods_folder_exists'])
            self.assertIn('Папка mods отсутствует', format_instance_report(report))
            self.assertFalse((root / 'mods').exists())

    def test_empty_mod_folder_has_actionable_read_only_hint(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'mods').mkdir()
            report = format_instance_report(inspect_instance(root))
            self.assertIn('Моды не найдены', report)
            self.assertIn('mods/1.20.1', report)
            self.assertNotIn(str(root), report)

    def test_missing_instance_has_clear_message(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = format_instance_report(inspect_instance(Path(tmp) / 'missing'))
            self.assertIn('Папка Minecraft не найдена', report)
            self.assertNotIn('JAR-модов:', report)



class EmptyJarArchiveTests(unittest.TestCase):
    def test_empty_zip_is_reported_without_modifying_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / "mods"
            mods.mkdir()
            jar = mods / "empty.jar"
            with zipfile.ZipFile(jar, "w"):
                pass
            original = jar.read_bytes()
            report = inspect_instance(root)
            self.assertEqual(report["empty_archives"], ["empty.jar"])
            self.assertNotIn("empty.jar", report["invalid_mods"])
            self.assertIn("JAR без файлов внутри", format_instance_report(report))
            self.assertEqual(jar.read_bytes(), original)

if __name__ == "__main__":
    unittest.main()
