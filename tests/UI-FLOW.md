# Current UI acceptance flow: OpenRun 1.3.0

Use [the S23 checklist](s23/CHECKLIST.md) for the current running/walking, Plan, Coach and race-first flows. Device execution remains pending.

## Historical 0.4 flow (superseded; strength has been retired)

# OpenRun 0.4 UI acceptance flow

Use a dedicated Android emulator/test installation containing only synthetic data.

1. Install `OpenRun-0.4-debug.apk` and the Android test APK built from `SmokeInstrumentation` with the same signing key.
2. Run `adb shell am instrument -w app.rwun.test/app.rwun.SmokeInstrumentation` and require the final PASS count. The test preferences namespace is isolated from app history.
3. Launch `app.rwun/.MainActivity`. Complete onboarding with synthetic values.
4. Open Today → Strength & mobility → Runner foundation. Complete/tick each exercise and save. Require the completed-session count/history to change.
5. Force-stop/relaunch and verify the strength session persists. Inspect Plan, History and Settings.
6. Start a free run. Grant precise location. Supply emulator coordinates gradually (for example longitude 18.42, latitude -33.92; then small longitudinal increments over several seconds).
7. Confirm nonzero recorded distance, pause/resume controls, and a finish/save feedback flow. Save the activity, then force-stop/relaunch and verify it remains in History.
8. Inspect app crash logs and review screenshots/UI trees for clipping, usable controls and accurate state labels.

Derive tap coordinates from UI-tree bounds. Scroll controls fully into view before tapping. Software-emulator GPS does not establish physical accuracy, battery behavior or real audio/wearable reliability. Keep historical 0.3 validation separate from fresh 0.4 results.
