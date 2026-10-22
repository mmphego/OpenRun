# Changelog

## Unreleased — public collaboration preparation

- Renamed visible branding and distribution artifacts from Rwun to OpenRun, preserving `app.rwun` and backup identity.
- Replaced unsupported API usages and literal Android constants; pinned current lint for LiteRT metadata compatibility.
- Added contributor onboarding, community policies, issue/PR templates and release documentation.
- Added secret-free PR checks and signed version-tag release packaging with SHA-256 checksums.
- Restored clean-clone Room dependency/annotation-processing configuration and local debug signing.
- Included Top Events, Bield and approval-gated RunSignup adapters, conservative multi-provider merging, foreground JobScheduler sync, database leases and content-aware Room upserts.
- Connected local race discovery to saved races, provider sources and plan previews.
- Added regression coverage for race persistence, merge identity and saved-user-choice preservation.
- Kept project-wide licensing explicitly pending; no new licence is imposed.

## 1.4.4 — recovered baseline

Recording reliability, stalled-recording diagnostics, notification finish/review and import duplicate review. See [original release notes](docs/RELEASE-1.4.4.md) and [historical release narrative](docs/HISTORY.md) for previous implementation details. Recovered version labels describe source artifacts; they do not imply public Git tags or signed releases exist.
