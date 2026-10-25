# Daily movement and recovery

Open **Today → Movement & recovery** or **Profile → Movement & recovery**. Both movement monitoring and pre-session prompts start off. This is wellness guidance, not medical monitoring or clearance to exercise.

## Movement

Choose working weekdays and same-day hours (`00:00`–`24:00` supports a full day), an inactivity interval of **60–720 minutes** (default 120) and a walking break of **3–60 minutes** (default three). Overnight windows are not supported. Enabling step-based monitoring turns off legacy fixed-time movement prompts; planned-workout reminders are unchanged.

Android notifications, Health Connect steps and separate background health reads require consent. Android 14+ framework Health Connect and supported background reads are needed for monitoring. Manual check-ins remain usable on Android 8+.

An inexact JobScheduler job runs approximately every 15 minutes without network, GPS or a continuously running sensor service. The first alert waits a full interval after enabling/changing monitoring, the beginning of working hours, or an **Already moved** confirmation. Navigation does not continually reset the scheduled job.

Health Connect aggregates steps across sources; OpenRun does not manually sum overlapping phone/watch totals. Fewer than 50 steps over the trailing interval is treated as low **recorded** movement. It does not establish sitting, continuous inactivity or phone carriage. Freshness also requires a short step record (15 minutes or less) ending within the last 20 minutes. Broad daily watch summaries cannot establish recent freshness. Missing, stale, failed, future or truncated reads suppress alerts instead of becoming zero steps. Providers that do not publish zero-step intervals may never provide a sufficiently fresh low-movement signal. The UI explains insufficient data; no sitting claim is substituted.

Notifications offer **Start walk**, **Already moved** and **Snooze 30 min**. Start walk opens the indoor timer with the configured goal and requires an explicit start. Already moved resets the interval without inventing steps. Snooze allows a fresh check after 30 minutes. Skip today is available in preferences. Alerts repeat no more frequently than the configured interval, up to eight per local day. Active/pending recordings, training holds, quiet hours and unavailable/revoked permissions suppress delivery. Completing an indoor activity acknowledges movement. The existing boot receiver restores scheduling; force-stop, battery restrictions and sync delays affect delivery.

Garmin Connect can publish supported data to Health Connect following device sync and configuration. This is historical data, not continuous watch access or a direct Garmin account integration. Verify data availability on the actual device. Live compatible Bluetooth heart-rate recording is separate.

## Recovery check-in

Optional answers include hours slept, fatigue and muscle soreness (unknown or 1–5), alcohol yesterday evening (unknown/skip, no, yes), feeling unwell (unknown, no, yes), and explicitly reported pain/injury symptoms. Unknown answers remain unknown.

When enabled, the check-in is offered before new GPS and indoor sessions, with an explicit skip. A saved answer is recent for six hours. Completing the form never starts a recorder. Existing permission, hold and pending-session gates remain in effect.

Suggestions explain their inputs: self-reported sleep below six hours, fatigue/soreness at four or five, or reported alcohol suggest considering easier activity or rest. These are transparent product heuristics requiring qualified review, not validated physiological or medical thresholds. Pain, feeling unwell and existing holds suggest recovery review. No check-in automatically changes a workout, marks it complete or clears a hold. Saving a pain hold requires a separate confirmation. Schedule changes use the existing reviewed weekly-change flow.

Optional, individually authorised sleep and resting-heart-rate context is read on demand. A recent sleep session is explicitly labelled a **window**, not time asleep. The latest resting-heart-rate measurement within 24 hours is compared with the median of daily medians over at least seven prior days within 14 days from the **same publisher**. No source mixing, HRV score, age-derived heart-rate ceiling or automatic adaptation is used. Comparisons remain contextual and do not override the manual answers. Each read is capped at 1,000 records and 30 seconds; incomplete input cannot become a physiological baseline.

## Privacy and durability

`DailyHealthStore` holds settings, reminder state and only the latest check-in in separate private preferences. Commits are durable across process death/reboot/upgrades without modifying training storage or Room schemas. A new check-in replaces the old one. The latest check-in expires after seven days and is purged on next app/feature access; there is no time-exact deletion job. Raw step/sleep/heart-rate records and comparisons are not persisted.

Feature state is absent from `RunStore.backupPreferences()`, portable/cloud backups, activity exports, optional metrics and local/server AI summaries. Android backup/device transfer is already disabled/excluded for the app. This feature sends no network requests, health uploads or model-training input, collects no GPS/venue history and makes no alcohol inference. Existing race/map/account networking and upstream publishing apps such as Garmin Connect remain separate.

Delete the latest check-in or all feature data through **Data, permissions & deletion**. Erasing all feature data disables monitoring and prompts, retaining workouts and Health Connect records. Permission revocation stops future reads, not saved answers. In-flight results are discarded following configuration changes, deletion, cancellation or revoked access. Notifications include no alcohol/illness answers and use private lock-screen visibility.

## Components

| Component | Responsibility |
| --- | --- |
| `DailyHealthRules` | Pure movement-window, freshness and recovery rules |
| `DailyHealthStore` | Separate durable settings, check-in expiry and deletion |
| `DailyHealthSource` | Bounded official Health Connect reads and aggregation |
| `DailyHealthJob`, `DailyHealthActions` | Permission-gated scheduling, cooldown and notifications |
| `DailyHealthActivity`, `RecoveryPrompt` | Native preferences, optional check-in and explanations |

## Verification and physical acceptance

Host tests use synthetic inputs and actual native Views. They cover thresholds, unknown/stale data, quiet hours, cooldowns, persistence, expiry/deletion, backup exclusion, source-aware baselines, optional prompt handoff and recreation/large fonts. Host renders under `app/build/reports/native-qa` are not emulator or physical-device evidence.

Before release, use an S23 and an older Android device to verify:

1. Same-signature upgrades preserve history and interrupted recordings.
2. Steps/background reads/notifications can each be denied and revoked, including during an active read; unsupported services remain explicit.
3. A 60-minute interval and three-minute walking goal work with fresh low-step records. Compare records with Health Connect; repeat with a phone left on a desk and watch awaiting sync. Missing/stale/daily-only data must suppress alerts.
4. Working days, quiet hours, snooze, already moved, skip today, repeat limits, reboot, clock/time-zone changes, force-stop and Samsung battery settings behave as documented. Measure approximate delivery.
5. Suggested walks require explicit start, preserve the configured duration, and record normally. Active/pending recordings and holds suppress movement notifications.
6. Optional check-ins before both recorders never silently alter training or clear a hold. Pain holds require confirmation.
7. Sleep-window and same-source resting-heart-rate context matches available records; no HRV or live Garmin readiness is claimed.
8. Feature data is absent from backups and optional AI payloads. Deleting data during a pending read cannot resurrect results or notifications. Test only with synthetic data.

References: [Health Connect reads/steps/aggregation](https://developer.android.com/health-and-fitness/health-connect/read-data), [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler), [Garmin → Health Connect](https://support.garmin.com/en-GB/?faq=JToBEy0jfe6pIygark2Ui5).

## Recovery check-in controls

The check-in uses visible single-choice chips instead of dropdowns. Skip maps to unknown, and the five fatigue/soreness levels retain their existing values. Rest, body and evening questions are grouped into cards; watch-data/privacy details expand only when requested. Save/review and continue-without-saving remain below the scrolling form. Sleep accepts 0–16 hours (including decimals) with inline validation. Unsaved choices survive recreation and expansion without being persisted as a check-in until Save. Large text uses two chip columns instead of three. Pain remains explicit and does not silently create or clear a training hold.
