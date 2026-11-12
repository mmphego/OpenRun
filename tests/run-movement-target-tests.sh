#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/movement-target-tests
javac -cp "$JSON_JAR" -d build/movement-target-tests tests/storage-host/android/content/*.java app/src/main/java/app/rwun/DailyHealthRules.java app/src/main/java/app/rwun/DailyHealthStore.java tests/MovementTargetTest.java
java -cp "build/movement-target-tests:$JSON_JAR" app.rwun.MovementTargetTest
