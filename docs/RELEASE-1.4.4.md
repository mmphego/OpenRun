# OpenRun 1.4.4 — recording reliability and import review

Version 1.4.4 / code 22 addresses the six software gaps identified in the review. Existing plans, recorded history and the development signing identity are retained. Save an active session before updating and install over the existing app without uninstalling.

## What changed

| Reviewed gap | Resulting behavior |
|---|---|
| Steps start and then stop updating | Indoor recording checks the last accepted progress timestamp. It warns after 30 seconds at zero, and after 60 seconds without further steps. The text is conditional: standing still is not automatically a sensor failure. Pauses suppress the warning; resuming gives a fresh grace period; new steps clear it. The screen and ongoing notification show the warning. |
| Forgotten sessions suppress reminders | Paused recordings become eligible for a review prompt after 30 minutes. Missing heartbeats, legacy checkpoints and active timers left idle beyond their goal also have bounded review policies. Prompts respect reminder opt-in, windows, snooze/skip, holds and notification access. They appear at most once per pending session per day and do not discard data. A normally progressing session suppresses movement prompts without consuming the delivery quota. Android still controls alarm delivery. |
| Notification Finish only opens a screen | Finish carries the current session identity into the indoor Activity, pauses that session and opens its save review. It works for a closed or already-open screen. Old Finish/Pause intents cannot control a newer session. Cancelling review keeps the recording paused; no background auto-save or discard occurs. |
| Cross-provider imports inflate totals | New Health Connect and Strava imports are compared against saved activities. Possible duplicates are held outside history, mileage and training evidence until reviewed. Progress and Health Connect expose Review imports. The user can keep the existing record or explicitly import a separate session. Repeat sync does not duplicate the review or repeatedly re-prompt after dismissal. GPX/TCX/FIT imports have a duplicate preflight before the normal feedback flow. |
| Indoor coaching depends on the screen | The user-started foreground session now owns timed progression and optional audio. It continues timed warm-ups/recoveries when the screen closes. Distance steps still require explicit confirmation from the machine. Delayed updates catch up timed steps without advancing through a distance step or playing a burst of old cues. Text-to-speech readiness/focus failures are visible. The final sentence is allowed to finish. |
| Diagnostics omit recording health | Recording diagnostics and previewed feedback include the installed version, mode, sensor availability, Physical activity permission, service state, last sample age, heartbeat/checkpoint age and bounded recording-error code. They contain no route coordinates, raw sensor values or account data and are never sent automatically. The obsolete 1.0 beta label is removed. |

## Where to find the fixes

- **Today → Start walk → Walking pad · phone steps** starts an indoor session. Physical activity permission and a carried phone are required for counting. The disclosed starter estimate remains 0.70 m per step until personally calibrated; it is not a measured distance.
- **Indoor recording → Recording diagnostics** previews current recording health. Profile's Android/readiness tools and feedback preview use the same report.
- **Progress → Review imports** appears when there are held activities. Incoming and saved source, distance, duration and available start times help the comparison. Missing timestamps are labelled unavailable rather than invented.
- **Finish in the ongoing indoor notification** pauses and opens the review. The original review can use a known machine distance, a phone-step estimate or time only.

## Import and storage safeguards

Known start/end intervals are compared independently of local date labels, so two providers representing the same instant in different timezones can still be matched. Similar same-day totals with missing timing are treated as uncertain and offered for review. Known sessions at different times remain distinct. Native and provider timing origins are retained where available. Clock changes do not block saving a native activity; unreliable wall-clock intervals are marked unknown.

Held imports do not complete planned workouts or change training feedback. Review decisions, origin metadata and ignored incoming IDs survive validated backups. Imported walks remain separate from running mileage; a walk without distance remains time only. Existing recorded routes and feedback are not replaced by incoming data.

This is conservative duplicate prevention, not a promise of perfect identity matching. It does **not automatically merge previously saved duplicates**, apply provider edits/deletions or replace an existing record with a preferred provider. Similar legitimate activities can need explicit review. Provider records without useful timing or with materially different totals can still require manual investigation.

## Background behavior and limits

The session uses the declared health foreground type when counting steps and media playback type when audio coaching is enabled. Timed audio work takes a bounded wake lock; it is released on pause/finish/discard/service destruction and when waiting for a manually confirmed distance step. Audio has a 12-hour session limit; standalone time goals remain 5–180 minutes. The optional audio path respects the existing Profile audio setting and audio focus. With audio off and no sensor tracking, elapsed timed steps are reconciled when the screen is reopened rather than keeping a coaching service alive.

Force-stop, reboot, permission revocation and Samsung restrictions can interrupt recording. No missing steps or route are invented, and no claim to bypass Android restrictions is made. The activity record can outlive a stopped service, so the new health warnings, checkpoint reporting and unfinished-session review remain important.

## Validation — 21 October 2026

- **59 Robolectric host tests pass**: 5 planning/UI, 10 outdoor GPS, 14 existing indoor recorder/flow tests, and **30 reliability/import/coaching tests**.
- New cases cover post-start step stalls and recovery, pause/resume grace, cold/warm notification Finish, stale Finish/Pause identity rejection, stale/legacy/forgotten-session reminders, quota and opt-out handling, active progress suppression, cross-provider duplicate holding, repeated sync, explicit review decisions, distinct workout times, timezone date differences, legacy uncertainty, time-only walks, validated backup/restore, invalid-import preservation, clock changes, review discoverability, background timed progression, distance-step boundaries, longest supported goal cue, delayed speech completion, wake-lock cleanup and diagnostic redaction.
- **49 storage/backup**, **63 indoor/state**, **34 walking/migration**, **10 navigation/error**, **68 recording-start**, **29 feature** and **96 workout/coaching** assertions pass. Plan invariants cover **84 configurations**.
- Standalone API 36 build succeeds. APK v2/v3 signing and 16 KiB ZIP alignment pass using the retained development certificate. The instrumentation APK compiles; it was not run on a device.
- Native Android host renders of the stalled-step warning, paused notification destination and duplicate review were visually inspected. The save dialog's open/paused state is asserted by the host tests. These are controlled host tests/renders, not physical S23 evidence.
- Test Android Apps inventory found **no attached adb device**. S23 screen-off sensor sensitivity, audible delivery, calls/music/Bluetooth interactions, battery drain and physical recovery remain unverified. The included non-destructive S23 test kit is a pending checklist, not a passed field test.

APK SHA-256: `ab687e90a6b47211333016b5794639e01bd395fb209467601e6b89f967c0fb97`

## External gaps still requiring activation or hardware

- **Garmin:** official automatic activity/workout sync needs approved partner API access, credentials and a configured HTTPS service. No working account sync or watch pairing is fabricated; exported activity-file import remains available.
- **South African races:** the starter catalogue and backend/feed contract remain limited; no comprehensive authorised live race feed/service is deployed. Manual races still work.
- **Gemma:** model manager and runtime code remain present, but a verified model import and live S23 JNI/inference/memory/latency/quality testing are still required. The deterministic schedule engine remains responsible for plans; model text cannot silently rewrite training.
- **Production release:** this is the retained development-signed build. Device acceptance, production signing/service deployment and final coaching review are still needed before calling the app market-ready.

Android contracts checked: https://developer.android.com/develop/background-work/services/fgs/service-types , https://developer.android.com/develop/background-work/services/fgs/launch , https://developer.android.com/health-and-fitness/health-connect/sync-data .
