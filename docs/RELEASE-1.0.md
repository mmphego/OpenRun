# OpenRun 1.0 — beta release candidate

Positioning: running that fits around cycling, gym and recovery. This is an installable beta, not a claim of production readiness, qualified coaching review or a Play ranking.

## Added

- An account-free welcome path for starting, returning and regular runners, with optional local measurement consent. Starter/returning paths use a run/walk programme from the first week. Preview and persisted schedule use the same generator and one atomic preferences commit. Existing history and pain holds remain.
- Whole-week planning for dated cycling, gym, hiking and mobility entries: create/edit/delete, planned/completed status, duration and expected/reported effort. Calendar and Today show additional activities. Same-day running overlaps and adjacent hard-session conflicts are flagged; no running mileage or intensity is silently changed. These are scheduling heuristics, not a multisport physiological load model. Additional activities are manual, not GPS-recorded rides or wearable imports.
- A native, resizable next-workout widget with View plan and Record actions. It shows training holds. Launcher pinning support is checked. The widget opens the app; it never starts GPS recording from the background.
- Recording notification with distance/time, current Pause or Resume label and View / finish action. Opening it lands on Run. Finishing still requires review/save feedback; notifications never discard or auto-save a run.
- Transient audio-focus handling for spoken cues; loss interrupts speech and denied focus leaves guidance visible. Audio focus is relinquished after queued speech. Device/call/music behavior needs physical validation.
- Checkpoint timing based on monotonic elapsed time, lifecycle checkpoint attempts and a service-side pending-run/hold guard. Five-second snapshots are not a guarantee against all storage or OS failures. Interrupted runs remain user-recovered rather than auto-resumed.
- Native haptics respecting system settings, accessible heading semantics on supported Android versions, labels on new inputs and permission/battery readiness diagnostics. Device accessibility checks remain pending.
- An original PNG activity card saved through Android's document picker and optionally shared through its chooser. It contains distance/recorded duration, optionally date, and no route, location, HR or account identity. File writes run off the UI thread. No storage/media-library permission is requested.
- Previewable beta feedback and optional local usage summaries. No automatic feedback delivery or analytics endpoint. Metrics are off by default, bounded to 90 days, erased on opt-out, omitted from training backups and reset to off on restore. They are per-installation counts, not cohort retention or unique-user analytics.
- Android API 36 compile/target, minimum API 26; AGP 8.9.1/Gradle 8.11.1 source configuration. Pure Java code, no app native libraries. CI matrix prepared for API 26/29/35/36.
- Store listing, four-week beta protocol, coach-review register and device acceptance matrix in docs/launch. No store listing, recruitment, referral campaign, paid acquisition or live service has been launched.

## Retained

Original adaptive running rules, GPS recorder, offline storage, 18 generated coaching articles, six strength routines, progress analytics, manual GPX/TCX/FIT import, explicit Health Connect access, optional self-hosted cloud/Strava service and original Android vector branding.

## Install

The APK uses the same development certificate as 0.4–0.9 and can update those builds without uninstalling. Export a backup before testing restore. Keep both the APK and source; this signing key is public development material, not a production key. A production release needs an owner-controlled signing/upload key and Play setup. No existing production keystore or Play account was supplied.
