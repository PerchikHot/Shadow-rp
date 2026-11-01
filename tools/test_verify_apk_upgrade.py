"""Headless tests for Shadow RP Android upgrade verification."""
import unittest
from pathlib import Path
from unittest.mock import patch
from verify_apk_upgrade import signature_digests, version_code, package_name, verify_upgrade


class ApkUpgradeTests(unittest.TestCase):
    def test_certificate_parser_rejects_empty_or_truncated_fingerprints(self):
        self.assertEqual(signature_digests("unsigned"), frozenset())
        self.assertEqual(signature_digests("Signer #1 certificate SHA-256 digest: ab"), frozenset())

    def test_certificate_parser_accepts_valid_sha256(self):
        digest = "A1" * 32
        self.assertEqual(
            signature_digests("Signer #1 certificate SHA-256 digest: " + digest),
            frozenset([digest.lower()]),
        )
        self.assertEqual(
            signature_digests("V2 Signer: certificate SHA-256 digest: " + digest),
            frozenset([digest.lower()]),
        )

    def test_version_code_parsing_is_strict(self):
        self.assertEqual(version_code("package: name='com.shadowrp.app' versionCode='50'"), 50)
        with self.assertRaises(ValueError):
            version_code("no version")
        self.assertEqual(package_name("package: name='com.shadowrp.app' versionCode='50'"),
                         "com.shadowrp.app")
        with self.assertRaises(ValueError):
            package_name("no package")

    def test_upgrade_rejects_changed_certificates_and_downgrades(self):
        old = Path("previous.apk")
        new = Path("candidate.apk")
        with patch("verify_apk_upgrade.inspect", side_effect=[
            (frozenset(["a" * 64]), 49, "com.shadowrp.app"),
            (frozenset(["b" * 64]), 50, "com.shadowrp.app"),
        ]):
            with self.assertRaisesRegex(ValueError, "certificate changed"):
                verify_upgrade("signer", "aapt", old, new)
        with patch("verify_apk_upgrade.inspect", side_effect=[
            (frozenset(["a" * 64]), 50, "com.shadowrp.app"),
            (frozenset(["a" * 64]), 49, "com.shadowrp.app"),
        ]):
            with self.assertRaisesRegex(ValueError, "decreased"):
                verify_upgrade("signer", "aapt", old, new)
        with patch("verify_apk_upgrade.inspect", side_effect=[
            (frozenset(["a" * 64]), 49, "com.shadowrp.app"),
            (frozenset(["a" * 64]), 50, "com.other.app"),
        ]):
            with self.assertRaisesRegex(ValueError, "package ID changed"):
                verify_upgrade("signer", "aapt", old, new)


if __name__ == "__main__":
    unittest.main()
