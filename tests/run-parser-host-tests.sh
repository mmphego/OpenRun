#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KXML2_JAR:?Set KXML2_JAR to a local net.sf.kxml:kxml2:2.3.0 JAR}"
mkdir -p build/parser-host-tests
javac -cp "$KXML2_JAR" -d build/parser-host-tests tests/host/android/util/Xml.java tests/host/app/rwun/{ParserHostTest,FitReaderHostTest}.java app/src/main/java/app/rwun/{ActivityFiles,FitReader,GpsAccumulator}.java
java -cp "build/parser-host-tests:$KXML2_JAR" app.rwun.ParserHostTest

java -cp "build/parser-host-tests:$KXML2_JAR" app.rwun.FitReaderHostTest
