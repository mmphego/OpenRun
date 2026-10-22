# OpenRun 1.2.0 validation

## Passed locally

- Standalone Android API 36 compilation, D8 desugaring, APK alignment and signature verification.
- Instrumentation APK compilation; execution requires an Android target.
- New focus/migration suite: 29 assertions. Covers archived cycling/strength retention, unfinished cycling migration, preserved running checkpoints, walking/run separation, duplicate identities, missing walking distance, walking summary, delete/undo, pain holds and route-bearing backup round trips.
- Existing host suites: storage 49; v1.0.3 63; v1.0 1,682; planning 84 configurations; feature 29; workout 96; navigation 10; recording-start 68; coaching 27; v0.8 compatibility 54,226; GPX/TCX 14 and FIT 9. Server suite: 5 tests.

## Not executed

No Android target was attached (`adb devices -l` was empty). No S23, emulator UI, permission-dialog, screen-off, hardware step accuracy, battery, accessibility or performance pass is claimed. The S23 test kit includes both APKs, evidence scripts and an updated manual checklist. No Gradle lint or remote CI execution is claimed.

Native screens were implemented locally. Design-canvas CLI generation was blocked by automatic approval review because it attempted an unrequested PostHog telemetry connection; it was not retried.
