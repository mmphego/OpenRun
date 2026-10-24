#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR to a local org.json JAR}"
mkdir -p build/voice-coach-tests
javac -cp "$JSON_JAR" -d build/voice-coach-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/VoiceCoachHostTest.java app/src/main/java/app/rwun/{OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,PreferenceEdit,PlanChanges,ActivityImportPolicy,WalkData,CyclingData,V103Data,IndoorClock,ReminderPlan,ScheduleProposer,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach,CoachingMemory,CoachGuide,VoiceGuidance,BeginnerProgramme,WorkoutFactory,WorkoutEngine,SpeechQueue}.java
java -cp "build/voice-coach-tests:$JSON_JAR" app.rwun.VoiceCoachHostTest
