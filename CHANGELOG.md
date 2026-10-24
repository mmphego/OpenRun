# Changelog

## 1.7.0 — Daily movement and recovery candidate

- Add opt-in Health Connect step checks during configurable working days/hours, intervals from 60 minutes and walking breaks from three minutes.
- Keep missing/stale steps unknown; suppress reminders during holds or pending recordings. Add snooze, already-moved, skip-today, repeat limits and durable preferences.
- Offer optional pre-session sleep, fatigue, soreness, illness, alcohol and pain check-ins with explainable suggestions. Training changes require review; holds cannot be cleared by a check-in.
- Read optional sleep-window and same-source resting-heart-rate context locally without claiming live Garmin access, alcohol detection or physiological readiness.
- Keep feature data separate from backups, exports, analytics and AI explanations. Retain only the latest check-in with seven-day expiry and deletion controls.
- See [feature behavior and device acceptance](docs/DAILY-HEALTH.md). No background location or venue inference is added.



## 1.7.0 — Plan-change history and safe undo candidate

- Add Plan changes from Plan and Coach, showing applied timestamps, reasons, evidence and actual before/after workout dates, distances and skipped state.
- Show the resulting workout type and changed effort guidance in previews and undo, including restored harder sessions.
- Stage previews without writes; reject confirmations after plan, activity evidence, recovery or recording state changes.
- Save plan edits and append-only history in one preference transaction. Preserve past/completed planned targets when recalculating workload.
- Review and undo only the newest eligible event; append a linked undo event. Reject past/completed/matched workouts, pending recordings, changed plans, collisions and inconsistent imported consequences.
- Keep pain holds and recovery review explicit. Undo never clears or reverses a hold or recovery review.
- Include validated history in portable/encrypted backups without changing the Rwun discriminator. Existing installs and older backups start with empty history.
- Version 1.7.0 / versionCode 26. Signed release workflow and third-party notices are unchanged; project licensing and physical-device acceptance remain unresolved.

### Automated APK releases (included from main)

- Build and publish a signed APK after main merges, with unique build tags and increasing Android version codes.
- Attach versioned APKs and SHA-256 checksums; identify the exact APK in generated release notes.
- Keep PR debug artifacts versioned and signing isolated from untrusted PR checks.


### Progress presentation (included from main)

- Group running history by month, sort activities by recording date/time and show distance, duration and average pace in compact cards.
- Replace truncated filters and Android package names with labelled controls and readable activity sources.
- Open activities into a dedicated detail view with available metrics, an offline route preview and an optional inline OpenStreetMap with pan/zoom.
- Keep review, correction, matching, sharing, export and deletion in activity details; retain Health Connect 1.6.0's reviewed original-activity export flow.
- Preserve selected activity and filters across Android recreation. Use two metric columns at larger accessibility font sizes.
- Missing routes and unsupported metrics remain explicitly unavailable; this change does not add route, cadence or calorie imports.


## 1.6.0 — Health Connect candidate

- Added a dedicated Health Connect screen with independent read and write consent, activity previews and access management.
- Read recent running/walking sessions with same-publisher distance and optional average heart rate; retain duplicate review, bounded pagination, partial-import safety and cancellation.
- Write original GPS runs/walks and timestamped indoor sessions using stable record IDs and content-aware version increments; unknown distance stays time-only. Imported activities are excluded from export.
- Added permission, native record mapping, concurrency, failure, upgrade-safe ID and UI tests. Health Connect service/device acceptance is still pending.


## 1.5.0 — OpenRun candidate

- Reconciled the recovered Rwun 1.5.0 race discovery, provider and planning implementation with the published OpenRun build and CI fixes.
- Updated Today, the home-screen widget, share cards and distribution examples to OpenRun; Android versionCode is 24, above Rwun 1.5.0's 23.
- Preserved multi-day event ranges, conservative duplicate matching, bounded Bield redirects and provider failure isolation.
- Added a non-destructive Room schema 1→2 migration for both previously shipped schema-1 layouts, retaining saved races and training goals.
- Added the recovered native Android host tests to Gradle CI; live-provider checks remain opt-in and physical-device acceptance remains pending.

This is a source/PR candidate, not a published signed release. Package `app.rwun`, storage keys, backup discriminator `Rwun` and external record identifiers remain unchanged. Updating an installed APK also requires the same signing certificate; the original private key is not committed.

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
