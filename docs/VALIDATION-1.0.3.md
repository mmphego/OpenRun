# OpenRun 1.0.3 validation — 2026-10-18

## Executed locally

- New domain/storage regression suite: 63 assertions covering monotonic timer/fractional checkpoint preservation, pause/reboot, step baseline/reset/stale samples/resume exclusion, calibration bounds, no assumed stride, reminder quiet hours/snooze/skip/DST, race anchoring, no catch-up or increased proposal mileage, stale preview rejection, demanding-session separation, explicit matching, duplicate suggestions, GPS split interpolation/gaps, undo, indoor provenance/mirror correction/deletion/restoration, walking totals separation, pain-hold preservation and backup consent stripping.
- Existing tests: 84 plan configurations; 29 feature, 96 workout/pace, 49 storage/backup, 10 navigation/error, 68 recording-start, 54,226 v0.8, 27 coaching, 1,682 v1.0, 10 GPX/TCX and 7 FIT assertions. Five service tests pass.
- Clean standalone Android API 36 build, Java 17, Build Tools 35.0.0, core-library desugaring. APK v2/v3 signature and alignment checked; development signing identity retained.
- Android smoke instrumentation compiled, including indoor/calibration/component registration cases. It has not run on an Android runtime.
- S23 test/evidence scripts syntax checked. `adb devices -l` returns no targets. No emulator or physical S23 test pass is claimed.

## Not executed / release gates

No actual UI screenshots, TalkBack/large-font/rotation device results, permission dialogs, Android 16 foreground health service launch, hardware step accuracy, screen-off sensor delivery, Samsung battery profile, app power loss, calls/music interaction, Health Connect integration, Garmin API sync or CI emulator results were collected. Host tests do not substitute for those checks. UI drafts and permission fallback behavior are implemented but need device confirmation.

Use tests/s23/run.sh and CHECKLIST.md with a connected S23. Run the supplied CI API matrix and Gradle lint before production. A qualified coach must review training/coaching, and the owner must complete production signing, Play declarations/closed testing, public privacy/support and official provider registrations. There is no Top 10 ranking guarantee.
