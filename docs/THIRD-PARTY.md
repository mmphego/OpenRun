# Third-party components

The Android app uses Android framework APIs. SDK build tools (AAPT2, D8/R8/L8, zipalign, apksigner) are external build dependencies, not OpenRun source. Google desugar_jdk_libs 2.1.4 and its configuration are used by the standalone build under their published licenses; source: https://github.com/google/desugar_jdk_libs.

REA (MIT) and JADX are analysis tools and are not linked into the app. hermes-dec (AGPL) was used as a separate offline analysis tool for string-table inspection; no hermes-dec implementation or analyzed Runna code is included in the OpenRun app.

The source archive retains the Gradle wrapper and its upstream licensing headers. Original OpenRun implementation and exercise text are separate from the inspected APK; no Runna assets or training plans are distributed.

## Material Symbols

24 Google Material Symbols Rounded Android XML vectors were downloaded from the official `google/material-design-icons` repository on 17 October 2026. Their source URLs and unmodified-content hashes are in `docs/icons/material-symbols.json`; their Apache 2.0 license is retained in `docs/icons/LICENSE`. The XML tint reference was adapted from AppCompat's `?attr/colorControlNormal` to the Android framework's `?android:attr/colorControlNormal`; runtime tinting follows the active light/dark palette.

OpenRun's original launcher mark is preserved in adaptive and monochrome launcher layers. No Runna assets were added. An original generated Cape Town illustration is used in 1.4.0.

## Retired local inference

The 1.4.0 implementation linked LiteRT-LM Android 0.18.0. PR #27 removes the
runtime dependency, native inference service, model manager and standalone runtime
download/lock files. OpenAI and Gemini BYOK now provide AI coaching over their
official HTTPS APIs without an additional Android inference dependency.

The previous runtime's licence and complete third-party notices remain unchanged in
`app/src/main/assets/licenses` for provenance. They do not indicate that LiteRT is
still linked. Room's transitive runtime dependencies remain resolved through its
normal dependency graph. Historical Gemma setup/release notes describe old versions;
model weights have never been distributed by this repository.

The original generated Cape Town cover (`res/drawable-nodpi/race_cape_town.png`) is illustrative location artwork, not a race photograph or organiser endorsement. No Runna logos, screenshots, photographs, participant counts or training programmes are bundled.

Robolectric 4.17, JUnit and instrumented Android framework/native graphics are host test dependencies only and are not packaged in the Android app.
