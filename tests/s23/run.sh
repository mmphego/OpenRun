#!/usr/bin/env bash
# Non-destructive device evidence. Never clears app data or resets permissions.
set -euo pipefail
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the connected S23 adb serial}"
ADB="${ADB:-adb}"
KIT="$(cd "$(dirname "$0")" && pwd)"
OUT="${RWUN_QA_OUTPUT:-$PWD/rwun-s23-evidence}"
APK="${RWUN_APK:-$KIT/OpenRun-1.4.4-debug.apk}"
mkdir -p "$OUT"
"$ADB" -s "$ANDROID_SERIAL" get-state
model="$("$ADB" -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')"
case "$model" in SM-S911*|SM-S916*|SM-S918*) ;; *) echo "Connected model is $model, not a recognised S23/S23+/Ultra. No S23 pass will be claimed." >&2; exit 2;; esac
"$ADB" -s "$ANDROID_SERIAL" shell getprop ro.product.model > "$OUT/model.txt"
"$ADB" -s "$ANDROID_SERIAL" shell getprop ro.build.version.release > "$OUT/android.txt"
"$ADB" -s "$ANDROID_SERIAL" shell getprop ro.build.version.security_patch > "$OUT/security-patch.txt"
"$ADB" -s "$ANDROID_SERIAL" install -r "$APK"
"$ADB" -s "$ANDROID_SERIAL" shell cmd package resolve-activity --brief app.rwun > "$OUT/activity.txt"
"$ADB" -s "$ANDROID_SERIAL" shell am start -W -n app.rwun/.MainActivity > "$OUT/launch.txt"
"$ADB" -s "$ANDROID_SERIAL" exec-out uiautomator dump /dev/tty > "$OUT/launch-ui.xml"
"$ADB" -s "$ANDROID_SERIAL" exec-out screencap -p > "$OUT/launch.png"
"$ADB" -s "$ANDROID_SERIAL" shell dumpsys meminfo app.rwun > "$OUT/memory.txt"
"$ADB" -s "$ANDROID_SERIAL" shell dumpsys gfxinfo app.rwun > "$OUT/frames.txt"
"$ADB" -s "$ANDROID_SERIAL" shell dumpsys package app.rwun > "$OUT/package.txt"
if [[ "${RWUN_RUN_INSTRUMENTATION:-0}" == 1 ]]; then
  "$ADB" -s "$ANDROID_SERIAL" install -r "$KIT/OpenRun-1.4.4-tests.apk"
  "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w app.rwun.test/app.rwun.SmokeInstrumentation > "$OUT/instrumentation.txt"
  cat "$OUT/instrumentation.txt"
fi
pid="$("$ADB" -s "$ANDROID_SERIAL" shell pidof -s app.rwun | tr -d '\r')"
if [[ -n "$pid" ]]; then "$ADB" -s "$ANDROID_SERIAL" logcat --pid "$pid" -d > "$OUT/app-logcat.txt"; fi
printf 'Evidence saved to %s. Complete CHECKLIST.md before recording a pass.\n' "$OUT"
