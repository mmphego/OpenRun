# OpenRun 0.3 validation — 17 October 2026

## Executed checks

| Check | Result |
| --- | --- |
| Clean Android app and test APK build | PASS — JDK 17, Gradle 8.9, Android SDK 35 |
| Android lint | PASS — no issues reported |
| APK signing and update certificate | PASS; certificate matches the supplied 0.2 APK |
| Plan invariants | PASS across 84 configurations |
| GPS / coaching / adaptive regression suite | PASS — 29 assertions |
| Pace / structured-workout / review suite | PASS — 96 assertions |
| GPX/TCX host parser suite | PASS — 10 assertions |
| Storage and backup host suite | PASS — 22 assertions using test adapters |
| Android instrumentation suite on API 26 | PASS — 32 actual Android storage, schedule, file and backup assertions |
| Install and launch of final APK on API 26 | PASS |
| Onboarding and pace targets | PASS — synthetic 30-minute 5 km benchmark, 24 km/week, 3 runs/week, 8 weeks; easy target displayed as 7:19–8:42 min/km |
| Recording with simulated GPS | PASS — nonzero filtered distance accumulated |
| Pause / resume | PASS — time and distance stayed fixed while paused; a large simulated movement during pause was not counted after resume |
| Save / force-stop / restart | PASS — recorded activity remained in history |
| Final update preserving history | PASS |
| Visual/UI review | Onboarding, workout, history screenshots/tree reviewed; focus, singular wording and pace formatting corrected |
| OpenRun app crash in captured logs | None observed |

The simulated run's elapsed time includes standing still during QA. Its pace is not a performance or GPS-accuracy benchmark. Tests used emulator coordinates, not a physical run. The structured GPS recovery change was driven by a failing regression test and then passed the new consistency/re-anchoring assertions.

## Environment failures and remaining limits

The API 30 software emulator suffered system-wide DeadSystemException failures before app installation. Its instrumentation and launch timeouts are not counted as passes. A lighter AOSP API 26 image booted and completed the Android tests and UI checks above.

The API 26 emulator's third-party Pico TTS native library crashed while synthesizing speech. OpenRun continued recording. Speech-error callbacks, failure return codes and disconnected-language checks now disable voice readiness; textual cues remain available. Actual audible playback on a physical phone has not been established. No claim is made that the native engine failure has been fixed inside Pico itself.

Physical outdoor GPS accuracy, Samsung screen-off/battery behavior, real audio/music routing, physical Garmin pairing, API 35/36 runtime behavior, full accessibility/rotation coverage, long-history performance, cloud synchronization and the physiological validity of training heuristics remain unverified or unimplemented. The app compiles against API 35 and has run on API 26; that is not a full Android compatibility matrix.

## Product boundary

This is a debug-signed beta with original code and coaching heuristics, not complete Runna parity. Automatic Garmin account/workout sync needs official API access. Strength/mobility programming, cloud accounts and validated race-specific coaching remain in PARITY.md. No Runna APK, code, assets or paid training content were modified or copied.

APK: OpenRun-0.3-debug.apk

SHA-256: `dc922792340a458a79af8f97cd09cf30fa5e40c075ca6de832a6e8d17a6a4c44`
