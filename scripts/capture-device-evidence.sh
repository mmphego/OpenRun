#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to an authorised adb device}"
: "${RWUN_EVIDENCE_DIR:?Set RWUN_EVIDENCE_DIR to a private local output directory}"
mkdir -p "$RWUN_EVIDENCE_DIR"
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk > "$RWUN_EVIDENCE_DIR/api.txt"
adb -s "$ANDROID_SERIAL" shell dumpsys package app.rwun > "$RWUN_EVIDENCE_DIR/package.txt"
adb -s "$ANDROID_SERIAL" shell dumpsys gfxinfo app.rwun > "$RWUN_EVIDENCE_DIR/frames.txt"
adb -s "$ANDROID_SERIAL" shell dumpsys meminfo app.rwun > "$RWUN_EVIDENCE_DIR/memory.txt"
rwun_pid=$(adb -s "$ANDROID_SERIAL" shell pidof -s app.rwun | tr -d '\r')
if [ -n "$rwun_pid" ]; then adb -s "$ANDROID_SERIAL" logcat -d --pid="$rwun_pid" > "$RWUN_EVIDENCE_DIR/app-logcat.txt"; fi
printf 'Evidence captured. Review for private information before sharing.\n'
