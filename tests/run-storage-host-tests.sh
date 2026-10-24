#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR to a local org.json:json:20240303 JAR}"
mkdir -p build/storage-host-tests
javac -cp "$JSON_JAR" -d build/storage-host-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/StorageHostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,CoachingMemory,CoachGuide,VoiceGuidance,BeginnerProgramme,WorkoutFactory,ActivityImportPolicy,WalkData,CyclingData,V103Data,IndoorClock,ReminderPlan,ScheduleProposer,BackupManager,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach}.java
java -cp "build/storage-host-tests:$JSON_JAR" app.rwun.StorageHostTest
