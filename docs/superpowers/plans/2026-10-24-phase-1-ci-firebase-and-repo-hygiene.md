# Phase 1: CI Firebase Hardening, Release Leak Protection & Repository Hygiene Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish fail-closed CI gates for Firebase App Distribution, prevent credential leaks in release binaries, un-ignore design documentation, and sanitize `README.md` to use clean relative paths and verifiable metrics.

**Architecture:** Remove ad-hoc Python monkeypatching in CI in favor of strict fail-closed JSON validation (matching both `package_name` and `mobilesdk_app_id`). Add an automated release binary scanner that unpacks the release APK and AAB to ensure no raw secrets exist in bytecode or assets. Sanitize public documentation and restore Git tracking for architecture specs.

**Tech Stack:** GitHub Actions, Python 3 / Bash, Android APK / AAB (zip/unzip tooling), Markdown.

**Spec:** `docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`

## Global Constraints
- Android Package Name: `com.adauto.cinemahub`
- Firebase App ID: `1:53423584032:android:129922fce1bf5a9e6b9d45`
- Fail-Closed Release Engineering: Production CD must halt on missing secrets or invalid client packages; no `continue-on-error: true` on App Distribution.
- Zero Plaintext Secrets: Release APK and AAB must not contain raw TMDB secret strings.
- Public Documentation: Zero local machine file paths (`file:///Users/...`) in `README.md`.

## Review Focus
- Missing or mismatched `mobilesdk_app_id` in `google-services.json`: Validation script must fail with exit code 1.
- Tampered `package_name` in `google-services.json`: Validation script must fail with exit code 1.
- Accidental secret string in release `.apk` or `.aab`: Unpack-and-grep scan must fail the build before staging.
- Relative links in `README.md`: All module and file links must resolve against repository root, without breaking on GitHub.
- Tracking of `docs/`: Specs and design documents in `docs/` must be versioned and not ignored by `.gitignore`.

---

### Task 1: Repository Hygiene - Un-ignore `docs/` & Sanitize `README.md`

**Files:**
- Modify: `.gitignore:36-39`
- Modify: `README.md:1-250`
- Test: Local verification via git commands and link checker.

**Interfaces:**
- Consumes: Repository files and Git ignore patterns.
- Produces: Version-controlled `docs/` directory and sanitized public `README.md`.

- [ ] **Step 1: Check git status for `docs/`**
  Run: `git check-ignore docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`
  Expected: Returns `docs/` (matching line 38 of `.gitignore`).

- [ ] **Step 2: Remove `docs/` from `.gitignore`**
  In `.gitignore`:
  Remove line 38 (`docs/`), leaving `.superpowers/` ignored while allowing `docs/` to be tracked.

- [ ] **Step 3: Verify `docs/` is now trackable**
  Run: `git check-ignore docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`
  Expected: Exit code 1 (not ignored).

- [ ] **Step 4: Sanitize `README.md`**
  In `README.md`:
  - Replace every `file:///Users/adautosouza/repos/estudos/<path>` link with a relative markdown link `<path>` (e.g. `app`, `core/model`, etc.).
  - Remove the static badge `![Detekt](https://img.shields.io/badge/Detekt-0%20Code%20Smells-success)`.
  - Replace unverified claims ("100% de cobertura nos fluxos críticos", "218 tests") with honest statements reflecting currently verified test suites and noting that Kover coverage gates are established in Phase 3.

- [ ] **Step 5: Verify no `file:///` strings remain in `README.md`**
  Run: `grep "file:///" README.md || echo "CLEAN"`
  Expected: `CLEAN`

- [ ] **Step 6: Commit changes**
  ```bash
  git add .gitignore README.md docs/superpowers/specs/
  git commit -m "docs: un-ignore docs directory, convert README links to relative, and remove unverified metrics"
  ```

---

### Task 2: Fail-Closed Firebase CI Validation & Secret Leak Scanner (.github/workflows/main-cd.yml)

**Files:**
- Modify: `.github/workflows/main-cd.yml:1-351`
- Test: Local validation script test and GitHub Actions workflow syntax check.

**Interfaces:**
- Consumes: `GOOGLE_SERVICES_JSON_B64`, `FIREBASE_APP_ID`, `TMDB_API_KEY`, `TMDB_READ_ACCESS_TOKEN`.
- Produces: Fail-closed verification steps in `main-cd.yml`.

- [ ] **Step 1: Write local unit test for the Firebase JSON validation script**
  Create a temporary test script `ci/validate_google_services.py` that verifies:
  1. Valid json with `com.adauto.cinemahub` and matching `mobilesdk_app_id` passes (exit 0).
  2. Mismatched `mobilesdk_app_id` exits 1 with error.
  3. Missing package name exits 1 with error.

- [ ] **Step 2: Run local validation test**
  Run: `python3 ci/validate_google_services.py`
  Expected: All 3 assertions PASS.

- [ ] **Step 3: Update `.github/workflows/main-cd.yml` with strict validation**
  In `.github/workflows/main-cd.yml`:
  - Under `Setup google-services.json`:
    Remove the python monkeypatching script (lines 80-97).
    Replace with strict validation:
    ```bash
    python3 -c '
    import json, sys, os
    with open("app/google-services.json") as f:
        data = json.load(f)
    clients = data.get("client", [])
    matching = [c for c in clients if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == "com.adauto.cinemahub"]
    if not matching:
        print("::error::app/google-services.json does not contain package com.adauto.cinemahub!", file=sys.stderr)
        sys.exit(1)
    app_id = matching[0].get("client_info", {}).get("mobilesdk_app_id")
    expected_app_id = os.environ.get("FIREBASE_APP_ID")
    if expected_app_id and app_id != expected_app_id:
        print(f"::error::mobilesdk_app_id mismatch! Expected {expected_app_id}, found {app_id}", file=sys.stderr)
        sys.exit(1)
    print(f"Validated google-services.json: package com.adauto.cinemahub matched app_id {app_id}")
    '
    ```
  - Under `deploy-and-release` job:
    Remove `continue-on-error: true` from `firebase_dist` step.
    Remove `Notify on Firebase App Distribution Mismatch` warning step.

- [ ] **Step 4: Update Firebase Test Lab step and workflow_dispatch override**
  In `.github/workflows/main-cd.yml`:
  - Add `workflow_dispatch` input:
    ```yaml
    on:
      push:
        branches: [main]
      workflow_dispatch:
        inputs:
          override_test_lab:
            description: "Bypass Firebase Test Lab failure (e.g. quota exhausted)"
            required: false
            type: boolean
            default: false
    ```
  - In `Firebase Test Lab Execution`:
    Remove `exit 0` on `TEST_QUOTA_EXCEEDED`.
    Check:
    ```bash
    if [ $EXIT_CODE -ne 0 ]; then
      if [ "${{ inputs.override_test_lab }}" == "true" ]; then
        echo "::warning title=Test Lab Override::Bypassing Test Lab failure due to explicit workflow_dispatch override."
      else
        echo "::error title=Firebase Test Lab Failed::Instrumentation tests failed (exit code $EXIT_CODE)."
        exit $EXIT_CODE
      fi
    fi
    ```

- [ ] **Step 5: Add Secret Leak Scanner in CI before Staging Artifacts**
  In `.github/workflows/main-cd.yml`:
  Add step `Scan Release Artifacts for Secret Leaks`:
  ```bash
  - name: Scan Release Artifacts for Secret Leaks
    env:
      TMDB_API_KEY: ${{ secrets.TMDB_API_KEY }}
      TMDB_READ_ACCESS_TOKEN: ${{ secrets.TMDB_READ_ACCESS_TOKEN }}
    run: |
      mkdir -p /tmp/apk_inspect /tmp/aab_inspect
      unzip -q app/build/outputs/apk/release/*.apk -d /tmp/apk_inspect
      unzip -q app/build/outputs/bundle/release/*.aab -d /tmp/aab_inspect
      
      LEAK_FOUND=0
      for secret in "$TMDB_API_KEY" "$TMDB_READ_ACCESS_TOKEN"; do
        if [ -n "$secret" ]; then
          if grep -r -F "$secret" /tmp/apk_inspect/ >/dev/null 2>&1; then
            echo "::error::Plaintext secret leaked inside release APK!"
            LEAK_FOUND=1
          fi
          if grep -r -F "$secret" /tmp/aab_inspect/ >/dev/null 2>&1; then
            echo "::error::Plaintext secret leaked inside release AAB!"
            LEAK_FOUND=1
          fi
        fi
      done
      rm -rf /tmp/apk_inspect /tmp/aab_inspect
      if [ $LEAK_FOUND -ne 0 ]; then
        exit 1
      fi
      echo "Release artifact leak scan passed: 0 raw secrets found."
  ```

- [ ] **Step 6: Remove `mapping.txt` from GitHub Release attachments**
  In `.github/workflows/main-cd.yml` step `Publish GitHub Release`:
  Remove `release-mapping/mapping.txt` from `files:` list, keeping only:
  ```yaml
  files: |
    release-apk/app-release.apk
    release-bundle/*.aab
  ```

- [ ] **Step 7: Verify workflow syntax and commit**
  Run: `python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/main-cd.yml"))'`
  Commit:
  ```bash
  git add .github/workflows/main-cd.yml ci/validate_google_services.py
  git commit -m "ci: enforce fail-closed Firebase validation, secret leak scan, and private mapping.txt"
  ```
