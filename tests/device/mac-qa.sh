#!/usr/bin/env bash
# Local Mac entry point. No account required; never clears app data.
set -euo pipefail
QA_DIR="$(cd "$(dirname "$0")" && pwd)"
RWUN_SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="${ADB:-$RWUN_SDK/platform-tools/adb}"
RWUN_APK="${RWUN_APK:-$QA_DIR/OpenRun-1.4.4-debug.apk}"
RWUN_QA_OUTPUT="${RWUN_QA_OUTPUT:-$QA_DIR/evidence}"
export ADB RWUN_APK RWUN_QA_OUTPUT
mkdir -p "$RWUN_QA_OUTPUT"
if ! command -v python3 >/dev/null; then echo 'Install Python 3.10 or newer first.' >&2; exit 2; fi
python3 -c 'import sys; assert sys.version_info >= (3, 10), "Python 3.10+ required"'
case "${1:-}" in
  emulator)
    SDKMANAGER="$RWUN_SDK/cmdline-tools/latest/bin/sdkmanager"
    if [[ ! -x "$SDKMANAGER" ]]; then
      echo 'In Android Studio: Settings > Android SDK > SDK Tools > install Android SDK Command-line Tools (latest).' >&2
      echo 'Set ANDROID_SDK_ROOT if your SDK is not in ~/Library/Android/sdk.' >&2
      exit 2
    fi
    case "$(uname -m)" in arm64) RWUN_ABI=arm64-v8a ;; x86_64) RWUN_ABI=x86_64 ;; *) echo 'Unsupported Mac architecture.' >&2; exit 2 ;; esac
    "$SDKMANAGER" --sdk_root="$RWUN_SDK" 'platform-tools' 'emulator' "system-images;android-36;default;$RWUN_ABI"
    # New evidence path prevents an older successful run from surviving a blocked launch.
    RWUN_UI_RUN="$RWUN_QA_OUTPUT/ui-$(date -u +%Y%m%dT%H%M%SZ)"
    rm -f "$RWUN_QA_OUTPUT/result.json"
    RWUN_RESULT=0
    python3 "$QA_DIR/emulator_runner.py" --sdk "$RWUN_SDK" --apk "$RWUN_APK" --output "$RWUN_UI_RUN" || RWUN_RESULT=$?
    if [[ -f "$RWUN_UI_RUN/result.json" ]]; then cp "$RWUN_UI_RUN/result.json" "$RWUN_QA_OUTPUT/result.json"; fi
    exit "$RWUN_RESULT"
    ;;
  s23-install|s23-walk|s23-finish)
    if [[ ! -x "$ADB" ]] && ! command -v "$ADB" >/dev/null; then echo 'Install Android platform-tools in Android Studio first.' >&2; exit 2; fi
    : "${ANDROID_SERIAL:?Connect the S23 with USB debugging; set ANDROID_SERIAL from adb devices -l}"
    if [[ "$1" == s23-install ]]; then
      echo 'Save any active OpenRun session before proceeding. This command updates the APK without clearing data.'
      bash "$QA_DIR/run.sh"
    elif [[ "$1" == s23-finish ]]; then
      python3 "$QA_DIR/s23_locked_walk.py" --adb "$ADB" --serial "$ANDROID_SERIAL" --apk "$RWUN_APK" --output "$RWUN_QA_OUTPUT" --finish
    else
      if [[ ! -f "$RWUN_QA_OUTPUT/physical-review.json" ]]; then
        cp "$QA_DIR/physical-review.json" "$RWUN_QA_OUTPUT/physical-review.json"
        python3 - "$RWUN_APK" "$RWUN_QA_OUTPUT/physical-review.json" <<'PY'
import hashlib,json,sys
from pathlib import Path
p=Path(sys.argv[2]);data=json.loads(p.read_text())
data['apk_sha256']=hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest()
p.write_text(json.dumps(data,indent=2)+'\n')
PY
      fi
      python3 "$QA_DIR/s23_locked_walk.py" --adb "$ADB" --serial "$ANDROID_SERIAL" --apk "$RWUN_APK" --output "$RWUN_QA_OUTPUT"
    fi
    ;;
  gate)
    python3 "$QA_DIR/release_gate.py" --apk "$RWUN_APK" --evidence "$RWUN_QA_OUTPUT" --output "$RWUN_QA_OUTPUT/release-gate.json"
    ;;
  *) echo 'Usage: bash mac-qa.sh {emulator|s23-install|s23-walk|s23-finish|gate}' >&2; exit 2 ;;
esac
