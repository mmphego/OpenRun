# Initial issue backlog

Create these only after checking for duplicates in the published repository.

## Select and document a project-wide licence

Priority: publication blocker. Review original-code ownership and retained third-party notices. Choose an appropriate project licence explicitly; add its text and update README/CONTRIBUTING without changing upstream component licences. Acceptance: GitHub detects the selected licence and all notices remain intact.

## Capture current Android device screenshots

Labels: good first issue, documentation. Build the current commit, populate synthetic data and capture goal selection, multi-provider catalogue, plan preview and indoor walk. Record device/API, commit and commands; avoid personal routes/health records. Acceptance: README distinguishes device captures from host renders and matches current UI.

## Audit race discovery accessibility

Labels: good first issue, accessibility. Check TalkBack order/labels, touch targets, keyboard search and 1.5–2x font scaling. Acceptance: documented findings, focused fixes and before/after evidence on an Android device or emulator.

## Add provider parser regression fixtures

Labels: good first issue, tests. Add minimal sanitised Top Events and Bield fixtures covering missing distance/location, non-running events, malformed entries and pagination boundaries. Use fixed dates and no live-network dependency. Acceptance: parsers preserve unknowns, filter unsupported records and report skipped rows; regressions fail before the fixes.

## Configure signing and validate first tag release

Priority: distribution blocker. Configure the four documented Secrets, protect version tags and test a signed candidate on a device. Acceptance: CI passes, the release job verifies APK signing, Release includes APK/checksum, checksum verifies and the exact downloaded APK installs. Do not attach private signing material to the issue.
