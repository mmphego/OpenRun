# Publication validation

The repository was imported from recovered Rwun 1.4.4 source without project Git metadata. OpenRun retains package `app.rwun` and the original backup discriminator/storage keys for compatibility. The target repository was inspected and empty before import; no unrelated history was overwritten.

## Completed locally

- All 11 standalone Java regression scripts passed before and after the visible-brand rename.
- Optional service: 14 Python tests passed.
- Device QA/evidence utilities: 20 Python tests passed; these validate utility logic, not a real phone session.
- Standard Gradle debug and custom-instrumentation APK compilation succeeded.
- Source allowlist review found no tracked keystore, local database, local.properties, APK, private key or recognised credential token.
- Third-party notices preserved; project-wide licensing remains explicitly unresolved.

## Completed Android checks

- `:app:lintDebug`: passed with 55 warnings and zero errors; warnings remain visible in reports. No baseline or disabled-error gate was introduced.
- `:app:testDebugUnitTest`: four Room/Robolectric tests passed.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed after compatibility fixes.
- The current LiteRT Kotlin metadata requires the pinned Android Lint 9.4.0 override; R8 is independently pinned for dexing.

## Not established

- Remote Actions/artifact results must be inspected after source publication.
- No Android device or emulator was connected in this environment. APK installation and Samsung S23 background/GPS/sensor acceptance are not claimed.
- Release signing secrets are unavailable. No production signed tag release has been created.
- Garmin partner integration is not implemented; RunSignup needs approval/proxy configuration; live Strava/model/Health Connect compatibility is not established by mocked tests.

Historical UI images are retained as clearly labelled Android host renders, not device screenshots. Current real-device captures remain tracked in issue #2.
