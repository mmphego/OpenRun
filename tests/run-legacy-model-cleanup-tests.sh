#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/legacy-model-cleanup-tests
javac -d build/legacy-model-cleanup-tests app/src/main/java/app/rwun/LegacyModelCleanup.java tests/LegacyModelCleanupTest.java
java -cp build/legacy-model-cleanup-tests app.rwun.LegacyModelCleanupTest
