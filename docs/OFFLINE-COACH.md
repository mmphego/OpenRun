# Offline Coach · Gemma (1.4.0)

Coach → Offline Coach and a reviewed plan → Coach insight open the local model manager. The actual schedule is produced by OpenRun's existing history-aware rules; Gemma supplies an optional short explanation. It cannot save new workouts, change targets or clear a hold. Generated text still needs review.

## Install the model

1. Choose **Get Gemma model**, accept the publisher's licence/account requirements, and download the exact file below. No credentials or Hugging Face token are stored by OpenRun.
2. Choose **Import downloaded model** and select the file. OpenRun verifies its byte length and SHA-256 before atomic installation into private storage. Invalid files do not replace a valid installation.
3. Choose **Check local runtime**, then **Generate offline insight**. Runtime loading is a separate check; it does not prove successful generation. Remove the model here to reclaim storage.

The alternative **Try direct download** asks before contacting the publisher. At implementation time the unauthenticated publisher endpoint returned HTTP 401. The app explains the licence gate and offers the import route. Weights are not bundled and no licence gate is bypassed.

| Item | Pinned value |
|---|---|
| Model | Gemma 3 1B instruction-tuned INT4 |
| Publisher | https://huggingface.co/litert-community/Gemma3-1B-IT |
| Revision | `d448835d84411a08f577e25f3afebe528402d73b` |
| File | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Bytes | 584,417,280 (about 557 MiB) |
| SHA-256 | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` |
| Runtime | LiteRT-LM Android 0.18.0, ARM64 / x86_64 |
| Backend | CPU, 2 threads; context 1,024, output cap 192 tokens |

Leave at least 700 MB free for installation. The model is excluded from backups. The runtime refuses generation with an active/pending recording, Android low-memory status or less than 1.5 GiB reported available memory. This check is not a device compatibility guarantee.

Generation runs only while its screen remains open, in a non-exported `:offlinecoach` app process. Leaving/cancelling stops that process; the recorder uses the main app process. A timeout stops a stalled request. Transfers verify before replacement, and obsolete callbacks are ignored after cancellation. A summary fingerprint prevents accepting stale history. No inference prompt is uploaded; optional cloud Gemini remains a separate explicit-consent action.

## What has been verified

Host integrity/cancellation/prompt tests, native Android model-manager rendering and APK packaging. No licence-accepted model was available here, so **live text generation, ARM64 JNI loading and S23 memory/latency/battery measurements are not passed**. Follow `tests/s23/CHECKLIST.md` before treating this as production-ready. 32-bit devices do not have a bundled inference runtime.

## Build

JDK 21 is required to read the runtime's class files; app Java source remains release 17. `runtime-lock.json` pins runtime artifacts and SHA-256 hashes. `build-tools-lock.json` pins R8/D8 9.5.26, replacing the older SDK compiler to support the runtime's Kotlin dependencies. `fetch-runtime.py` and `fetch-build-tools.py` download and verify those dependencies. Licences/notices are packaged as assets. The retained Gradle configuration also overrides the embedded R8 compiler; Gradle/lint/CI were not executed in this environment.

Upstream references: https://developers.google.com/edge/litert-lm/android , https://developer.android.com/build/kotlin-support , https://r8.googlesource.com/r8/+/refs/heads/main/README.md .
