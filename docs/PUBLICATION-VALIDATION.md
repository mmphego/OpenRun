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

## CI repair and fresh-checkout validation — 23 October 2026

The fresh checkout matched published baseline `54d551d3f06af9db80102f52dc5f5d5701f8933b`; no newer local implementation was present. Existing history, package `app.rwun`, storage identifiers and backup discriminator `Rwun` remain unchanged.

- Inspected [failed run 39835172203](https://github.com/mmphego/OpenRun/actions/runs/39835172203): SDK setup requested the removed `tools` package; the Gradle checksum was incorrect; skipped dependency installation caused the subsequent `cryptography` import failure.
- Both workflows now explicitly request `platform-tools` from `setup-android`. Python dependency installation in CI precedes SDK setup, so an Android setup failure cannot skip that installation.
- Retrieved Gradle's [official 8.11.1 binary distribution checksum](https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256): `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. Wrapper verification remains enabled.
- Reinstalled `server/requirements.txt` in a fresh Python 3.12 virtual environment: 14 service tests and 20 device-QA utility tests passed. All 11 standalone Java suites passed with Temurin JDK 21.
- CI verifies both Gradle debug APK signatures and 16 KB native-library ZIP alignment before app artifact upload. The [Actions workflow](https://github.com/mmphego/OpenRun/actions/workflows/ci.yml) is the source of truth for each commit's Android lint, Room tests, APK builds and downloadable artifacts; local Android Gradle builds were not rerun in this fresh environment without an SDK.
- Reviewed README commands against scripts, Gradle tasks, custom instrumentation runner and source. Corrected the architecture table to name `RwunApplication`. Local documentation links resolve. No signing keys, local databases, APKs, machine-specific configuration or recognised credential tokens were found in tracked source.
- ADB was installed and queried: no device or emulator was attached; `/dev/kvm` was absent. No installation, launch or physical recording pass is claimed.
- The existing five starter issues were inspected; none were duplicated. Repository description/topics cannot be updated through the available GitHub connector.

## Not established

- Inspect the exact pushed commit's Actions result and artifact; historical results do not establish a newer commit's status.
- No Android device or emulator was connected in this environment. APK installation and Samsung S23 background/GPS/sensor acceptance are not claimed.
- Release signing secrets are unavailable. No production signed tag release has been created.
- Garmin partner integration is not implemented; RunSignup needs approval/proxy configuration; live Strava/model/Health Connect compatibility is not established by mocked tests.

Historical UI images are retained as clearly labelled Android host renders, not device screenshots. Current real-device captures remain tracked in issue #2.
