#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/v1-tests
javac -cp "$JSON_JAR" -d build/v1-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/V1HostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,CoachingMemory,CoachGuide,VoiceGuidance,BeginnerProgramme,WorkoutFactory,ActivityImportPolicy,WalkData,CyclingData,V103Data,IndoorClock,ReminderPlan,ScheduleProposer,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach}.java
java -cp "build/v1-tests:$JSON_JAR" app.rwun.V1HostTest
