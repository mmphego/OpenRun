#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/gemma-tests
javac -d build/gemma-tests app/src/main/java/app/rwun/GemmaModel.java tests/GemmaModelTest.java
java -cp build/gemma-tests app.rwun.GemmaModelTest
