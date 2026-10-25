#!/usr/bin/env bash
# Only operates on a newly created disposable CI emulator. Never targets a phone.
set -euo pipefail
[ "${GITHUB_ACTIONS:-}" = true ] || { echo 'Only run this test on a disposable GitHub Actions runner.'; exit 1; }
if adb -s emulator-5554 get-state >/dev/null 2>&1; then echo 'An emulator already exists; refusing to touch it.'; exit 1; fi
: "${ANDROID_HOME:?Set ANDROID_HOME}"
# avdmanager and emulator can otherwise use different Android user homes in CI.
: "${RUNNER_TEMP:?Set RUNNER_TEMP}"
export ANDROID_USER_HOME="$RUNNER_TEMP/openrun-android-user"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME" build/reports/upgrade
sdkmanager 'emulator' 'system-images;android-35;default;x86_64'
printf 'no\n' | avdmanager create avd --name openrun-upgrade --package 'system-images;android-35;default;x86_64' --device pixel_2 --path "$ANDROID_AVD_HOME/openrun-upgrade.avd" --force
# Fail before launching if the SDK wrote an AVD somewhere unexpected.
test -s "$ANDROID_AVD_HOME/openrun-upgrade.ini"
"$ANDROID_HOME/emulator/emulator" -list-avds | grep -Fx openrun-upgrade
if [ -e /dev/kvm ]; then sudo chmod 666 /dev/kvm; fi
mkdir -p build/reports/upgrade
"$ANDROID_HOME/emulator/emulator" -avd openrun-upgrade -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > build/reports/upgrade/emulator.log 2>&1 &
emulator_pid=$!
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    cat build/reports/upgrade/emulator.log
    timeout 10 adb -s emulator-5554 logcat -d > build/reports/upgrade/logcat.txt 2>&1 || true
  fi
  kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
booted=false
for attempt in $(seq 1 120); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo '::error::Emulator exited before boot. See emulator.log.'
    exit 1
  fi
  if [ "$(timeout 5 adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" = 1 ]; then booted=true; break; fi
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
  timeout 120 adb -s emulator-5554 shell am instrument -w -e upgradePhase "$phase" app.rwun.test/app.rwun.SmokeInstrumentation | tee "build/reports/upgrade/$phase.txt"
  grep -q 'INSTRUMENTATION_RESULT: upgradeCheck=passed' "build/reports/upgrade/$phase.txt"
done
printf 'Selected baseline installed and reinstalled in place; synthetic activity, route, hold, preferences, ledger and SQLite sentinel retained.\n'
