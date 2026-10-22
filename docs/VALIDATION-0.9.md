# OpenRun 0.9 — executed validation

- Coaching: 27 domain assertions passed, including hold precedence, missing benchmark, workout intent, unreported feedback, fatigue/pain handling, input bounds and category/search behavior.
- Storage and backup: 49 assertions passed. Added regression: ordinary adaptation cannot overwrite an existing pain hold; explicit resume remains available.
- Planner: 84 configurations; GPS/adaptation: 29 assertions; workout/pace/coaching: 96 assertions passed.
- V0.8 feature regression matrix: 54,226 assertions passed.
- GPX/TCX: 10 assertions; FIT: 7 assertions; navigation/error fallback: 10 assertions passed.
- Server: 5 HTTP test cases passed. Provider interactions use fixtures; no live credentials or deployed service were used.
- Clean Java 17 / Android API 35 resource, Java compilation, desugaring and APK signing passed. V2/V3 signatures verified. Development certificate SHA256: e6c56f851a997e1d01f45227a572d219fa231411407de511c8a1e58470d72ff3.
- APK SHA256: e03c244295ea5a331c1fff019e0ac3abeaedb93dc61fa51bc9791af5ac9efc76.
- Android instrumentation APK compiled successfully, but was not run. CI includes the coaching test suite; CI itself has not run here.

No Android runtime or working emulator is available in this environment. Actual screen rendering, accessibility, TTS voice readiness/playback, background recording, Health Connect and physical-device/provider integrations remain unverified. Source-informed content has not undergone qualified coach review. Passing these tests does not establish clinical safety or Runna-equivalent coaching quality.
