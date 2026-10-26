#!/usr/bin/env bash
set -euo pipefail

echo "==> Checking for hardcoded string literals in Compose src/main..."

# Pattern matches:
# 1. Text("...") or Text(text = "...") with literal non-empty string
# 2. contentDescription = "..." with literal non-empty string
VIOLATIONS=$(git grep -En '(Text\s*\(\s*"|Text\s*\(\s*text\s*=\s*"|contentDescription\s*=\s*")' -- '*src/main*.kt' || true)

if [ -n "$VIOLATIONS" ]; then
    echo "ERROR: Hardcoded string literals found in production Compose code:"
    echo "$VIOLATIONS"
    echo ""
    echo "Please extract all user-facing strings and content descriptions to strings.xml via stringResource()."
    exit 1
fi

echo "SUCCESS: Zero hardcoded UI string literals found in src/main."
exit 0
