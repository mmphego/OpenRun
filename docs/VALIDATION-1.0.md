# OpenRun 1.0 — validation performed

## Passed in this environment

- Clean resource linking, Java 17 compilation and D8/L8 desugaring against Android API 36, with minimum API 26 and target API 36.
- APK signature verification with the retained development certificate. Certificate SHA256: e6c56f851a997e1d01f45227a572d219fa231411407de511c8a1e58470d72ff3.
- Final APK SHA256: f2a27f1b42322304560ca64d0202ea2b956ba580e66924e4c53efda517f17987.
- V1 host suite: 1,682 assertions. Covers all run-count/start-weekday/onboarding-mode combinations, exact preview-to-persisted schedules, history/hold preservation, pending-recording protection, mixed-activity spacing, date windows, malformed/oversized numeric input, per-day entry bounds, measurement opt-in/default-off, 90-day pruning, backup exclusion and restore-consent reset.
- Storage/backup regression: 49 assertions. Includes pain-hold preservation, schedule/history round trips and concurrent repository mutation.
- Existing planner invariants: 84 configurations; GPS/adaptation: 29 assertions; workout/pace/coaching: 96 assertions.
- V0.8 feature matrix: 54,226 assertions.
- Coaching guide rules/search: 27 assertions.
- GPX/TCX: 10 assertions; FIT: 7 assertions; navigation/error handling: 10 assertions.
- Optional service: 5 HTTP test cases. Provider replies are simulated; no live Strava/Garmin credentials or deployed service were used.
- Android instrumentation APK compiled. It includes Android JSON/storage/backup checks, first-plan preview equality, local-metrics backup exclusion, native activity-card dimensions, widget inflation and tab layout at three font scales. It was not executed.
- Original Play artwork exported at 512 × 512 and 1024 × 500; feature graphic visually inspected for clipping and branding. Artwork inspection is not app UI verification.

## Not passed or not run

No adb target, Android emulator or /dev/kvm is available in this environment. Real-device GPS, screen-off continuity, battery impact, calls/music/audio focus, widget interaction, card export to actual document providers, TalkBack/large-font navigation, Health Connect and Bluetooth behavior remain unverified. The prepared API 26/29/35/36 CI workflows have not run remotely. Gradle/lint, production App Bundle generation and Play pre-launch reports were not run here; this APK used the standalone SDK build.

There has been no qualified review of coaching/training rules, recruited-user beta, measured retention improvement, official Garmin activation, deployed cloud/Strava test or Play publication. No top-10 ranking or featuring outcome is claimed.

Read docs/launch/DEVICE-TESTS.md and COACH-REVIEW.csv before treating this beta as a production release.
