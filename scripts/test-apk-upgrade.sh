#!/usr/bin/env bash
# Only operates on a newly created disposable CI emulator. Never targets a phone.
set -euo pipefail
[ "${GITHUB_ACTIONS:-}" = true ] || { echo 'Only run this test on a disposable GitHub Actions runner.'; exit 1; }
if adb -s emulator-5554 get-state >/dev/null 2>&1; then echo 'An emulator already exists; refusing to touch it.'; exit 1; fi
: "${ANDROID_HOME:?Set ANDROID_HOME}"
sdkmanager 'emulator' 'system-images;android-35;default;x86_64'
printf 'no\n' | avdmanager create avd --name openrun-upgrade --package 'system-images;android-35;default;x86_64' --force
if [ -e /dev/kvm ]; then sudo chmod 666 /dev/kvm; fi
mkdir -p build/reports/upgrade
"$ANDROID_HOME/emulator/emulator" -avd openrun-upgrade -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > build/reports/upgrade/emulator.log 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT
timeout 120 adb -s emulator-5554 wait-for-device
booted=false
for attempt in $(seq 1 120); do
  if [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then booted=true; break; fi
  sleep 2
done
"$booted" || { echo 'Emulator failed to boot'; exit 1; }
adb -s emulator-5554 install build/upgrade/baseline.apk
adb -s emulator-5554 install -t build/upgrade-test.apk
for phase in seed verify; do
  if [ "$phase" = verify ]; then
    candidate=(release-assets/*.apk)
    # No uninstall, clear-data, downgrade or signature bypass is allowed.
    adb -s emulator-5554 install -r "${candidate[0]}"
  fi
  adb -s emulator-5554 shell am instrument -w -e upgradePhase "$phase" app.rwun.test/app.rwun.SmokeInstrumentation | tee "build/reports/upgrade/$phase.txt"
  grep -q 'INSTRUMENTATION_RESULT: upgradeCheck=passed' "build/reports/upgrade/$phase.txt"
done
printf 'Published APK updated in place; synthetic activity, route, hold, preferences, ledger and SQLite sentinel retained.\n'
