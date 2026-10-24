#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/focus-tests
javac -cp "$JSON_JAR" -d build/focus-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/FocusHostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,PreferenceEdit,PlanChanges,ActivityImportPolicy,WalkData,CyclingData,BackupManager,V103Data,IndoorClock,ReminderPlan,ScheduleProposer,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach}.java
java -cp "build/focus-tests:$JSON_JAR" app.rwun.FocusHostTest
