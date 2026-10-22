#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/navigation-tests
javac -d build/navigation-tests app/src/main/java/app/rwun/{SettingsNavigator,UserErrors,RunStartGate}.java tests/NavigationErrorTest.java tests/RunStartTest.java
java -cp build/navigation-tests app.rwun.NavigationErrorTest

java -cp build/navigation-tests app.rwun.RunStartTest
