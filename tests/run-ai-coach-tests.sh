#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/ai-coach-tests
javac -cp "$JSON_JAR" -d build/ai-coach-tests tests/storage-host/android/content/*.java tests/storage-host/app/rwun/AiCoachHostTest.java tests/storage-host/app/rwun/AiPlanningHostTest.java app/src/main/java/app/rwun/{AiFullPlanPolicy,RaceTraining,TrainingReview,TrainingProgress,OnboardingPlan,WeekBalance,V1State,PlanPreferences,StrengthPlan,RunStore,AiCoachPolicy,AiCoachClient,PreferenceEdit,PlanChanges,CoachingMemory,CoachGuide,VoiceGuidance,BeginnerProgramme,WorkoutFactory,ActivityImportPolicy,WalkData,CyclingData,V103Data,IndoorClock,StepEstimate,ReminderPlan,ScheduleProposer,RacePlanner,WorkoutMatcher,WorkoutReview,BestEfforts,PlanEngine,PaceProfile,PaceInsights,AdaptiveCoach}.java
java -cp "build/ai-coach-tests:$JSON_JAR" app.rwun.AiCoachHostTest

java -cp "build/ai-coach-tests:$JSON_JAR" app.rwun.AiPlanningHostTest
