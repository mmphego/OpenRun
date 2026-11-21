# Daily movement and recovery

Open **Today → Movement & recovery** or **Profile → Movement & recovery**. Both movement monitoring and pre-session prompts start off. This is wellness guidance, not medical monitoring or clearance to exercise.

## Movement

Choose working weekdays and same-day hours (`00:00`–`24:00` supports a full day). Configure the lookback (60–720 minutes; default 60 for new installations), minimum lookback steps (default 50), break target (default 50), check interval (default 10 minutes), repeat interval (default 10 minutes), and snooze (default 15 minutes). Step targets and intervals are independent. Existing lookback and step settings are preserved on upgrade. Overnight windows are not supported. Enabling monitoring turns off legacy fixed-time prompts; planned-workout reminders are unchanged.

Android's JobScheduler runs one-shot checks on a best-effort basis. Android can delay them because of Doze, battery restrictions, force-stop or other platform policies; the configured interval is not a delivery guarantee. No background GPS or continuously running sensor service is used. A notification requires app and channel permission. The notification channel controls sound and vibration, and Android may override either; newly created channels request a bounded vibration pattern.

Health Connect aggregates steps across sources rather than manually summing overlapping phone/watch totals. Below the configured lookback target, OpenRun starts a break at the time of the first reminder and tracks a separate aggregate from then until the break target is reached. A completion notification confirms recorded progress. Health Connect publishers may delay syncing or provide coarse intervals, so counts and completion can be delayed or approximate. A fresh short record is needed to establish freshness; missing, stale, incomplete, denied or failed reads trigger a reminder explicitly labelled unverified rather than being treated as zero steps. A break remains active when its progress cannot be verified.

Snooze pauses alerts while checks and break tracking continue. Unfinished breaks repeat at the configured interval, with no daily cap, until confirmed complete. Working hours, skip today, active/pending recordings and training holds suppress alerts; an unfinished break does not carry into the next working day. The existing boot receiver restores scheduling. Permissions, a paused Health Connect read switch, or an unavailable provider prevent reads but do not disable reminders. Low recorded movement does not establish sitting or phone carriage.

The movement screen shows setup status for step access, background access, the OpenRun read switch and the movement notification channel. Returning from Android settings refreshes status without clearing unsaved edits. A test notification does not establish Health Connect readiness or prove vibration or sound playback.

Garmin Connect can publish supported data to Health Connect following device sync and configuration. This is historical data, not continuous watch access or a direct Garmin account integration. Verify data availability on the actual device. Live compatible Bluetooth heart-rate recording is separate.

## Recovery check-in

Optional answers include hours slept, fatigue and muscle soreness (unknown or 1–5), alcohol yesterday evening (unknown/skip, no, yes), feeling unwell (unknown, no, yes), and explicitly reported pain/injury symptoms. Unknown answers remain unknown.

When enabled, the check-in is offered before new GPS and indoor sessions, with an explicit skip. A saved answer is recent for six hours. Completing the form never starts a recorder. Existing permission, hold and pending-session gates remain in effect.

Suggestions explain their inputs: self-reported sleep below six hours, fatigue/soreness at four or five, or reported alcohol suggest considering easier activity or rest. These are transparent product heuristics requiring qualified review, not validated physiological or medical thresholds. Pain, feeling unwell and existing holds suggest recovery review. No check-in automatically changes a workout, marks it complete or clears a hold. Saving a pain hold requires a separate confirmation. Schedule changes use the existing reviewed weekly-change flow.

Optional, individually authorised sleep and resting-heart-rate context is read on demand. A recent sleep session is explicitly labelled a **window**, not time asleep. The latest resting-heart-rate measurement within 24 hours is compared with the median of daily medians over at least seven prior days within 14 days from the **same publisher**. No source mixing, HRV score, age-derived heart-rate ceiling or automatic adaptation is used. Comparisons remain contextual and do not override the manual answers. Each read is capped at 1,000 records and 30 seconds; incomplete input cannot become a physiological baseline.

## Privacy and durability

`DailyHealthStore` holds settings, reminder state and only the latest check-in in separate private preferences. Commits are durable across process death/reboot/upgrades without modifying training storage or Room schemas. A new check-in replaces the old one. The latest check-in expires after seven days and is purged on next app/feature access; there is no time-exact deletion job. Raw step/sleep/heart-rate records and comparisons are not persisted.

Feature state is absent from `RunStore.backupPreferences()`, portable/cloud backups, activity exports, optional metrics and local/server AI summaries. Android backup/device transfer is already disabled/excluded for the app. This feature sends no network requests, health uploads or model-training input, collects no GPS/venue history and makes no alcohol inference. Existing race/map/account networking and upstream publishing apps such as Garmin Connect remain separate.

Delete the latest check-in or all feature data through **Data, permissions & deletion**. Erasing all feature data disables monitoring and prompts, retaining workouts and Health Connect records. Permission revocation stops future reads, not saved answers. In-flight results are discarded following configuration changes, deletion or cancellation; revoked access is treated as unavailable data. Notifications include no alcohol/illness answers and use private lock-screen visibility.

## Components

| Component | Responsibility |
| --- | --- |
| `DailyHealthRules` | Pure movement-window, freshness and recovery rules |
| `DailyHealthStore` | Separate durable settings, check-in expiry and deletion |
| `DailyHealthSource` | Bounded official Health Connect reads and aggregation |
| `DailyHealthJob`, `DailyHealthActions` | Best-effort scheduling, break tracking, cooldown and notifications |
| `DailyHealthActivity`, `RecoveryPrompt` | Native preferences, optional check-in and explanations |

## Verification and physical acceptance

Host tests use synthetic inputs and actual native Views. They cover thresholds, unknown/stale data, quiet hours, cooldowns, persistence, expiry/deletion, backup exclusion, source-aware baselines, optional prompt handoff and recreation/large fonts. Host renders under `app/build/reports/native-qa` are not emulator or physical-device evidence.

Before release, use an S23 and an older Android device to verify:

1. Same-signature upgrades preserve history and interrupted recordings.
2. Steps/background reads/notifications can each be denied and revoked, including during an active read; unsupported services remain explicit.
3. Compare the lookback and break totals with Health Connect. Repeat with a phone left on a desk, delayed watch sync, missing permissions and stale records; unavailable data must produce an unverified reminder.
4. Working days, quiet hours, snooze, skip today, repeats, reboot, clock/time-zone changes, force-stop and Samsung battery settings behave as documented. Measure approximate delivery.
5. A break starts with its first reminder and completes when recorded steps reach its target, including while snoozed. Active/pending recordings and holds suppress alerts.
6. Optional check-ins before both recorders never silently alter training or clear a hold. Pain holds require confirmation.
7. Sleep-window and same-source resting-heart-rate context matches available records; no HRV or live Garmin readiness is claimed.
8. Feature data is absent from backups and optional AI payloads. Deleting data during a pending read cannot resurrect results or notifications. Test only with synthetic data.

References: [Health Connect reads/steps/aggregation](https://developer.android.com/health-and-fitness/health-connect/read-data), [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler), [Garmin → Health Connect](https://support.garmin.com/en-GB/?faq=JToBEy0jfe6pIygark2Ui5).

## Recovery check-in controls

The check-in uses visible single-choice chips instead of dropdowns. Skip maps to unknown, and the five fatigue/soreness levels retain their existing values. Rest, body and evening questions are grouped into cards; watch-data/privacy details expand only when requested. Save/review and continue-without-saving remain below the scrolling form. Sleep accepts 0–16 hours (including decimals) with inline validation. Unsaved choices survive recreation and expansion without being persisted as a check-in until Save. Large text uses two chip columns instead of three. Pain remains explicit and does not silently create or clear a training hold.

## Spoken notification sound

Configure spoken reminder creates a local WAV using the highest-quality installed offline voice for the device language. Enter up to 200 characters, such as “Get up and stretch a little”. This uses Android TTS; neural quality depends on the installed engine. Missing voices leave standard reminders available. The generated sound is used by test and movement notifications through a separate Android channel. Android controls notification volume, DND and channel sound overrides. Use standard sound disables the spoken channel selection. This does not bypass notification permissions or promise Samsung delivery.

## Notification and spoken-sound troubleshooting

Send a test reminder now displays a result dialog at your current scroll position, including the installed build and sound status. It uses a separate high-importance test channel with the active movement sound; Android still controls pop-ups, volume, DND and notification cooldown. The test posts independently of working hours and step freshness, but does not bypass a blocked movement channel. A posted notification is not proof of audible playback.

Preview spoken audio plays the generated file directly following a tap, independently of Android notification URI playback. This separates synthesis/file problems from channel and system playback problems. Creating a spoken sound allocates a new channel and versioned URI so an old immutable channel cannot retain a previous phrase or silent sound. The old spoken channel is removed after selection of the new one. New channels use high importance; Android notification settings remain authoritative.

When Health Connect movement detection is active, fixed-time movement controls are hidden and replaced with an explanation and a link to the active movement settings. Other dark-toned buttons are normal enabled secondary actions, not disabled controls.
