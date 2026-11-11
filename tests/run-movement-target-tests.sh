#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/movement-target-tests
javac -d build/movement-target-tests app/src/main/java/app/rwun/DailyHealthRules.java tests/MovementTargetTest.java
java -cp build/movement-target-tests app.rwun.MovementTargetTest
