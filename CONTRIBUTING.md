# Contributing to OpenRun

OpenRun focuses on running and walking with native Android Views, offline training and explicit user review. Read the [README](README.md) for the build commands, architecture, feature status and configuration.

## Licensing comes first

No project-wide licence was recovered. Do not assume that public source grants reuse or redistribution rights. Until the maintainer selects and verifies a licence, use issues for proposals and ask the maintainer before contributing code. No CLA or copyright transfer is implied. Keep all third-party notices.

## Your first change

1. Check the issue tracker for a matching proposal and describe the expected behaviour.
2. Fork and clone the repository. Install JDK 21, Python 3.12 and Android SDK 36/Build Tools 35.0.0. Copy `local.properties.example` and set your SDK location.
3. Create a feature branch. Use a Python virtual environment and install `server/requirements.txt` if running the complete check suite.
4. Run `bash scripts/check.sh`. For focused iterations, run the relevant host script or Gradle task listed in the README.
5. Build and install the debug APK. Exercise the changed flow with synthetic data; record Android API/device and results. Clearly label host renders versus device screenshots.
6. Open a PR using the template. Explain the user-visible change, evidence, limitations and any new dependencies.

## Code and review standards

- Preserve the Java/native Android architecture and original visual language. Keep UI, domain decisions, persistence and network adapters separate; prefer small single-responsibility changes over a framework rewrite.
- Never wait for network on startup or the UI thread. Preserve local data across failures and partial fetches. Keep provider identity and saved user choices stable.
- Add meaningful regressions for training logic, ingestion, migrations, recovery and authentication changes. Avoid tests that only repeat implementation details.
- Do not claim Garmin account sync, medical validation, Runna parity or working integrations based on mocks. Explain which parts were exercised live.
- Keep running and walking the active scope. Discuss major changes and dependencies in an issue first.
- Never commit signing files, private routes, health records, credentials, local databases or machine-specific files. Use synthetic fixtures and safe examples.
- AI-assisted changes face the same review bar: understand the code, verify provenance and explain the tests.

## What CI runs

`ci.yml` runs Android Lint, Gradle unit tests, standalone Java regressions, service tests, device-QA utility tests, and debug/app-test APK compilation. Reports upload even after failure. Fork PRs use `pull_request`, never `pull_request_target`, and receive no signing/provider secrets. GitHub may require a maintainer to approve first-time contributor workflows.

Device instrumentation is a separate local acceptance step, not falsely reported as part of headless CI. Follow the README's explicit `am instrument` command.

## Good first contributions

- Capture current device screenshots with synthetic data and document reproduction steps.
- Audit TalkBack labels, large fonts and touch targets in race discovery and planning.
- Add sanitised provider parser fixtures for missing optional fields and pagination boundaries.
- Improve clean-clone setup on Windows and macOS after actually running it.

Use the bug form for reproducible defects and the feature form for scoped proposals. Use the private process in [SECURITY.md](SECURITY.md) for vulnerabilities and follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
