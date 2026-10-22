#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/coach-tests
javac -d build/coach-tests app/src/main/java/app/rwun/CoachGuide.java tests/CoachGuideTest.java
java -cp build/coach-tests CoachGuideTest
