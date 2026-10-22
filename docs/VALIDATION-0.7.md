# OpenRun 0.7 validation

## Executed checks

- Build: Java 17, Android 35 resources/API, D8 and signed APK; v2/v3 signature verification.
- Planner: 84 configurations.
- GPS/adaptive feature tests: 29 assertions.
- Workout/pace/coaching tests: 96 assertions.
- GPX/TCX parser: 10 assertions.
- Storage/backup: 38 assertions, including restart completion IDs, saved history/profile, safety hold preservation, clearing moved/skipped schedule, blocking a pending recording, and Health import identity through backup.
- Settings/error regressions: 10 assertions, including a SecurityException from the Health Connect action, fallback to system settings, both actions blocked/missing, actionable numeric validation and sanitizing platform errors.
- Android instrumentation APK compiles with new native tab layout tests for font scales 1.0, 1.3 and 2.0. Compilation is not a runtime pass.

## Health Connect correction

The reported error occurred after launching android.health.connect.action.MANAGE_HEALTH_PERMISSIONS on the user's Samsung device. The implementation now uses android.health.connect.action.HEALTH_HOME_SETTINGS, matching AndroidX's public settings action. Launch denial/missing activity falls back to ordinary Settings, then manual instructions. No privileged permission is requested.

Primary sources:
- https://android.googlesource.com/platform/frameworks/support/+/HEAD/health/connect/connect-client/src/main/java/androidx/health/connect/client/HealthConnectClient.kt
- https://developer.android.com/health-and-fitness/health-connect/migration/android-13-to-14

## Device acceptance

- Update from 0.6 without uninstalling; verify history and current plan.
- Plan hub → Continue; Back → hub; Restart → Cancel; Restart → confirm retains old activities and any hold.
- Start new plan → four steps → preview → save. Back/cancel must retain the previous plan until save.
- Progress Workouts/Performance at normal and large fonts: labels must remain single-line with selected state available to TalkBack.
- Health Connect → Manage access should open the public home/settings screen without exposing a Permission Denial stack. Revoke/read-grant both permissions and return; status must refresh.
- Recheck GPS, audio, Bluetooth HR and screen-off recording physically.

See evidence-0.7 for the actual emulator outcome; no current runtime/visual pass is implied by a successful APK build.

## Emulator outcome

The fresh software API 26 emulator did not finish booting within 240 seconds. It never reached app installation. No 0.7 UI, Android instrumentation or visual comparison pass is claimed. The settings fallback is verified through host regression tests; its exact Samsung screen transition remains unverified.
