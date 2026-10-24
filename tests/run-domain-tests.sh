#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/domain-tests
javac -d build/domain-tests app/src/main/java/app/rwun/{PlanEngine,GpsAccumulator,CoachingCues,AdaptiveCoach,PaceProfile,WorkoutFactory,WorkoutEngine,SpeechQueue,RollingPace,PaceInsights}.java tests/{PlanEngineTest,FeatureTests,CoachingV3Test}.java
java -cp build/domain-tests PlanEngineTest
java -cp build/domain-tests FeatureTests
java -cp build/domain-tests CoachingV3Test
