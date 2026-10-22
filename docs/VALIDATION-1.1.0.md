# OpenRun 1.1.0 — validation, 2026-10-18

Executed locally:

- 46 cycling host assertions: separate totals, planned/actual linking and undo, duplicate protection, invalid payloads, route traversal, duration/HR/identity bounds, unknown/past plans, time-only indoor rides, speed calculation and different GPS thresholds, pause/gap filtering, moving-time/ascent provenance, cycling route backup/restore, atomic pending-session clearing and pain holds, calendar hard-ride conflicts and stale running-preview invalidation.
- GPX/TCX suite: 14 assertions; FIT suite: 9 assertions. Includes bike speed/elevation GPX round-trip, cycling FIT session totals, mismatched sport rejection and existing checksums/parser regression cases. These are fixtures, not a physical Garmin watch-file compatibility suite.
- Retained suites: 63 v1.0.3, 49 storage/backup, 1,682 v1.0, 10 navigation/error, 68 recording-start, 27 coaching, 54,226 v0.8, 29 feature, 96 workout/pace assertions and plan invariants across 84 configurations. Five service tests pass.
- Standalone Java 17 / Android API 36 APK build succeeds, with core library desugaring, APK alignment and v2/v3 signature verification. Public development certificate retained. No Gradle lint result is claimed.
- Android smoke instrumentation compiled including cycling component registration/calculation cases. Not executed on Android. CI configuration includes the cycling suite but the remote CI matrix has not run here.
- adb was installed and `adb devices -l` returned no attached targets. S23 evidence/test kit updated for cycling; scripts syntax checked, device flows NOT EXECUTED.

Not established:

Actual screen layouts, large fonts/TalkBack, permission/settings returns, Android 16 foreground startup, GPS distance/speed/elevation accuracy, Samsung screen-off recovery and battery use, Bluetooth heart rate, real Health Connect import/write/revision behavior, actual walking-pad accuracy and physical watch FIT files. The supplied kit gives acceptance steps, not fabricated results.

Production still needs S23 plus broader device/API acceptance, qualified coach review, real provider approval/credentials, service hosting if used, production signing and Play declarations/public privacy/support setup. Cycling scheduling uses transparent software rules, not clinically validated training-load prescriptions.
