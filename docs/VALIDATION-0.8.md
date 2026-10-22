# OpenRun 0.8 — executed validation

## Passed

- Clean Java 17 / Android API 35 resource and D8 build. APK v2/v3 signatures; retained 0.4–0.7 development certificate.
- Planner invariants across 84 configurations.
- GPS/adaptation: 29 assertions; workout/pace/coaching: 96 assertions.
- GPX/TCX parsing: 10 assertions; FIT decoding: 7 assertions including known CRC16 check value, session totals, HR/route points, stable identity, checksum damage, truncation and wrong sport.
- Navigation/error fallback: 10 assertions.
- Storage/backup: 48 assertions. New coverage includes frozen preferred-day schedules, current-week preservation, backup round trips, invalid-schedule rejection, restart dates, disabling speed work, new-plan reset, hold/history preservation and concurrent repository writes.
- V0.8 feature suite: 54,226 assertions across supported weekday combinations/programme modes, date/collision/long-run invariants, calendar escaping, GPS-distance boundary interpolation, pause/jump rejection, authenticated backup encryption/tamper/wrong-password cases, strength configuration and multi-session feedback handling.
- Account service: 5 HTTP test cases covering auth/session revocation, account isolation/deletion, conflict detection/concurrent uploads, missing-credential blockers, provider token encryption and single-use OAuth state (including concurrent callback attempts). Provider replies in OAuth tests are simulated; no live credentials were used.

## Not executed

Current Android UI/instrumentation and physical tests are not a pass. No Android runtime is running here and /dev/kvm is absent. The supplied API 29/35 CI workflow has not run. Native Health Connect reads/writes/background grants, Keystore behavior, JobScheduler, real Strava OAuth/upload, deployed HTTPS operation, map rendering and actual Garmin FIT compatibility need device/live validation.

The APK and host/server tests establish implementation/build correctness within their scope. They do not establish Runna-level coaching, usability, GPS accuracy or reliability.

See evidence-0.8 for logs. Earlier reports describe historical releases only.
