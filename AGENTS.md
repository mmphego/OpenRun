# OpenRun repository guidance

Repository: <https://github.com/mmphego/OpenRun>

## Purpose and scope

- Build a native Android app for running and walking: training plans, recording,
  audio coaching, progress, race discovery and explicitly configured integrations.
- Preserve OpenRun's original code, branding and Android architecture. Learn from
  reference apps without copying proprietary code, assets or backend dependencies.
- Cycling and strength code is retained for compatibility, outside the active scope.
  Do not delete archived user data or expand those features without a request.

## Start every task with repository evidence

1. Inspect the working directory, Git status, current branch, history and remotes.
   Preserve existing changes. If the checkout is missing, clone the repository.
2. Fetch the remote and compare local work with current `origin/main`. Inspect
   relevant open PRs when overlapping work could affect the task. Do not assume a
   previous session's branch, files, version or test results are still current.
3. Read applicable `AGENTS.md` files, [README.md](README.md) and
   [CONTRIBUTING.md](CONTRIBUTING.md), then the source and docs for the affected flow.
4. Establish the requested outcome, acceptance criteria and non-goals. Proceed
   autonomously within the authorized scope; ask only questions that block safe,
   correct implementation. State material assumptions and limitations plainly.

## Architecture and implementation

- Keep Java and native Android Views/framework services. The Java language level
  is 17; the documented build environment uses JDK 21. Avoid framework rewrites or
  new dependencies unless the task needs them.
- Apply SOLID pragmatically: keep UI presentation, domain rules, persistence and
  network/device adapters separate. Prefer small cohesive components and existing
  abstractions over adding more responsibilities to large activities.
- Main source: `app/src/main/java/app/rwun/`; resources: `app/src/main/res/`.
  `RunStore` holds training history; Room backs the separate race catalogue.
  The optional independently hosted service lives in `server/`.
- Keep core planning and recording usable offline. Never block startup or the UI
  thread on network, model inference or substantial file/database work.
- Preserve package ID `app.rwun`, storage keys, backup identifiers, external record
  IDs and upgrade compatibility. Change visible branding independently of these.
- Preserve user history, active-session recovery, pain holds and explicit review
  of training changes. AI explanations must not silently change workouts.

## Data and integration rules

- Prefer transactional, content-aware upserts over destructive full rewrites.
  Keep provider identity, saved user choices and training history separate.
- Preserve cached data on failed/partial fetches. Keep pagination, timeouts,
  retries and background jobs bounded; retain duplicate/concurrency protections.
- Running and walking totals stay separate. Missing distance, timestamps, routes
  or metrics stay unknown. Do not invent values, recording times or GPS tracks.
- Health Connect read/write consent is separate. Retain reviewed exports,
  duplicate review, stable upsert IDs and protection against re-exporting imported
  activities. Follow [docs/HEALTH-CONNECT.md](docs/HEALTH-CONNECT.md).
- Garmin file import is distinct from official account/workout sync. Report real
  approval, credential and service blockers rather than simulating integrations.
- Preserve explicit consent for optional data sharing, uploads and street maps.
  Do not upload personal routes or health data to validate a feature.
- For race ingestion changes, read [docs/RACE-SYNC.md](docs/RACE-SYNC.md).

## Verification

Use JDK 21, Python 3.12 and the Android SDK/Build Tools specified in the README and
CI. Keep SDK paths, proxy settings and credentials local; never disable TLS checks.
Install `server/requirements.txt` in a virtual environment for service checks.

Run focused checks while iterating. For changes affecting application behavior,
complete the relevant checks below; `bash scripts/check.sh` runs the full suite:

```sh
python3 scripts/run-host-tests.py
./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s server -p 'test_*.py' -v
python3 -m unittest discover -s tests/device -p 'test_*.py' -v
git diff --check
```

- Add meaningful regressions for domain rules, ingestion, migrations, persistence,
  recovery and permissions. Avoid tests that only repeat implementation details.
- For UI changes, render and inspect the actual native screens using synthetic
  data, including empty/missing-data states and larger accessibility fonts.
  See [tests/native/README.md](tests/native/README.md).
- Distinguish host tests/renders, emulator evidence, real-device evidence and live
  provider/service checks. Report skipped, failed and unavailable checks honestly.
  Host success does not prove GPS, audio, battery, OEM or Health Connect behavior.
- Device acceptance: [tests/s23/CHECKLIST.md](tests/s23/CHECKLIST.md). The custom
  instrumentation command is `adb shell am instrument -w app.rwun.test/app.rwun.SmokeInstrumentation`;
  build/install both debug APKs first as documented in the README.
- Documentation-only changes need content/link review and `git diff --check`;
  do not rebuild the app unless the change affects build or runtime behavior.

## Git, privacy and delivery

- Default to one focused feature branch and PR per substantial feature. A direct
  update to `main` is allowed when the user explicitly requests it. Commit/push
  completed authorized work; do not merge PRs, tag or publish releases without
  authorization for that action. Never force-push or overwrite unrelated work.
- If CLI authentication is unavailable, use the connected GitHub tools. Verify
  that published content matches the reviewed/tested files and current base.
- Never commit credentials, signing keys, real health records/routes, local
  databases, model weights, machine configuration or generated build outputs.
  Preserve third-party notices. Licensing is pending: do not add a project licence
  or claim unrestricted open-source reuse without the maintainer's decision.
- Update relevant docs and the changelog for user-visible changes. Keep durable
  decisions and known limitations in repository docs, not only in chat; link to
  canonical files instead of duplicating status, version numbers or test counts.
- Use [ROADMAP.md](ROADMAP.md), [REMAINING-GAPS.md](REMAINING-GAPS.md) and the affected
  source for current scope and limitations. Follow [docs/RELEASING.md](docs/RELEASING.md)
  for versioning, stable signing and release acceptance. A debug APK is not a
  production release; do not uninstall an existing app as an upgrade shortcut.
- Finish with the concrete outcome, relevant checks, remaining blockers and the
  commit/PR link. Do not claim success, publication or device validation without
  observing it.
