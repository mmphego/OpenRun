# OpenRun 1.3.0 validation

## Passed locally

- Standalone API 36 build/desugaring, APK alignment and development-signature verification. Version code 17, min API 26, target API 36.
- Android instrumentation APK compilation, including registration of the new race-planning activity. No execution claim.
- New race domain/catalogue/storage suite: **1,929 assertions**. Includes a 24-configuration distance/duration matrix, selected weekdays, unique sessions, race-week caps, unsupported/too-soon goals, inactivity/fatigue/pain/unknown-history handling, initial baseline, preserved history, exact preview persistence, stale previews after corrections, one-time evidence reductions, atomic rejection of target mismatch, held-plan creation and race/receipt backup round trips.
- Existing focus/migration suite: 29 assertions; storage/backup: 49; v1.0.3: 63; v1.0: 1,682; plan invariants: 84 configurations; feature: 29; workout/coaching v0.3: 96; navigation/error: 10; recording start: 68; coaching library: 27.
- Server: **14 tests**, including actual local HTTP catalogue filtering/auth/consent/missing-key responses, feed validation/cache/backoff, bounded summaries, provider transport contract, quota and failed/partial response rejection. Gemini transport is mocked; no live provider request is claimed.

## Not tested here

No Android target was attached (`adb devices -l` empty). S23/native-screen layout, taps, keyboard, back/rotation, audio, real feed refresh, permission dialogs, screen-off recording, hardware steps, accessibility and performance remain pending. The S23 kit has a specific race-flow acceptance checklist. No Gradle lint, remote CI, deployed backend, nationwide provider integration or live Gemini pass is claimed.

The new catalogue cache uses Android AtomicFile and validates before replacement. Its disk-failure behavior is not hardware-tested. The service uses a bounded operator-provided normalised feed; no vendor-specific response mapper or national-coverage claim is made. Secrets are not bundled in the APK.

The original training thresholds and plans need qualified coaching review. Tests establish implemented behavior, not race readiness, clinical safety or equivalence to Runna's coaching engine.
