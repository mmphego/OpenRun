# OpenRun 1.0.3 — movement and useful training feedback

Version code 13. Original Java Android implementation, API 36 target, API 26 minimum. Retained public development signing certificate; install over 0.4–1.0.2 without uninstalling. This is an installable beta/release candidate. It is not yet certified market ready.

## Indoor walking and running

- Walking-pad/treadmill entry from welcome (no running plan required), Today, recorder, Profile and planned workout details.
- Three-second foreground countdown, measured monotonic elapsed timer, pause/resume, optional time goal and review before saving. Timer requires no GPS. Time can be reconstructed after process death within the same boot; reboot recovers only the last checkpoint, paused. Force-stop can stop tracking and notifications until reopening.
- Optional Android TYPE_STEP_COUNTER with physical-activity permission when needed and a user-started health foreground service. Counter availability is checked at runtime; denial/absence retains time/manual-distance mode. Carry the phone on your body. A phone left on the desk may severely undercount. The sensor may deliver steps with latency.
- Distance estimate = detected steps × calibrated metres per step ÷ 1000. Separate walking and running calibrations; no guessed default stride. Calibrate over at least 100 detected steps and a known machine distance, either before a future session or at save. Calibration is captured at session start, so changing it does not rewrite history. Step length varies with gait/speed; this is an estimate, not machine telemetry.
- Counter baselines exclude pre-session steps. Pause/resume re-baselines conservatively and can lose boundary steps. Reset, implausible deltas and service interruption mark partial estimates; no missing distance is invented. Sensor writes are checkpointed approximately every five seconds on a worker thread and flushed on pause; Android or power loss can lose uncommitted samples.
- Finish review: optional machine distance takes precedence; otherwise explicitly selected calibrated estimate, or time only. Source and detected-step provenance remain visible. No treadmill control or Bluetooth FTMS speed/distance implementation is supplied.
- Walking minutes and active days are separate from running mileage. Time-only indoor runs stay in indoor history. Distance-bearing indoor runs also appear once in running Progress. Corrections update both views. Run deletion/undo removes/restores the indoor mirror. Walking/time-only deletion has explicit permanent confirmation.
- Optional planned indoor workout: timed phases advance while the screen is open; distance phases require confirmation from the display. Step completion is self-reported, with no fabricated quality-set/GPS evidence. Spoken cues require foreground screen and a working TTS engine; background spoken indoor coaching is not implemented.

## Reminders and flow

- Explicit opt-in movement windows, days, 30–180 minute intervals, up to 12 scheduled movement slots/day, planned-workout time, test notification, snooze and skip today. No sitting detection or automatic exercise start.
- Inexact alarms can be delayed by Doze/Samsung controls. Local times survive DST/timezone changes; boot/update reschedules. Active/pending sessions and training holds suppress prompts. Notification permissions/channels remain under user control.
- Optional pre-session self-check: available time, fatigue and pain; offers indoor time or lighter-week review. Pain hold requires explicit confirmation and is not a diagnosis. No numerical readiness score.
- Balanced/lighter week previews consider remaining sessions, available days and manually recorded hard other activities. Every move/skip requires approval. No catch-up or added mileage. Stale previews, protected history, occupied dates and pending recordings are rejected. These rules are not a validated injury/combined-load model.
- Race-date plan preview anchors 4–24 preparation weeks before the event. Rejects compressed past starts; original final-two-week taper rules are retained. Race day appears as an event marker; it is not a readiness guarantee.
- Explicit match/unmatch suggestions for saved/imported runs, possible-duplicate warnings, planned/actual distance, continuous GPS kilometre splits and split consistency. No automatic merge or extrapolated GPS splits. Source-specific duration semantics are shown. Corrections clear quality-set evidence; raw GPS routes and existing holds remain.
- Most-recent running deletion undo for 15 minutes. Routes expire on later app cleanup. Match/indoor links restore with the activity; conflicting matches must be resolved first. Badges are replaced with practical movement/consistency views.

## Storage and validation

Feature history/calibration/planning data is in private storage and validated portable backups. Active indoor timers, deletion undo, notification consent and delivery counters are excluded/reset on backup restore. Backup/restore and outdoor starts reject pending indoor sessions. No new backend is needed for these features.

See VALIDATION-1.0.3.md and tests/s23/CHECKLIST.md for executed checks and unexecuted device acceptance. No Android device or emulator was attached. Official Garmin partner approval/credentials, live service activation, qualified coach review, production signing and Play setup remain external blockers; none are simulated as connected.
