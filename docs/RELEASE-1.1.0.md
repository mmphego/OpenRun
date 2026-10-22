# OpenRun 1.1.0 — cycling joins your training week

Version code 14, API 36 target / API 26 minimum. Original native Java code and OpenRun branding. Restored the shipped 1.0.3 source before this work; walking-pad calibration, reminders, plan auto-advance and recording-start fixes are retained. Retained public development signing key supports an in-place update from 0.4–1.0.3. This remains a beta/release candidate.

## Delivered

- Dedicated native Cycling workspace with Ride / Progress / Plan tabs, custom vector bicycle icon, accessible metric labels, dark cards and a six-week chart drawn from actual cycling hours. No sample rides or demo metrics.
- Road and MTB GPS recording uses the shared foreground location recorder with a cycling-specific speed filter (35 m/s upper rejection threshold) while the running filter stays at 12 m/s. Pause/resume, screen-off recording infrastructure, notification controls, actual start confirmation, checkpoint recovery and one-recorder guards apply. Physical-device reliability still needs acceptance testing.
- Current speed is a 30-second GPS-window estimate; average uses saved distance/recorded duration. Optional Bluetooth broadcasting heart-rate sensors use the existing client, with separate permission/scan/choose/disconnect actions. No ANT+, power meter, cadence sensor or automatic watch-account pairing is claimed.
- Indoor ride timer uses monotonic time without GPS or step permission. Phone steps never estimate cycling distance. Enter the trainer display’s distance or save time only. Local ongoing notification opens the timer. Same-boot process recovery reconstructs elapsed time; reboot pauses at the last checkpoint. Force-stop may halt features until reopening.
- Separate cycling history, weekly distance/time/ride totals, six-week hours, route sketch and opt-in street map, cycling GPX export, sampled maximum speed, observed GPS moving-time estimate and noise-filtered approximate ascent when elevation exists. Missing elevation stays unavailable; FIT elevation is not decoded. GPS ascent is not a barometric measurement. Pauses/gaps are not bridged to invent speed or climbing.
- Explicit cycling GPX/TCX/FIT import with bounded parsing, checksums for FIT, type checks for TCX/FIT, selected Road/MTB/Indoor classification and review before saving. GPX may lack reliable sport metadata and requires user classification. Repeated files are identified by their hash. A file already saved as a run cannot also be imported as a ride via this flow. Cross-provider or edited/exported copies still need possible-duplicate review.
- Health Connect imports now include outdoor and stationary cycling alongside running, using the existing real grants and per-publisher distance aggregation. Missing cycling distance produces a time-only ride; imported effort starts unreported. Generic outdoor imports remain visibly unclassified until the user confirms Road/MTB. Health Connect remains Android 14+.
- Explicit write of original OpenRun GPS rides to Health Connect after separate exercise/distance write grants. Stable client IDs and correction revisions avoid duplicate writes. No imported records are echoed back and no route is uploaded. The Health Connect session spans first-to-last route timestamp, including pauses; app active duration can differ. Indoor/manual/recovered ride export is not supplied.
- Manual rides, explicit planned-ride linking/unlinking and planned-vs-actual duration; correction of totals/effort without rewriting raw route samples; most-recent ride deletion undo for 15 minutes. Deleting a linked ride reopens its plan. A conflicting restored link/duplicate identity fails safely.
- Add/move/remove planned Road/MTB/Indoor rides, visible with running and other activities in the shared calendar. Existing manually entered cycling calendar entries remain intact and should be reviewed if they describe a ride also added to the new history.
- Planned/completed rides feed shared weekly conflict review. Reported/planned effort 7–10 flags a hard ride; demanding adjacent runs raise review prompts. Balanced/lighter schedule proposals remain explicit and reject stale previews when cycling changes. There is no km conversion, automatic extra running mileage, validated physiological load model, FTP/VO2 estimate or injury/readiness score.
- Existing opt-in planned-workout reminders now include pending cycling dates. Global quiet hours, notification permissions, training holds, session suppression and inexact Android delivery still apply.
- Cycling data and route files are included in validated portable backups with staged route replacement on restore. Active indoor cycling and deletion undo are omitted; active recordings block backup/restore. Cycling pain reports save a recovery hold without clearing existing holds.

## Validation and limits

See VALIDATION-1.1.0.md and tests/s23/CHECKLIST.md. No device/emulator is attached, so there is no S23, Bluetooth, GPS accuracy, actual UI or Health Connect runtime pass. Official Garmin two-way sync still requires Activity/Training API approval, credentials and a configured service. No provider success is fabricated. Structured cycling coaching/training plans, trainer control/FTMS, navigation and sensor ecosystems remain future work.

## Primary references

- Android exercise types: https://developer.android.com/reference/android/health/connect/datatypes/ExerciseSessionType
- Garmin FIT activity structure: https://developer.garmin.com/fit/articles/file-types/activity.html
- FIT decoding considerations: https://developer.garmin.com/fit/articles/cookbook/decoding_activity_files.html
- Official Garmin Activity API: https://developer.garmin.com/gc-developer-program/activity-api/

The existing home-screen workout widget and calendar-file export remain focused on running; cycling is visible in the in-app shared calendar. Cycling activity share cards and structured cycling coaching remain future work.
