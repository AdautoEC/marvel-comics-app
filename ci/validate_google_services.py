#!/usr/bin/env python3
"""Validation script and self-test for google-services.json."""

import json
import os
import subprocess
import sys
import tempfile


def validate_google_services(file_path: str, expected_app_id: str | None = None) -> str:
    """Validates google-services.json for com.adauto.cinemahub and expected mobilesdk_app_id.

    Exits with code 1 if invalid.
    """
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    clients = data.get("client", [])
    matching = [
        c
        for c in clients
        if c.get("client_info", {})
        .get("android_client_info", {})
        .get("package_name")
        == "com.adauto.cinemahub"
    ]
    if not matching:
        print(
            "::error::app/google-services.json does not contain package com.adauto.cinemahub!",
            file=sys.stderr,
        )
        sys.exit(1)

    app_id = matching[0].get("client_info", {}).get("mobilesdk_app_id")
    if expected_app_id and app_id != expected_app_id:
        print(
            f"::error::mobilesdk_app_id mismatch! Expected {expected_app_id}, found {app_id}",
            file=sys.stderr,
        )
        sys.exit(1)

    print(
        f"Validated google-services.json: package com.adauto.cinemahub matched app_id {app_id}"
    )
    return app_id


def run_self_tests() -> None:
    """Executes self-testing suite covering all three validation criteria."""
    print("Running self-tests for google-services.json validation...")

    # 1. Matching package_name == "com.adauto.cinemahub" and matching mobilesdk_app_id passes (exit 0)
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(
            {
                "client": [
                    {
                        "client_info": {
                            "mobilesdk_app_id": "1:12345:android:abcde",
                            "android_client_info": {
                                "package_name": "com.adauto.cinemahub"
                            },
                        }
                    }
                ]
            },
            f,
        )
        valid_path = f.name

    try:
        proc = subprocess.run(
            [sys.executable, __file__, valid_path],
            env={**os.environ, "FIREBASE_APP_ID": "1:12345:android:abcde"},
            capture_output=True,
            text=True,
        )
        assert (
            proc.returncode == 0
        ), f"Expected exit 0, got {proc.returncode}. Stderr: {proc.stderr}"
        assert "Validated google-services.json" in proc.stdout
        print("Assertion 1 (valid package and matching app_id passes with exit 0): PASS")
    finally:
        os.remove(valid_path)

    # 2. Mismatched mobilesdk_app_id exits 1 with error
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(
            {
                "client": [
                    {
                        "client_info": {
                            "mobilesdk_app_id": "1:12345:android:wrong_id",
                            "android_client_info": {
                                "package_name": "com.adauto.cinemahub"
                            },
                        }
                    }
                ]
            },
            f,
        )
        mismatch_path = f.name

    try:
        proc = subprocess.run(
            [sys.executable, __file__, mismatch_path],
            env={**os.environ, "FIREBASE_APP_ID": "1:12345:android:expected_id"},
            capture_output=True,
            text=True,
        )
        assert proc.returncode == 1, f"Expected exit 1, got {proc.returncode}"
        assert "mobilesdk_app_id mismatch" in proc.stderr
        print("Assertion 2 (mismatched mobilesdk_app_id exits 1 with error): PASS")
    finally:
        os.remove(mismatch_path)

    # 3. Missing package name exits 1 with error
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(
            {
                "client": [
                    {
                        "client_info": {
                            "mobilesdk_app_id": "1:12345:android:abcde",
                            "android_client_info": {
                                "package_name": "com.other.app"
                            },
                        }
                    }
                ]
            },
            f,
        )
        missing_pkg_path = f.name

    try:
        proc = subprocess.run(
            [sys.executable, __file__, missing_pkg_path],
            env={**os.environ, "FIREBASE_APP_ID": "1:12345:android:abcde"},
            capture_output=True,
            text=True,
        )
        assert proc.returncode == 1, f"Expected exit 1, got {proc.returncode}"
        assert "does not contain package com.adauto.cinemahub" in proc.stderr
        print("Assertion 3 (missing package name exits 1 with error): PASS")
    finally:
        os.remove(missing_pkg_path)

    print("All 3 assertions PASS.")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] != "--test":
        target_path = sys.argv[1]
        expected_app_id = os.environ.get("FIREBASE_APP_ID")
        validate_google_services(target_path, expected_app_id)
    else:
        run_self_tests()
