# OpenRun 1.4.4 — recording reliability

Post-start step-stall warnings, unfinished-session reminders, notification Finish/save review, conservative import duplicate review, background indoor timing/audio and versioned recording diagnostics. See [fixes and validation](docs/RELEASE-1.4.4.md). Install over the existing build without uninstalling after saving active sessions. Physical S23 testing and external integration activation remain pending.

## Previous releases

# OpenRun 1.4.3 — walking-pad recording

Start walk now asks indoors or outdoors. Phone step counting is enabled by default for the walking pad, with explicit Physical activity permission, live steps and estimated distance, a visible zero-step warning and machine-distance calibration. See [release and validation](docs/RELEASE-1.4.3.md). Save active sessions before updating; install over the current development build without uninstalling. Physical S23 acceptance remains pending.

## Previous releases

# OpenRun 1.4.2 — walking GPS recording fix

Preserves valid delayed GPS samples, warns when distance does not advance and supports manual correction of a known walking distance. See [release and validation](docs/RELEASE-1.4.2.md). Install over the current development build without uninstalling.

## Previous releases

# OpenRun 1.4.1 — compact typography

Smaller headings, card titles, body text and inputs. See [release and validation](docs/RELEASE-1.4.1.md). Install over the existing development build; JDK 21 is required to build.

## Earlier releases

# OpenRun 1.4.0 — rebuilt planning and local Gemma coaching

A reference-led native planning wizard and optional offline model manager, while retaining running/walking history, plans and holds. See [release](docs/RELEASE-1.4.0.md), [Gemma setup](docs/OFFLINE-COACH.md), [validation](docs/VALIDATION-1.4.0.md) and [remaining gaps](REMAINING-GAPS.md). JDK 21 is required for builds.

## Previous releases

# OpenRun 1.3.0 — race-first running and walking

Choose a race, add your own, and preview an original date-anchored plan from your running history and available days. The small offline catalogue is not nationwide live coverage. Gemini explanations and live catalogue updates need a configured backend.

See [release details](docs/RELEASE-1.3.0.md), [data and AI contracts](docs/RACE-DATA-AND-AI.md), [validation](docs/VALIDATION-1.3.0.md) and [remaining gaps](REMAINING-GAPS.md).

## Earlier releases

# OpenRun 1.2.1 — running and walking

This correction keeps the running/walking-only scope while restoring the training-first flow. It does not remove or recreate your existing running plan.

- Bottom tabs: Today, Plan, Calendar, Progress and Coach. Profile is available from the top-left icon.
- Today leads with the actual plan goal/current week, next workout and coach briefing before free run/walk controls.
- Plan retains full weekly scheduling, workout details, preferences, race dates, restart/new plan and reviewed week changes. The goal/current week summary is visible above the schedule.
- Calendar is a separate tab again.
- Coach has next-workout briefing/listening, workout access, saved-workout review, optional check-in, weekly workload review, library and audio preferences. These use existing deterministic/editorial guidance; no live professional coaching or Runna coaching engine is claimed.
- Gym/cycling stay removed. Archived data and running/walking histories are unchanged.

Version 1.2.1, code 16. Install over the previous development-signed app without uninstalling. Android 8+; target API 36.

See [validation](docs/VALIDATION-1.2.1.md) and [remaining gaps](REMAINING-GAPS.md).

## Historical release notes

## OpenRun 1.2.0 (superseded navigation)

This release narrows OpenRun to running and walking. Gym, strength routines and cycling workspaces, recording, imports, planning and reminder entry points are removed. Their existing stored records remain archived and exportable in backups; they no longer influence running plans. Pending cycling recordings are archived without inventing a completed activity. Existing running and walking checkpoints remain recoverable.

The dark OpenRun design retains original branding with a calmer surface palette and teal primary actions. Four tabs—Today, Plan, Progress and Profile—replace competing destinations. Today prioritises the next planned run, Start run, Start walk and indoor walking/treadmill entry. Plan opens the weekly schedule directly; Calendar and plan management live inside it. Progress separates Runs, Walks and Performance.

Outdoor walks have their own GPS recording identity, history, routes, GPX export and delete/undo. Indoor walking-pad sessions and calibrated phone steps remain supported. Walking totals never inflate running mileage. Health Connect imports running and walking only; missing walk distance remains time only. Walking Health Connect export is not implemented.

Retained: original adaptive running rules, scheduling, offline storage and backup, audio coaching, run/walk workout phases, Bluetooth HR, movement reminders and optional independently hosted services. No Garmin account connection is simulated.

Version 1.2.0 / code 15; Android 8+ (API 26), target API 36. Development signed APK, compatible with the retained development signature. Install over the previous build without uninstalling; export a backup first.

See [validation](docs/VALIDATION-1.2.0.md), [remaining gaps](REMAINING-GAPS.md) and [S23 checklist](tests/s23/CHECKLIST.md).

## Historical release notes (superseded where scope changed)

## What changed in 1.1.0

Cycling now has a dedicated Ride / Progress / Plan workspace: Road/MTB GPS, indoor timer, speed/route/elevation estimates, optional Bluetooth HR, cycling file imports, Health Connect ride import and explicit original GPS ride write, separate history, corrections/undo, planned-ride matching and shared weekly review. Walk/run features are retained. No fake distance, Garmin pairing or power/readiness scores. See [release details](docs/RELEASE-1.1.0.md), [validation](docs/VALIDATION-1.1.0.md) and [S23 acceptance](tests/s23/CHECKLIST.md).

## What changed in 1.0.3

Walking-pad/treadmill timer with optional calibrated Android phone-step distance, walking progress, opt-in movement/workout reminders, reviewed weekly changes, explicit workout matching/correction, kilometre splits, deletion undo, race-date plans and optional pre-session check-in. Indoor walking works without a running plan. No guessed distance, automatic treadmill control, sitting detection or badge system. See [release details](docs/RELEASE-1.0.3.md), [validation](docs/VALIDATION-1.0.3.md) and [S23 acceptance kit](tests/s23/CHECKLIST.md).

## What changed in 1.0.2

Start free run now uses a shared readiness flow with permission/location settings guidance, resume-after-settings checks and confirmed recorder startup. The inactive dashboard shows GPS/permission blockers. Goal selection still advances automatically. See [docs/RELEASE-1.0.2.md](docs/RELEASE-1.0.2.md).

## What changed in 1.0

Account-free onboarding with starter/returning/regular paths, exact preview-to-save schedules, mixed-activity weekly planning, native workout widget, improved recording notifications, transient audio focus, lifecycle checkpoint attempts, haptics/accessibility labels, route-free activity cards, local opt-in measurements and previewable beta reports.

Android compile/target is now API 36, with API 26 minimum. Source uses AGP 8.9.1 / Gradle 8.11.1; the standalone build uses the installed API 36 platform with Build Tools 35.0.0. The supplied CI matrix covers API 26/29/35/36 but has not executed here.

This is a beta release candidate. Physical-device validation, qualified coach review, Garmin partner approval and Play production setup remain blockers. Read [docs/RELEASE-1.0.md](docs/RELEASE-1.0.md), [REMAINING-GAPS.md](REMAINING-GAPS.md), and [docs/launch](docs/launch) for release and beta preparation. Additional activities are manually planned/logged; they are not multisport GPS or automatic wearable imports.

## What changed in 0.9

- Eighteen original offline coaching guides with category and text search, focus/practice/reflection structure, source links where applicable and explicit provenance.
- A Coach briefing in every planned workout, tailored to easy, long, tempo, interval or run/walk sessions. Existing training holds take priority; missing benchmarks never produce personalised pace claims.
- Optional spoken briefing through the existing TTS service. It respects the audio preference, reports unavailable engines and refuses playback during an active recording so workout cues take priority.
- Post-save reflections use the entered effort, fatigue and pain. Past-date entries receive a reflection without changing the current plan. Current-day adjustment dialogs include the reflection and retain the existing approval/hold behavior.
- Coaching is generated editorial text and deterministic rules: no remote AI service, live coach, qualified coach review or medical assessment. General references are background guidance, not endorsement or validation of OpenRun's rules.

## What changed in 0.8

This release expands the functional scope; it does not claim Runna parity or validated coaching equivalence.

- Preferred running weekdays and long-run day, with Race preparation, Base building, Maintenance and Return to running modes. Current/past weeks remain fixed when changing preferences; schedules persist in backups. Return mode has executable run/walk workout steps.
- Multi-session weekly review with transparent fatigue/effort and workload-spike rules; reductions require explicit review. Imported placeholder feedback does not drive suggestions. No automatic mileage increases or safety-hold clearing.
- Six original strength/mobility/yoga/Pilates routines, dumbbell option, session budget and progressive set volume gated by completed sessions.
- Workout/year/month filters; actual activity/strength milestones; continuous GPS-derived best efforts at standard distances, with interpolation, pause/gap/jump rejection and no average-pace extrapolation.
- Calendar file export (.ics) and individual workout insertion through the installed Android calendar. No silent calendar write permission or automatic external calendar updates.
- A native opt-in street map with OpenStreetMap attribution, application User-Agent and a seven-day cache. Only visible tiles are requested; no offline-area downloads or turn-by-turn navigation.
- Original decoder for standard single-session running FIT files, including definitions, ordinary/compressed timestamps, endianness, developer-payload skipping and checksum validation. Complex/multi-session FIT support remains limited; test with your watch's files.
- Optional Health Connect background reads, optional average-HR import for new records, and explicit write of OpenRun GPS exercise/distance records using stable client IDs. Platform support and grants are required. Route import and pre-Android-14 support remain absent.
- Optional self-hosted account service: encrypted backup upload/restore, conflict detection and periodic unmetered-network backup. Credentials/passphrase are protected with an Android Keystore key. The service is supplied as source/Docker configuration; it is not deployed.
- Official Strava OAuth, recent-run import, explicit GPX upload and asynchronous upload-status checks through the configured service. Real app credentials, callback and consent are required. No provider secret is bundled into Android.
- Training hub with original guidance and troubleshooting. Hosted human support, a community service and instructional videos are not supplied.
- Host/server tests and an Android API 29/35 CI matrix. CI and device tests have not been executed here.

**Updating 0.4–0.9:** install 1.0 over the existing app; signing identity is retained. Export a backup if you want a recovery copy. New v1 preferences/schedules/additional activities are backed up; newer backups are not intended for older app releases.

See `docs/RELEASE-1.0.md`, `docs/VALIDATION-1.0.md` and `server/README.md` for precise behavior, setup and remaining gates.

## What changed in 0.7

- Fix Health Connect settings navigation: use the public Health Connect home action from AndroidX; catch permission denial and try ordinary Android Settings before offering manual instructions. The privileged permission-management screen is no longer launched.
- Plan hub based on the supplied reference: Start new plan, Restart current plan and Continue plan. New plans use goal → current fitness → schedule → preview, then explicit save. Restart preserves saved activities, pace profile and training holds; pending recordings block plan replacement/restart.
- Compact centred app bar, contextual back navigation and a quick recorder shortcut. Plan and Calendar use distinct icons.
- Dedicated single-line Workouts/Performance tabs with accessible selected state, responsive metric cells and useful empty states. No arrows or visible selected suffix in tab labels.
- Shorter connection screens, optional integration details, concise import results and actionable error messages without Android intent/process internals.
- The same development certificate supports update over 0.4, 0.5 and 0.6 without uninstalling.

## What changed in 0.6

- Dark-first charcoal/slate theme, pale primary actions, teal accents, native dark dialogs and original OpenRun identity.
- Today / Plan / Calendar / Progress / Profile navigation, a free-run entry on Today, weekly agenda, and Workouts / Performance views using saved data.
- Grouped profile preferences and Connections screens with explicit Health Connect, Garmin account, file import and Bluetooth sensor states.
- Official Android 14+ Health Connect read integration: exercise/distance permissions, privacy rationale, foreground last-30-day import, source-filtered distance and stable record-ID deduplication.
- Health import is read-only and manual. No Health Connect background sync, route/HR import or record writes. Android 8–13 remain supported for the app, without Health Connect in this version.
- Garmin automatic two-way sync is still blocked by official partner approval, credentials and a deployed integration. No fake connected state or password collection.

**Updating 0.4 or 0.5:** install 0.6 over it; the development signing key and data format are retained. Export a backup first if you want a recovery copy.

## What changed in 0.5

- Current Material Symbols Rounded vector icons, selected navigation pills and accessible icon labels.
- Compact native app bar, cards, action hierarchy, ripple feedback and a week/date selector.
- Dedicated live run dashboard: distance, duration, pace, workout stage, target and sensor/audio status.
- System light/dark palettes and matching system-bar colors; adaptive launcher and Android 13+ monochrome icon resources.
- Weekly distance/completion summary and plan progress, calculated from saved runs.
- Detail screens reset their scroll position; smaller headings and responsive metric text replace the oversized text block.
- Short recorded distances appear in metres instead of misleadingly rounding to 0.0 km.

This is a native Android Views redesign using Material-inspired patterns and official Material Symbols. It does not migrate the app to Jetpack Compose or claim to include the complete Material 3 Expressive component library.

**Updating 0.4:** install 0.5 over it. Package identity, data format and signing key are retained. No uninstall or data migration is required. Exporting a backup remains available.

## What changed in 0.4

- Two original bodyweight strength/mobility routines with exercise prescriptions, movement cues and completion checklists.
- Persistent strength session history included in portable backup/restore.
- Strength work respects the existing pain-related training hold.
- APK inspection evidence and backend dependency report in `docs/APK-INSPECTION.md`.
- Updated storage and Android instrumentation coverage for strength data.

## What changed in 0.3

- Optional recent-race profile with estimated easy, steady, tempo and interval pace ranges.
- Structured interval and tempo sessions, warm-up, recovery and cool-down steps.
- Step transitions by time or distance, rolling GPS pace, split speech and sustained pace alerts.
- Pace review after three eligible quality sessions, with explicit approval before changing targets.
- Calendar rest days, skip/unskip, reschedule within the plan, and same-day collision checks.
- Full portable ZIP backup/restore: plan, profile, adjustments, schedule, history and GPS routes.
- Backward reads of 0.2 history, plus a fix for marathon-distance float precision on restart.
- Additional domain and Android instrumentation tests, including backup round trips and rejection of unsafe data.

## Install or update

Download `OpenRun-1.0-debug.apk` and open it on Android. Package `app.rwun`, minimum Android 8 (API 26), target Android 16 (API 36).

**Existing 0.3 installation:** the older signing key did not survive workspace cleanup. This APK uses a new development certificate and cannot install over 0.3. Export a full backup from 0.3 before uninstalling, install 0.4, and restore the backup. Uninstalling without exporting removes your saved data. The 0.4 development-only key is included under `development/` so future local builds can retain this update identity. It must never be used for production.

Check VALIDATION.md for the build and runtime checks actually completed. This remains a beta; physical outdoor GPS, phone audio/battery behavior and the exact wearable model require device validation.

## Running workflows

1. Plan → Start new plan: choose a goal, current fitness, schedule and preview. Create a plan from current weekly mileage, 2–5 runs/week, 4–24 weeks and a 5 km, 10 km, half-marathon or marathon goal.
2. Optionally enter a recent race distance and time in minutes. Use a recent result, not a goal time. Without a benchmark, workouts stay effort-based.
3. Experienced runners can explicitly enable speed sessions. The generator also requires at least three runs/week, 15 km/week, a six-week plan, and enough main-set distance. It adds at most one speed session/week after the first two weeks, excluding recovery/taper weeks.
4. View each workout's steps and estimated pace ranges. Planned distance is the main set; timed warm-ups, recoveries and cool-downs add distance beyond it.
5. Start a planned GPS workout or a free run outdoors with precise location enabled. Optional notification permission makes the recording easier to see.
6. Pause/resume from the run screen or notification. Finish/save and report effort, fatigue and pain. Missed workouts never add catch-up mileage.
7. Review workload or pace suggestions before applying them. Pain feedback pauses training until explicitly reviewed and resumed.

Profile → Pace profile & targets updates your benchmark and opt-in. The calendar supports moving incomplete workouts to an unused day within the plan, skipping and unskipping. Completed history is retained. Changing a profile regenerates the displayed plan; it does not rewrite saved activity records.

## How the coaching works

All rules are original, inspectable heuristics, not copied Runna logic or a validated physiological model.

The profile converts a recent result to an estimated 5 km equivalent using `time × (5 / distance)^1.06`. Pace ranges are multiples of its seconds/km: easy 1.22–1.45, steady 1.15–1.25, tempo 1.08–1.16 and intervals 1.00–1.06. These estimates are not a race prediction or instruction to ignore effort, terrain, illness or recovery.

The workout engine advances time/distance steps automatically. Current pace needs at least 15 seconds and 20 metres of GPS data in a roughly 30-second window. Stale data does not drive pace guidance. Pace alerts require about 15 seconds outside the range and are throttled to at most one every 30 seconds. Easy stages only warn when too fast; speed-up cues are limited to quality stages. Spoken cues depend on the installed Android text-to-speech engine and audio routing. Unsupported languages fall back to the device default/US English. Reported engine errors disable speech while text guidance remains available. The API 26 emulator’s Pico speech engine crashed natively during playback; physical audio playback is still unverified.

Workload adjustment rules:

- Pain: pause training with explicit review before resuming.
- Effort ≥8/10 or fatigue ≥4/5: suggest 80% of base distance for tomorrow and the following six days, all easy.
- Completion <70%: suggest 90% for that window.
- Otherwise maintain the plan. One comfortable run never increases workload.
- Adjustments replace prior adjustments; they do not compound indefinitely. After seven days the base schedule resumes.

Pace reviews require three completed GPS quality sessions within 21 days using the same benchmark. Sessions with recorded GPS gaps or incomplete quality blocks are excluded. If all three were quicker than target at effort ≤6 and fatigue ≤2, suggest a 1% quicker benchmark. If all were more than 8% slower at effort ≥8, suggest a 2% slower benchmark. Nothing changes without approval. Manual and Garmin imports do not invent interval-performance data.

Selecting a marathon does not establish readiness. Plans and supplementary rules need qualified coaching review before being considered a substitute for professional coaching.

## GPS and data recovery

GPS records full-workout distance, active duration and local route points. Poor accuracy, implausible jumps, pause jumps and signal-gap jumps are excluded; after a large jump, three consistent fixes can establish a new anchor without counting the jump. This can undercount distance. There is an offline route sketch, not street-map tiles or navigation. Recording uses a user-started foreground service and a wake lock.

A checkpoint is written about every five active seconds. If Android terminates the app or the device restarts, recording stops. Today → Start free run can recover the recorded portion; it does not synthesize missing time or resume tracking silently.

Profile → Export full backup saves a portable ZIP through Android's document picker. It includes private location and fitness data. Profile → Restore full backup replaces the current plan, profile, schedule, history and referenced routes after confirmation. Restore validates the schema, preference values, activity bounds, safe filenames, route contents and a 50 MB expanded-size cap. New routes are staged under fresh names before preferences are committed. Invalid backups are rejected before preferences are replaced. Back up while no recording or recovery is pending.

GPX route export remains available per activity. Automatic cloud backup is disabled. Uninstall removes private local data; keep an exported backup if you need recovery.

## Garmin and wearable support

- Completed Garmin GPX/TCX activity files can be imported manually and matched to a workout on the same date, or saved as unscheduled runs.
- Import accepts timestamped GPX/TCX and supported single-session running FIT files up to 10 MB. Courses without activity timestamps, chained files and multisport sessions are unsupported.
- Identical file imports are rejected. Different exports of the same activity can still require manual duplicate review.
- Standard Bluetooth Heart Rate service discovery, explicit sensor selection, live HR and session averages are implemented. Start a OpenRun GPS run before scanning.
- Original Instinct live HR uses ANT+, which OpenRun does not implement. Compatible later Instinct models/HR straps can use Bluetooth HR broadcasting. Check the exact device manual.
- Automatic Garmin Connect account sync, sending structured workouts to the watch, standalone watch recording and watch audio are not implemented. Official Garmin API access needs programme approval.

## Privacy

Core training remains offline, with no advertising or analytics. Internet access is used only for optional configured account/Strava operations and an explicitly opened street map. Optional automatic backup uses your chosen service and unmetered networks after setup. Routes, HR and feedback stay private on the device unless you choose an export, Health Connect write or optional service operation. Android system cloud/device backup is disabled. Exported backup/GPX files reveal location and fitness data. The selected Android TTS engine has separate settings and may use a network voice independently; use an installed offline voice if needed. No Garmin credentials are requested.

## Strength & mobility

Open **Today → Strength & mobility** or the same action in Profile. Choose a foundation, mobility, dumbbell, yoga, Pilates or post-run routine; dumbbells are shown when selected in Strength preferences. Complete each prescribed exercise block, tick it, then save the session. Completed sessions survive app restart and full backup/restore. Routines are original bodyweight guidance; there are no copied Runna videos, equipment-specific programs or claims of validated coaching equivalence.

## Build and test

Requires JDK 17, Android SDK platform 36 and internet for the first dependency download. Gradle wrapper 8.11.1, Android Gradle Plugin 8.9.1 and desugared Java support 2.1.4 are pinned.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
tests/run-domain-tests.sh
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w app.rwun.test/app.rwun.SmokeInstrumentation
```

The instrumentation suite uses a separate preferences namespace, not real run history. Domain tests exercise plan generation, GPS filtering, coaching transitions, target personalisation and pace reviews. Host parser tests use a test-only Android Xml adapter and `net.sf.kxml:kxml2:2.3.0`; with a local JAR, run `KXML2_JAR=/absolute/path/kxml2-2.3.0.jar tests/run-parser-host-tests.sh`. That dependency is not bundled in the app.

The GitHub Actions workflow builds APKs, runs domain tests and lint, and uploads the debug APK when this project is used as a repository root. It has not been executed remotely in this session.

## Architecture and remaining scope

Domain logic includes `StrengthPlan` and is in `PlanEngine`, `PaceProfile`, `WorkoutFactory`, `WorkoutEngine`, `RollingPace`, `PaceInsights`, `GpsAccumulator` and `AdaptiveCoach`. Android adapters are `TrackingService`, `HeartRateClient`, `RunStore`, `ActivityFiles` and `BackupManager`. `MainActivity` and `RouteView` own the native UI.

See PARITY.md for the remaining backlog: coach-reviewed race-specific programming, expanded strength/mobility programming, official Garmin sync, cloud accounts, a broader device test matrix and a complete accessibility/reliability pass. The app is not yet a product-equivalent Runna replacement.

## References

- [Runna pace-target behavior](https://support.runna.com/en/articles/6205998-adjusting-your-estimated-race-time-and-pace-targets) and [structured workouts](https://support.runna.com/en/articles/15690947-understand-your-runna-workouts) informed the functional backlog, not the implementation or training rules.
- [Garmin Activity API](https://developer.garmin.com/gc-developer-program/activity-api/).
- [Garmin broadcasting compatibility](https://support.garmin.com/en-US/?faq=Zj1947s6pqAHzBCAhLhrC9).
- [Android foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- [Android document picker](https://developer.android.com/training/data-storage/shared/documents-files).

Storage/backup host tests use test-only Android Context/SharedPreferences adapters with `org.json:json:20240303`. Run `JSON_JAR=/absolute/path/json-20240303.jar tests/run-storage-host-tests.sh`. These exercise the real repository and backup code, but do not establish Android filesystem lifecycle or device persistence reliability. Neither host-test adapter nor its JSON dependency ships in the app.


### Standalone SDK build used for 1.0

The Gradle wrapper could not reach its distribution through this runtime's Java networking path. The supplied APK was built with JDK 17, SDK platform 36, AAPT2, D8/L8 and apksigner using `build-standalone.sh`. Gradle sources remain available for Android Studio; Gradle/lint were not run for 1.0.

```sh
export ANDROID_SDK_ROOT=/absolute/path/to/android-sdk
export DESUGAR_JAR=/absolute/path/to/desugar_jdk_libs-2.1.4.jar
export DESUGAR_CONFIG_JAR=/absolute/path/to/desugar_jdk_libs_configuration-2.1.4.jar
./build-standalone.sh
```

The script uses build-tools 35.0.0 and the Android 36 platform. It requires JDK 17 `java`, `javac`, `jar`, `keytool`, Python 3, `unzip` and `zip` on PATH. Google desugaring libraries use their published license; see `docs/THIRD-PARTY.md`.

### New regression checks in 0.7

Run `bash tests/run-navigation-host-tests.sh` for protected-settings fallback and safe errors. The storage host suite also covers restart/history/hold preservation. Android instrumentation includes Workouts/Performance tab layout at font scales 1.0, 1.3 and 2.0; see the validation report for execution status.
