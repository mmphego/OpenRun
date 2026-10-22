#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
R8_JAR="$(python3 fetch-build-tools.py)"
PLATFORM="$ANDROID_SDK_ROOT/platforms/android-36/android.jar"
B=build/instrumentation
mkdir -p "$B/classes" "$B/dex"
cat > "$B/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.rwun.test"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/><instrumentation android:name="app.rwun.SmokeInstrumentation" android:targetPackage="app.rwun" android:functionalTest="true"/><application android:label="OpenRun tests"/></manifest>
XML
"$BT/aapt2" link -I "$PLATFORM" --manifest "$B/AndroidManifest.xml" -o "$B/base.apk"
javac --release 17 -cp "$PLATFORM:build/standalone/classes" -d "$B/classes" app/src/androidTest/java/app/rwun/SmokeInstrumentation.java
jar cf "$B/tests.jar" -C "$B/classes" .
java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" --classpath build/standalone/classes.jar --desugared-lib build/standalone/desugar.json --output "$B/dex" "$B/tests.jar"
(cd "$B/dex" && zip -q ../base.apk classes.dex)
"$BT/zipalign" -f 4 "$B/base.apk" "$B/aligned.apk"
"$BT/apksigner" sign --ks development/debug.keystore --ks-pass pass:android --out "$B/tests.apk" "$B/aligned.apk"
