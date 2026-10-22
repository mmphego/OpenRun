# OpenRun 1.4.4 — S23 acceptance record (NOT EXECUTED)

No attached Android target was available during implementation. These scripts are a test kit, not a passed device test. Record model, Android/One UI build, permissions, battery setting and observed outcome for every row. Never publish screenshots containing personal data without review.

## Run

Enable USB debugging on your test S23; install Android platform-tools on your computer. Put both supplied APKs beside run.sh (the test kit archive already includes them). Set ANDROID_SERIAL from `adb devices -l`. Run `bash run.sh`. Set RWUN_RUN_INSTRUMENTATION=1 to run the isolated-preference smoke instrumentation as well. It does not clear the real training preferences. The test APK uses the public development key and must never ship as a production app.

For UI taps, use `python3 ui-tap.py 'exact visible label'`. If the element is absent, scroll within the content, dump again and retry. Capture a new UI tree/screenshot at each important state; coordinates must come from the tree. The initial evidence script does not declare UI success or complete the manual rows below.

| Flow | Acceptance evidence |
|---|---|
| Fresh install / upgrade | Existing plan, GPS history and routes retained on upgrade. Indoor walking available before creating a running plan. |
| Permissions | Decline activity recognition: selected step tracking must not silently start a timer; explicitly turn off Count phone steps to record time only. Decline notifications: timer works. Grant/revoke recognition: no crash or phantom steps. No GPS prompt for indoor mode. |
| Phone steps | Carry phone in pocket, count at least 200 observed steps. Compare app count; repeat phone-on-desk (expect undercount). Report error, speed and placement; do not substitute an emulator sensor for hardware evidence. |
| Calibration | Record machine distance and detected steps over the same steady walk. Save walking calibration, start a new session, verify formula. Repeat at several speeds to measure estimation error. Before personal calibration, walking must explicitly show the 0.70 m starter estimate. Running calibration is separate; no assumed running length. |
| Pause / resume | Walk while paused, resume: paused steps and time excluded; re-baseline may lose boundary steps and flags a partial estimate. Save uses selected machine distance or explicitly selected estimate. |
| Screen off / process death | Record 30 minutes locked; pause/resume from notification. Compare timer with stopwatch. Kill process without force-stop and reopen; no duplicate save or invented missing steps. Restarted sensor tracking is partial. |
| Reboot / force-stop | Reboot recovers only saved time paused, labels interruption. User resumes. Force-stop may stop all tracking/reminders until reopened; no claim to bypass Android. |
| Indoor save | Walking minutes never increase running mileage. Time-only run goes to Indoor progress; distance-bearing run also appears once in Progress. Machine totals override estimate. Pain retains training hold. |
| Corrections / deletion | Correct indoor run: both views agree, route/quality provenance explicit. Delete run removes indoor mirror; undo restores both. Walking/time-only entry deletion requires permanent-delete confirmation. |
| Outdoor recording regression | GPS disabled → settings → return; permissions denied/permanent denial; start confirmation; no double start; indoor pending blocks GPS and vice versa. Test real outdoor route, screen off, call/music interruption. |
| Reminder controls | Explicit opt-in, notification permission/channel denial, test notification, daytime window/day selection, Snooze 30 min, Skip today, training hold, active/pending session suppression. Delivery may be delayed by Doze/Samsung controls. |
| Reminder lifecycle | Restart phone, change time/timezone/DST, update APK. No bursts of missed notifications or midnight alerts; no more than 12 movement prompts/day under unchanged settings. |
| Planning / import | Balanced/lighter proposal preview, running-session collisions, no catch-up, stale proposal refusal. Match/unmatch imported activity without duplicating totals. Deletion undo with matching conflict. |
| Race / check-in | Race too soon rejected; preview start/event/taper; saved event visible. Optional check-in fits available time, fatigue offers lighter review, pain requires explicit hold confirmation. |
| Accessibility / layout | TalkBack order and labels, 200% font, small display, landscape/rotation, dark theme, keyboard and dialogs. Check no clipped controls and drafts survive rotation. |
| Performance / battery | Measure 30–60 minute indoor and outdoor sessions, temperature, battery delta, startup, frames and memory under normal Samsung settings. No performance pass from compilation alone. |
| Integrations | Real Health Connect grants/import/export/revocation and duplicates. Garmin remains explicitly unavailable until official approval/credentials/service; no pairing-success claim. |

Release gates: complete the above on an S23 and broader API/device matrix, resolve failures, review training with a qualified coach, complete Play privacy/Health Connect/FGS declarations and production signing. Development APK is a release candidate, not a certified market-ready release.


## Running and walking focus (NOT EXECUTED)

- Upgrade a copy of 1.1.0 data containing rides, strength history and unfinished cycling recordings. Five tabs: Today, Plan, Calendar, Progress, Coach. Profile opens from the top-left icon. No cycling/gym launch controls. Old data remains in exported backup; archived cycling must not resume as a run.
- Open Plan: the return hub offers Start new plan, Restart current plan and Continue plan; Continue opens the schedule. Switch between Plan and Calendar tabs, navigate weeks and manage plan. No clipped labels at 200% font.
- Record outdoor walk before creating a running plan. Pause, resume, save, view route, export GPX, delete and undo. Walking minutes appear in Walks and do not increase running mileage or complete a planned run.
- Repeat with indoor walking-pad steps and manual distance; indoor running stays in running totals. Missing Health Connect walk distance stays time only. Reimport does not duplicate.
- Recover unfinished running and walking sessions after process death. Completed session saves once. Pain feedback retains the training hold.
- Verify cycling/gym no longer generates reminders or changes a running-week proposal. Run/walk reminders remain opt-in.

## Plan and Coach discoverability (NOT EXECUTED)

- Today: plan goal/week, next workout and coach preview are visible before free activity controls. Open plan and Coach from these cards.
- Coach: open briefing, listen, open workout, return, weekly review, check-in, library and audio preferences. Test empty history and training hold.
- Rotate on each tab; selected destination persists. Test 200% font and TalkBack labels for five-tab navigation.
- No-plan user: open Coach through a destination intent; Create my plan opens onboarding. Calendar must tolerate no plan.

## Race-first planning (NOT EXECUTED)

- Fresh install: Welcome → Create my plan → Race. Existing plan: Plan → Start new plan → Race. Verify auto-advance after choosing a race and existing non-race onboarding remains available.
- Search by name/city, country, distance and date range; empty search and invalid ranges; source links; manual name/city/country/date/distance; unsupported trail/ultra and too-soon dates. Catalogue must state its limited scope and checked date.
- Offline: create a manual race plan and inspect the exact first/race-week preview, selected weekdays and long-run day. Save and reopen; same schedule and race target appear in Today/Plan/Calendar. Completed history and holds remain.
- Cancel/back/rotation through every wizard stage. Large fonts, keyboard, TalkBack, physical Back and no clipped confirmation buttons. Replacing a plan requires the concrete preview confirmation.
- Configured test backend: catalogue refresh success/cache, invalid response/HTML/timeout and stale fallback. Use an authorised test feed; no scraping or false live-provider claim.
- Real recorded history: two-week/four-week gaps, recent high feedback and low baseline. Coach → Adapt plan → preview/apply. Same evidence must not reduce again; corrections/new evidence invalidate an old preview; active recording/hold prevents mutation. Past/completed workouts stay fixed.
- Optional AI: without server/account/keys, explicit blocker. Consent cancel sends nothing. With a configured test server/provider, inspect disclosed numeric fields, no routes/names/HR, successful explanation, provider error/timeout and quota. AI cannot change workout data. No live provider pass has been recorded.

## Gemma and rebuilt wizard (NOT EXECUTED ON S23)

- Model manager is reachable from Coach and a reviewed plan. Open publisher link, decline/cancel download, import a wrong/truncated file and verify a clear rejection; an existing valid model is kept. Import the exact linked model, verify checksum and remove it without changing history.
- Accept Gemma licence on the publisher site and download the pinned 557 MiB file. Weights are not bundled or silently downloaded. Offline Generate must make no network request; record model load time, generation latency, peak memory, temperature and battery use on S23. Repeat with airplane mode.
- Check local runtime: record actual ARM64 JNI load outcome. This alone is not a model generation pass. Generate a short insight, verify no invented activity/target or hold clearance. Test fresh/returning history, held training and stale history while generating.
- Cancel or leave screen during generation; confirm separate offlinecoach process ends and main app/recording data remain. Low memory, timeout, repeated taps, rotation, back navigation and process crash should leave a usable app.
- Active/pending indoor or outdoor session must block generation. Leave a recording locked for 30 minutes to establish that the new runtime has not affected recovery.
- Wizard: goal choice immediately continues; race card immediately continues. Distance goal skips race catalogue. Back and rotation preserve manual race name/date/distance and selected weekdays. Add remains disabled for incomplete/too-soon details.
- Search/clear, date/distance/country sheets, no-results/manual entry, selected-day long-run picker, preview/confirmation and saved schedule consistency. TalkBack and 160–200% font with real Samsung keyboard. Capture native screenshots at every stage.

Host Robolectric Native Graphics interaction tests and renders were run in this release. Android emulator boot failed in the implementation environment; no emulator, S23, live Gemma generation or physical permission/sensor pass is claimed.


## Compact typography (1.4.1; physical test pending)

Check normal and enlarged Samsung text/display settings in Plan, race/manual onboarding, Coach, Progress and recording. Verify no clipped text, usable inputs, accessible touch targets and unchanged persisted history.


## GPS distance fix (1.4.4; S23 NOT EXECUTED)

- Outdoor walk: first check 50–100 metres with the phone carried. Confirm distance advances, sample age is current and GPS recording details show more than one accepted point. If not, retain diagnostics before doing a long walk.
- Continue over a known 1–2 km route with screen locked. Capture count/delay/gap diagnostics before and after. Compare with a separately recorded reference. Inspect route, elapsed time and saved distance; do not infer accuracy from a green GPS accuracy label alone.
- Stop moving for two minutes; check warning wording. Disable GPS, revoke permission and restore it; no invented connecting jump. Pause/move/resume must exclude paused movement. Background and Samsung battery settings require real-device evidence.
- Walking pad: choose the indoor session, explicitly enable phone steps and calibrate, or enter machine distance. GPS mode must not silently invent indoor distance.
- Save/recover a zero-distance walk; enter a known 2 km or use Progress → Walks → Correct walking distance. Verify manual source label, preserved actual time/route and unchanged running history/holds. Test cancellation, invalid numbers, backup/restore, deletion and undo.


## Walking-pad regression — 1.4.4 (NOT EXECUTED)

1. Save any current recording, upgrade without uninstalling, and verify old plan and walks remain.
2. Today → Start walk → Walking pad: no location prompt or GPS dashboard. Count phone steps starts selected. Grant Physical activity permission and start. Declining must leave the start screen, with an explanation.
3. Put the S23 in the same pocket used for the reported walk. Walk 100–200 steps and confirm the count increases before a longer session. Record app steps, manually observed steps, pad speed and distance, and the phone/One UI version.
4. Lock the phone and walk on the pad for 10 minutes; unlock and allow the counter to deliver its sample. Count and estimated distance should advance. Check notification and preserve logs if either stays at zero. Do not mark a host simulation as a hardware pass.
5. On a controlled no-steps session, verify NO STEPS YET appears after 30 seconds and the notification says no steps detected while the process is awake. No spoken warning is promised.
6. Compare the estimate to the pad display. On Finish and review, enter the pad's known distance and choose calibration. The next walk must use that personal length rather than 0.70 m. Record error over multiple speeds; distance remains approximate.
7. Pause, walk while paused, resume and finish. Paused movement is excluded; baseline-boundary loss is labelled partial. Save once, verify Walks history and unchanged running mileage. Review a saved outdoor-mode walk with known distance correction separately; this cannot reconstruct its missing route or sensor samples.
8. Return to the Move screen during an active indoor session. It must offer Return to indoor session, without the outdoor GPS dashboard.


## Reliability regression — 1.4.4 (NOT EXECUTED)

- Start a carried-phone indoor walk, observe increasing steps, then stop moving for more than 60 seconds. NO RECENT STEPS must appear. Resume walking: the warning clears. This is a conditional check, not automatic proof of failure while resting.
- Lock the S23 during a guided treadmill warm-up with Audio enabled. Verify the timed transition and audible cue without reopening the screen. Distance steps still need explicit confirmation. Repeat with music, a call, TTS missing, Bluetooth audio and audio disabled. Record battery drain and verify service/wake-lock cleanup after pause, finish and discard. A timing/voice host test is not an audible hardware pass.
- Tap Finish from the notification with the app closed and already open. Timer pauses, review opens, time stays fixed until Resume, save occurs once. Cancel review, rotate, reopen; drafts and paused state remain understandable. An old session notification cannot control a newer session.
- Leave a paused recording for 30+ minutes. Within the opted-in reminder window, an unfinished-session review prompt appears once per day without deleting data. Check an interrupted GPS recording, legacy checkpoint and an active timer left idle well beyond its goal. A normally progressing session must suppress movement prompts without consuming reminder quota.
- Import the same real workout through two providers. It must be held for review outside history totals; repeated sync must not queue it twice. Keep existing, then sync again. Test two separate same-day sessions with similar totals, timezone date differences, time-only walks and a GPX/TCX/FIT file. Compare displayed start times, original feedback, route and running mileage.
- Back up/restore pending import reviews on a test dataset. An invalid import or failed save must retain existing activities. This release does not automatically merge previously saved duplicate activities or apply provider edits/deletions.
- Open Recording diagnostics and preview feedback. Version must be 1.4.4; mode, sensor permission/availability, recent sample, heartbeat/checkpoint age and bounded error code should be shown without routes, account data or raw samples. Sharing requires an explicit user action.
