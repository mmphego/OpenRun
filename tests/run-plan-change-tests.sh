#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR to a local org.json:json:20240303 JAR}"
mkdir -p build/plan-change-tests
javac -cp "$JSON_JAR" -d build/plan-change-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/PlanChangeHostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,PreferenceEdit,PlanChanges,ActivityImportPolicy,WalkData,CyclingData,V103Data,IndoorClock,ReminderPlan,ScheduleProposer,BackupManager,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach}.java
java -cp "build/plan-change-tests:$JSON_JAR" app.rwun.PlanChangeHostTest
