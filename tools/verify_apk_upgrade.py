"""Fail closed if a new Android APK cannot update the previous public APK.

This checks the published signing identity, not the certificate on a user's
phone. Android still enforces its own signature and package-ID rules.
"""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path


def signature_digests(output: str) -> frozenset[str]:
    values = re.findall(
        r"(?:Signer #\d+|V\d+(?:\.\d+)? Signer:)\s*certificate SHA-256 digest:\s*([0-9a-fA-F]{64})",
        output, flags=re.IGNORECASE,
    )
    return frozenset(value.lower() for value in values)


def version_code(output: str) -> int:
    match = re.search(r"versionCode='(\d+)'", output)
    if not match:
        raise ValueError("Cannot read APK versionCode")
    return int(match.group(1))


def package_name(output: str) -> str:
    match = re.search(r"package: name='([^']+)'", output)
    if not match:
        raise ValueError("Cannot read APK package name")
    return match.group(1)


def inspect(apksigner: str, aapt: str, apk: Path) -> tuple[frozenset[str], int, str]:
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError(f"Missing APK: {apk}")
    signed = subprocess.run(
        [apksigner, "verify", "--verbose", "--print-certs", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout
    digests = signature_digests(signed)
    if len(digests) != 1:
        raise ValueError("Expected exactly one verified signing certificate")
    badging = subprocess.run(
        [aapt, "dump", "badging", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout
    return digests, version_code(badging), package_name(badging)


def verify_upgrade(apksigner: str, aapt: str, previous: Path, candidate: Path) -> None:
    old_certs, old_code, old_package = inspect(apksigner, aapt, previous)
    new_certs, new_code, new_package = inspect(apksigner, aapt, candidate)
    if old_package != new_package or new_package != "com.shadowrp.app":
        raise ValueError("APK package ID changed; refusing to overwrite release")
    if old_certs != new_certs:
        raise ValueError("APK signing certificate changed; refusing to overwrite release")
    if new_code < old_code:
        raise ValueError(f"APK versionCode decreased: {old_code} -> {new_code}")
    print(f"APK upgrade check passed: same SHA-256 certificate, versionCode {old_code} -> {new_code}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    args = parser.parse_args()
    verify_upgrade(args.apksigner, args.aapt, args.previous, args.candidate)
