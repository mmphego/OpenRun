#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${DESUGAR_JAR:?Set DESUGAR_JAR to desugar_jdk_libs 2.1.4}"
: "${DESUGAR_CONFIG_JAR:?Set DESUGAR_CONFIG_JAR to desugar_jdk_libs_configuration 2.1.4}"
python3 fetch-runtime.py
python3 fetch-race-deps.py
R8_JAR="$(python3 fetch-build-tools.py)"
RUNTIME_CP="$(python3 -c 'from pathlib import Path; print(":".join(str(p) for p in Path("build/runtime-deps").glob("*.jar")))')"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
PLATFORM="$ANDROID_SDK_ROOT/platforms/android-36/android.jar"
B=build/standalone
rm -rf "$B/classes" "$B/generated" "$B/dex" "$B/library-dex"
mkdir -p "$B/classes" "$B/generated" "$B/dex" "$B/library-dex" dist
python3 - <<'PY'
from pathlib import Path
s=Path('app/src/main/AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="app.rwun" ')
Path('build/standalone/AndroidManifest.xml').write_text(s)
PY
"$BT/aapt2" compile --dir app/src/main/res -o "$B/resources.zip"
"$BT/aapt2" link "$B/resources.zip" -A app/src/main/assets -I "$PLATFORM" --manifest "$B/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 36 --version-code 27 --version-name 1.7.1 --java "$B/generated" -o "$B/resources.apk"
ROOM_CP="$(python3 -c 'from pathlib import Path; print(":".join(str(p) for p in Path("build/race-compiler").glob("*.jar")))')"
mkdir -p app/schemas
javac --release 17 -processorpath "$ROOM_CP" -processor androidx.room.RoomProcessor -Aroom.schemaLocation="$PWD/app/schemas" -s "$B/generated" -cp "$PLATFORM:$RUNTIME_CP" -d "$B/classes" "$B/generated/app/rwun/R.java" app/src/main/java/app/rwun/*.java
jar cf "$B/classes.jar" -C "$B/classes" .
unzip -p "$DESUGAR_CONFIG_JAR" META-INF/desugar/d8/desugar.json > "$B/desugar.json"
java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" --desugared-lib "$B/desugar.json" --output "$B/dex" "$B/classes.jar" build/runtime-deps/*.jar
# R8 9 no longer exposes D8's legacy keep-rule-output option. Retain the
# complete desugared support library in this development APK.
printf '%s\n' '-keep class j$.** { *; }' > "$B/desugar-keep.pro"
java -cp "$R8_JAR" com.android.tools.r8.L8 --release --min-api 26 --lib "$PLATFORM" --desugared-lib "$B/desugar.json" --pg-conf "$B/desugar-keep.pro" --output "$B/library-dex" "$DESUGAR_JAR" "$DESUGAR_CONFIG_JAR"
cp "$B/resources.apk" "$B/unsigned.apk"
(cd build/runtime-deps/native && zip -q -0 -r ../../standalone/unsigned.apk lib)
if [ -f "$B/library-dex/classes.dex" ]; then
  DEX_INDEX=2
  while [ -f "$B/dex/classes${DEX_INDEX}.dex" ]; do DEX_INDEX=$((DEX_INDEX+1)); done
  cp "$B/library-dex/classes.dex" "$B/dex/classes${DEX_INDEX}.dex"
fi
(cd "$B/dex" && zip -q ../unsigned.apk classes*.dex)
"$BT/zipalign" -P 16 -f 4 "$B/unsigned.apk" "$B/aligned.apk"
if [ ! -f "$B/development.keystore" ] && [ -f development/debug.keystore ]; then cp development/debug.keystore "$B/development.keystore"; fi
if [ ! -f "$B/development.keystore" ]; then keytool -genkeypair -keystore "$B/development.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=OpenRun Development,O=OpenRun,C=ZA' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1; fi
"$BT/apksigner" sign --ks "$B/development.keystore" --ks-pass pass:android --out dist/OpenRun-1.7.1-pending.apk "$B/aligned.apk"
"$BT/apksigner" verify --verbose dist/OpenRun-1.7.1-pending.apk
mv dist/OpenRun-1.7.1-pending.apk dist/OpenRun-1.7.1-debug.apk
sha256sum dist/OpenRun-1.7.1-debug.apk
