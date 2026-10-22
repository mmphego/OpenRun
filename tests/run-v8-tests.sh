#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/v8-tests
javac -d build/v8-tests app/src/main/java/app/rwun/{PlanEngine,PlanPreferences,PaceProfile,WorkoutFactory,BestEfforts,CalendarExport,BackupCipher,StrengthPlan,TrainingReview}.java tests/FeaturesV8Test.java
java -cp build/v8-tests FeaturesV8Test
