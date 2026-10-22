# OpenRun 0.8 — scope and acceptance

| V0.7 gap | Added in 0.8 | Limit |
| --- | --- | --- |
| Fixed running weekdays | Weekday/long-day preferences and fixed schedule snapshots | Changes start next full plan week; a new plan resets running preferences |
| Narrow planning | Four programme modes and run/walk steps | Original heuristics, not Runna's backend or professionally validated plans |
| Single-session adaptation | Multi-session weekly workload review | Explicit reductions only; no automatic performance gains inferred |
| Two strength routines | Six original routines, equipment, time budget and set progression | Text instructions; no video library or coaching review |
| Basic analytics | Filtering, milestones and GPS best efforts | GPS estimates, not certified race records; manual/summary-only runs cannot yield splits |
| No calendar integration | ICS export and calendar-app insert intent | Export/import is not continuous bidirectional calendar sync |
| Route sketch only | Optional native online street map | OSM availability, no turn-by-turn or offline area packs |
| No FIT import | Bounded FIT running-activity parser | Synthetic fixtures tested; unusual watch/developer/multi-session files may be rejected |
| Manual Health Connect | Optional background read, avg HR and explicit local-GPS writes | Android 14+ and module support/grants; no route read, no pre-14 provider |
| No account/cloud | Supplied account service and client-encrypted snapshot backup/recovery | You must deploy HTTPS; no live managed service, record merging or email password reset |
| No Strava | OAuth, run import, explicit upload/status via service | Registered credentials, callback, user consent and live tests required |
| No Garmin account sync | Existing file import improved; explicit service blocker | Official partner approval, credentials and documentation still required |
| Limited support | Original training hub and troubleshooting | No human staff or community moderation service |

## Where to find it

- Plan → Training preferences: programme, running days and long-run day.
- Today → Strength & mobility → Strength preferences: time budget, equipment and volume.
- Progress → Workouts: filters; recorded route cards expose Health Connect and Strava export actions where supported.
- Progress → Performance: milestones and route-derived best efforts.
- Calendar → Export training calendar, or workout details → Add to device calendar.
- View route → Open online street map; tile requests begin only after consent.
- Profile → Connected apps & devices: Health Connect optional background/HR grants and Strava.
- Profile → Account & encrypted cloud backup: requires your deployed service. `server/README.md` documents setup.
- Profile → Training hub: guidance and troubleshooting.

## Safety/data rules

Past/current-week sessions are retained on preference changes. New/restarted plans keep saved activities and any training hold. Paused/pending recordings prevent plan replacement and backup/restore. Cross-instance repository writes are serialized to prevent UI/import races. Backups validate before replacing data. Do not downgrade an installation with newer schedule/preferences without exporting data first.

Automatic cloud jobs use unmetered networks and Android-controlled periodic scheduling. Backups are snapshots, not record-level merging; revision conflicts stop overwrite. Passphrases are unrecoverable if lost. Cross-source duplicate activities remain a review task.

## OpenStreetMap

Only tiles for the actively displayed viewport are requested from https://tile.openstreetmap.org. Cache lifetime is at least seven days, a stable OpenRun User-Agent is sent, and attribution stays visible. No background/prefetch or offline-area feature is offered. Provider availability has no SLA. Policy: https://operations.osmfoundation.org/policies/tiles/

## FIT

Original implementation from the public protocol: https://developer.garmin.com/fit/protocol/ . It is not Garmin's SDK and does not claim exhaustive FIT compatibility. Chained/multisport sessions and unknown unsupported records are rejected or skipped conservatively.
