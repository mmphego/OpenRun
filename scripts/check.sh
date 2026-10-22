#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/run-host-tests.py
./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s server -p 'test_*.py' -v
python3 -m unittest discover -s tests/device -p 'test_*.py' -v
