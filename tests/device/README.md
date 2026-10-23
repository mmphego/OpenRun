# OpenRun 1.5.0 device QA

The current candidate is OpenRun 1.5.0 / versionCode 24. A Samsung account is not required for these local tests. The emulator is AOSP Android 16 at 1080 × 2340, density 420; this approximates the S23 display and does not emulate Samsung One UI, its step sensor or its battery policies.

## 1. Emulator UI tests on your Mac

Install Android Studio, its SDK Command-line Tools (latest), and Python 3.10+. Extract the QA kit and run from its folder:

```bash
bash mac-qa.sh emulator
```

If Android Studio cannot find a Java runtime for sdkmanager, set JAVA_HOME to Android Studio's bundled `jbr/Contents/Home`. Set ANDROID_SDK_ROOT only if your SDK is outside `~/Library/Android/sdk`. Review/accept the Android SDK licence prompts on your own Mac. The script chooses ARM64 for Apple silicon and x86_64 for Intel, downloads the emulator image, boots it with hardware acceleration and runs UI checks.

Checks: first-use plan entry, race search/manual form validation, automatic advance after distance selection, plan preview without saving, 160% font navigation, and indoor walking entry. Screenshots, fresh UI trees and logs are collected. Taps use UI-tree bounds; controls are searched again after scrolling. A blocked boot or incomplete flow cannot become a passing result. Screenshot collection alone is not a visual/accessibility pass; inspect the rendered screens and complete CHECKLIST.md.

## 2. Physical S23 setup and upgrade

Save active recordings first. Enable Developer options → USB debugging on the S23; connect by USB and approve the Mac on the phone. Keep normal Samsung battery settings for the initial acceptance run; record any later changes separately.

```bash
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" devices -l
export ANDROID_SERIAL='YOUR_S23_SERIAL'
bash mac-qa.sh s23-install
```

Installation uses `adb install -r`, retaining app data. Do not uninstall OpenRun or clear its storage. If the signature differs, stop and preserve your backup; do not uninstall to force installation. Optional isolated-preference instrumentation is available through `RWUN_RUN_INSTRUMENTATION=1 bash mac-qa.sh s23-install`. Instrumentation does not prove physical walking accuracy.

## 3. First acceptance: locked-screen walking pad

1. In OpenRun, select **Start a walk → Walking pad · phone steps**. Enable Count phone steps and grant Physical activity/notification permissions. Start the session and confirm the step count increases with the phone in your pocket.
2. First do an independently counted 200-step check. Record observed/detected steps, pad speed and pocket placement. Our initial release target is no more than 10% step error. This is a product acceptance target, not a medical measurement claim.
3. Calibrate using the pad's known distance and detected steps from the same steady walk. Start a fresh walk for the uninterrupted screen-off test. Reset/note the pad distance and note the app estimate at the start.
4. Run the command below while the indoor walk is actively recording. It checks the installed APK hash, turns the display off, then collects about 31 minutes of samples. Put the phone in your pocket and keep walking at a comfortable steady speed. The Mac must remain awake and connected. The collector does not acquire an Android wake lock, turn the screen back on, change battery exemptions, inject steps or save a session.

```bash
bash mac-qa.sh s23-walk
```

5. Unlock manually and leave the **same walk actively recording**, with its step count/timer visible at the top. Run `bash mac-qa.sh s23-finish` to capture the end values. This captures evidence; it does not finish the app session. Note pad distance and app estimate **before correcting it**. Then finish using the recording notification. Verify the timer pauses and the review opens. Save once, then verify one Walks entry and unchanged running mileage. Repeat the warm/cold notification, pause/resume and interrupted-recovery checks in CHECKLIST.md as separate sessions.
6. Complete `evidence/physical-review.json`: named/date reviewer, actual 200-step measurements, calibrated pad/app distance deltas, and each check's result, notes and relative evidence paths. Use `passed` only after observation; retain `pending` or `failed` otherwise. Initial distance target: at most 15% error after personal calibration at the tested speed. Recalibrate/retest if it fails. The estimate is approximate and speed/placement dependent.

This is a USB-connected functional recording test. Charging and adb sampling can change power behaviour; it is **not** a battery/Doze acceptance pass. Repeat an unplugged locked-screen walk and the wider battery, reminders, outdoor GPS, Health Connect and accessibility checks before launch. The collector uses UI values before/after the walk and Android power/service diagnostics while locked. It does not access private preferences, inject steps, or prove per-step delivery while asleep. Human review must confirm the same session was kept running. Screenshots/logs may contain personal information: review before sharing.

## 4. Release gate

```bash
bash mac-qa.sh gate
```

Exit 0 means this recording/UI gate passed. Nonzero blocks release. Required: the exact APK hash on all evidence, complete emulator UI results, a physical S23 walk with 30+ sampled screen-off minutes and increasing endpoint steps/distance, timer drift ≤30 seconds, no partial/paused endpoints or absent-service samples, acceptable independent step/distance measurements, and reviewed same-session continuity, notification, pause/resume, single-save and recovery checks with evidence files. Missing, pending, failed or different-build evidence blocks. Repeat after recording-related code or APK changes.

The gate does not publish or install a production release, and does not approve production signing, Play declarations, coaching quality, Garmin activation, live race feeds or Gemma hardware performance. Wire its command as a required step in the eventual publishing pipeline. Until then, it is a local pre-release check, not an enforced store policy.

For candidate packaging, use the gated entry point below. It refuses to create an archive when evidence is pending/failed or belongs to a different APK, and never overwrites an existing archive. Normal debug builds remain available for testing fixes. This package clears only the recording/UI checks, not the other production launch requirements.

```bash
python3 package_candidate.py --apk OpenRun-1.5.0-debug.apk --evidence evidence --output recording-qa-cleared.zip
```

## Run from source

The source contains these tools under `tests/device/`. Put both APKs beside `mac-qa.sh` plus `run.sh`, `ui-tap.py` and CHECKLIST.md from `tests/s23/`, or use the assembled QA kit. No Samsung emulator skin is needed for automated screenshots.
