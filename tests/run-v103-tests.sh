#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/v103-tests
javac -cp "$JSON_JAR" -d build/v103-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/V103HostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,CoachingMemory,CoachGuide,VoiceGuidance,BeginnerProgramme,WorkoutFactory,ActivityImportPolicy,WalkData,CyclingData,V103Data,IndoorClock,StepEstimate,ReminderPlan,ScheduleProposer,RacePlanner,WorkoutMatcher,WorkoutReview,BestEfforts,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach}.java
java -cp "build/v103-tests:$JSON_JAR" app.rwun.V103HostTest
