# OpenRun 0.6 validation — 2026-10-17

## Completed

- Native Java 17/API 35 resource compilation and APK build with D8/desugaring.
- APK v2 and v3 signature verification; same retained development certificate as 0.4/0.5.
- Planner invariants: 84 configurations.
- GPS/adaptation feature tests: 29 assertions.
- Workout/pace/coaching domain tests: 96 assertions.
- Storage/backup host tests: 30 assertions, including Health Connect provenance and record IDs through backup/restore, duplicate ID rejection, and no accidental scheduled-workout completion.
- GPX/TCX parser host tests: 10 assertions.
- Official framework signatures checked against the local Android 35 API; Health Connect code compiles without a third-party health SDK.

## Not completed

A fresh API 26 software emulator exited during startup before app install. No 0.6 emulator flow, screenshot or instrumentation pass is claimed. The available API 26 image cannot validate Health Connect anyway. Health Connect permission grant, partial denial/revocation, actual provider import/pagination, empty/missing-distance data, and Android 14–16 runtime behavior need a compatible device. Host storage tests do not validate the platform service.

## Device acceptance checklist

1. Update 0.4/0.5 without uninstalling and confirm existing plan/history.
2. Check Today → workout → recorder, Calendar details/rescheduling, Progress tabs, Profile → Connections, system Back, and font scale 1.3+.
3. On Android 14+: Profile → Connected apps & devices → Health Connect → Enable. Grant exercise and distance; Sync now; compare source distance and elapsed duration. Sync twice and confirm record IDs are skipped.
4. Revoke either permission in Health Connect and return; partial/no-access state must appear and Sync now must disable. Restore access and retry. An error must leave saved data intact.
5. Confirm Garmin never shows an account as connected without credentials; test exported GPX/TCX and a compatible BLE HR broadcaster separately.
6. Verify outdoor GPS, screen-off persistence and audio on the target Samsung device.

## Sources

- https://developer.android.com/health-and-fitness/health-connect/get-started
- https://developer.android.com/reference/android/health/connect/HealthConnectManager
- https://developer.android.com/reference/android/health/connect/HealthPermissions
- https://android.googlesource.com/platform/frameworks/support/ (platform health permission contract delegates to ordinary multiple-permission requests on Android 14+)
- https://developer.garmin.com/gc-developer-program/overview/
- https://developer.garmin.com/gc-developer-program/program-faq/
