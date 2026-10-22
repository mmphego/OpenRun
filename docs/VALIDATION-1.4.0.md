# OpenRun 1.4.0 validation · 18 October 2026

## Passed

- Standalone API 36 compilation/dexing with JDK 21 and pinned R8 9.5.26; native libraries and notices packaged. APK alignment, version and retained development certificate checked. The test APK also compiles; it is not executed on a device. Two compiler warnings concern unused JVM-only coroutine debug-agent types; Kotlin metadata errors were resolved by the compiler update.
- Five **Robolectric 4.17 Native Graphics** tests drive the actual Android Views: goal → race search → manual form, disabled/valid manual entry, date/distance picker, automatic forward transitions, Back, rotation, non-race preview, Plan return hub and Offline Coach discovery. Twelve host native renders were captured and visually inspected, including 160% font. An image-intrinsic sizing error found during inspection was fixed and guarded by an assertion.
- Offline model integrity/prompt suite: 9 assertions. Empty, truncated/wrong and cancelled imports are refused; revision/checksum and bounded prompt contract checked. No successful 557 MiB import is claimed without the real model.
- Race/catalogue/storage suite: 1,943 assertions. Includes selected weekdays, race-week caps, stale previews, exact saved schedules, reductions, holds, unknown history and new non-race target removal/history preservation.
- Focus/migration 29; storage/backup 49; v1.0.3 63; v1.0 1,682; navigation/error 10; recording start 68; coaching library 27. Plan invariants cover 84 configurations; feature 29 and workout/coaching 96 assertions.

Native renders are **host-side evidence**, not emulator screenshots or S23 acceptance. The test harness supplies a tracking-service binder for UI navigation; no GPS, step-sensor or recording-service lifecycle behavior is established by it.

## Test Android Apps

The requested Test Android Apps Android Emulator QA skill was used. API 35 x86_64 and API 28 software-emulation attempts did not produce a boot-ready adb target in this environment (no KVM; startup crashes/hangs). Log excerpts are retained with evidence. No successful adb install/launch/tap/logcat flow is claimed. The non-destructive S23 kit uses UI-tree-derived taps and captures model/build, UI, logcat, frames and memory without clearing real app data.

## Not passed

- Physical S23 / One UI UI, TalkBack/200% fonts, permission flows, notifications, screen-off GPS/steps, recovery, calls/music, thermal/battery/performance and production-device upgrade.
- Actual Gemma generation, successful real-model import, ARM64 JNI loading, model quality or latency. The publisher's unauthenticated model endpoint returned HTTP 401; weights were not downloaded and the licence gate was not bypassed. The UI exposes publisher download/import and does not claim an installed model.
- Gradle lint, remote CI, Play release/signing, live Health Connect/Garmin, live Gemini, nationwide race feed or hosted backend. Unchanged backend behavior is covered by the earlier release's tests, not a new live-provider pass.
- Independent qualified review of training rules or generated advice. UI/host tests do not establish parity with Runna's coaching engine.

The design-canvas CLI remained unavailable after automatic approval review rejected its earlier unrequested telemetry. No retry/bypass was attempted; native implementation and host rendering supplied the visual evidence.
