# OpenRun 0.4 validation — 17 October 2026

This report covers the recovered 0.3 baseline plus the 0.4 strength feature. Historical 0.3 runtime results are retained separately in `docs/VALIDATION-0.3.md` and are not counted as fresh 0.4 results.

## Executed for 0.4

| Check | Result |
| --- | --- |
| APK compile, resource link and DEX build | PASS — JDK 17, SDK/build-tools 35, standalone AAPT2/D8/L8 build |
| APK signature verification | PASS — v2 and v3 signature schemes |
| Plan invariants | PASS — 84 configurations |
| GPS/coaching/adaptive regression suite | PASS — 29 assertions |
| Pace/structured-workout/review suite | PASS — 96 assertions |
| GPX/TCX host parser suite | PASS — 10 assertions |
| Storage/backup host suite | PASS — 26 assertions, including strength persistence, unknown-routine rejection, invalid-backup rejection and round trip |
| Android instrumentation | PASS — 36 assertions on an API 26 x86_64 software emulator |
| Android UI flow | PASS — onboarding, Today and strength session completion; layouts and saved completion reviewed |
| Fresh 0.4 GPS save/restart flow | NOT COMPLETED — later software-emulator boots remained offline |
| Gradle/lint | Not completed for 0.4: wrapper distribution fetch failed in the runtime's Java networking path |

`tests/results-0.4/` contains the fresh host-test output. Domain assertions and host adapters do not establish real-device reliability. The Android instrumentation suite includes four new strength assertions in addition to the recovered 32 assertions.

APK SHA-256: `eb116eaacb72b96638e11420253a746d49cc5f82b4d2834f3959a37d6c3b8a05`.

## Runtime limits observed in this session

The first API 26 software-emulator boot installed the final APK and test APK and passed 36 Android assertions. Onboarding, Today, strength selection, the exercise checklist and saving a completed strength session were exercised. The Today and strength-workout screenshots were visually reviewed. The initial UI driver's final count assertion used wording that had changed during a copy edit; the captured UI showed the correct saved session. This automation assertion is not counted as a full end-to-end pass.

Subsequent emulator starts stalled with the device offline; one retry also encountered an AVD lock. The final extended GPS/pause/resume/save/restart pass was abandoned after these environment failures. No fresh 0.4 result is claimed for that flow. GPS service behavior is unchanged from the recovered 0.3 implementation, whose historical validation remains separately documented. This is not evidence of physical GPS, speech or wearable performance.

## Installation identity

Package `app.rwun`, version 0.4.0 / code 4, minimum Android 8 (API 26), target Android 15 (API 35). The previous debug signing identity was not recoverable after workspace cleanup. Export a full backup from an existing 0.3 installation before uninstalling it; install 0.4 and restore the backup. The development-only 0.4 key is preserved in `development/` for future local updates and must never be used for production.

## Remaining product and verification gaps

- Automatic two-way Garmin Connect sync is unimplemented. Official partner approval, API credentials/documentation, user consent and a deployed HTTPS integration are required. Manual GPX/TCX import and BLE HR are separate features.
- OpenRun provides a simplified original UI and local coaching rules. It does not reproduce Runna's full navigation, server-side algorithms, content library, community or support services.
- There is no cloud account, backend synchronization or conflict resolution. Local storage, routes and portable backups replace the core single-device data workflows.
- Strength includes two bodyweight routines and session tracking, not a coach-reviewed adaptive strength library or instructional videos.
- Physical outdoor GPS accuracy, Samsung screen-off/battery behavior, audio/music routing, physical Garmin/HR pairing, newer Android runtimes, accessibility and long-duration reliability still require validation.
- Coaching heuristics have automated invariants but are not physiologically validated training prescriptions.
- REA's APK analysis failed its sandbox process ownership check. JADX and Hermes fallback findings are documented without claiming a successful REA decompilation.

No Runna APK, application code, paid plan content or assets are distributed in these deliverables.
