#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/race-tests
javac -cp "$JSON_JAR" -d build/race-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/RaceHostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,PreferenceEdit,PlanChanges,ActivityImportPolicy,WalkData,CyclingData,BackupManager,V103Data,IndoorClock,ReminderPlan,ScheduleProposer,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach,RacePlanner,RaceTraining,TrainingReview,RaceCatalogue}.java
java -cp "build/race-tests:$JSON_JAR" app.rwun.RaceHostTest
