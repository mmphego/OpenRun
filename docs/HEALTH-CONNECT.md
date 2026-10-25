# Health Connect

The integration uses the official Android 14+ framework API, retaining Java and Android Views. Android 8–13 can still use core OpenRun features; this integration does not include the older standalone Health Connect app adapter.

Open **Profile → Connected apps & devices → Health Connect**. Two master switches control **Read from Health Connect** and **Write to Health Connect** independently. Enabling either opens a data chooser and Android permission request; it does not transfer an activity. The switches reflect actual allowed data, including partial denial and permissions revoked outside OpenRun.

The read chooser offers runs/walks with distance, heart rate, steps, active calories, sleep and resting heart rate. Only selected permissions are requested; deselection and the Read switch gate all OpenRun Health Connect adapters, including Movement & recovery. Previously granted choices remain selected on upgrade. Runs and distance remain paired for safe activity imports. Use **Read selected data now** to import activities and display permitted daily aggregates. Steps and active calories are today-to-now totals; heart rate is the available today-to-now average; sleep/resting HR use the existing bounded recovery context. Missing data remains unavailable. These summaries are not persisted or converted into workouts.

Writing supports one selectable group: original run/walk sessions and recorded distance. It continues to require choosing and reviewing each activity. Steps, calories, sleep and heart-rate sample exports are intentionally unavailable: OpenRun does not retain the original samples needed to write them reliably. No values are fabricated.

Turning a master switch off pauses that direction locally, without deleting imports or system records or revoking the other direction. Read-off cancels an active screen import and stops background activity imports. A write already submitted to the platform may finish. **Manage Health Connect access** opens Android controls for actual system permission revocation. Background activity import uses one separate switch, still requires explicit supported background permission and never writes. Other summaries remain foreground-only.

| Direction | Included | Behaviour |
| --- | --- | --- |
| Read | Last 30 days of runs, treadmill runs and walks; publisher-matched distance; optional average heart rate | Manual import, with separately consented background reads when supported. Walks stay outside running mileage. |
| Write | Original OpenRun GPS runs/walks and indoor run/walk recordings with real start/end times | Choose an activity and review it before writing its session and known distance. |
| Excluded | Imported activity exports, routes, step totals and sleep from the activity import/export flow, heart-rate samples, feedback and medical records | No unsupported data is fabricated or silently shared. |

The separately opted-in [Movement & recovery](DAILY-HEALTH.md) feature can read aggregate steps, sleep-session windows and resting heart rate. Its permissions and local-only storage are independent of activity import/export.

## Permission and data handling

- Reading requests `READ_EXERCISE` and `READ_DISTANCE`; average HR needs separate `READ_HEART_RATE` consent. Writing requests `WRITE_EXERCISE` and `WRITE_DISTANCE` without requiring read access.
- Background reads require the platform permission/extension support, core read grants and separate `READ_HEALTH_DATA_IN_BACKGROUND` consent. JobScheduler controls six-hour scheduling; the job never writes. Revoked access prevents future jobs; disabling the job stops scheduled reads.
- Imports ignore this package's own records, keep source identity and real timestamps, skip unsupported/invalid sessions, and preserve already saved data after a later page fails. Exact repeats are skipped; uncertain cross-source duplicates wait for review. Provider edits/deletions are not mirrored into local history.
- Reads are bounded to 100 pages of 100 sessions, detect repeated tokens and expire after two minutes. Cancellation/late callbacks cannot continue the import after it has finished. Optional HR failure does not discard an otherwise valid session.
- Export previews are fingerprinted. A modified/deleted recording requires a fresh review. Timestamp-less legacy entries remain local; calendar dates never become invented recording times. Original recorded wall-clock intervals include pauses; active time is labelled in session notes.
- Outdoor walks use the walking exercise type; indoor runs use treadmill running. Unknown distance produces only a session, never a zero-distance record. If this installation previously confirmed a distance export, changing it to time-only is blocked with an instruction to manage the old distance in Health Connect; no silent stale-distance update or automatic deletion is performed. Machine-entered/corrected distance is labelled as manual entry; phone-step estimates stay labelled as estimates.
- Client IDs retain the `rwun-session:` and `rwun-distance:` prefixes for compatibility. Repeats reuse the same client IDs/version; changed content allocates a higher version before the platform upsert. A private local export ledger preserves versions across failures/restarts. A missing acknowledgement is shown as unconfirmed, not success; retry uses the same IDs.
- The ledger is excluded from portable backups. Restoring an older backup or changing device clocks can affect conflict ordering against an already exported higher version; physical restore/service acceptance remains pending. Local deletion never deletes Health Connect data; manage those records in Health Connect.

## Architecture and validation

`HealthAccess` handles API availability and consent. `HealthSync.Source` separates native reads/aggregation from the bounded importer and existing `RunStore` duplicate policy. `HealthWritePlan` selects original recordings and creates the reviewed snapshot. `HealthExport.Writer` separates versioned upserts from native record construction. `HealthConnectActivity` owns permission requests and review; `HealthJob` is read-only.

Run `bash scripts/check.sh`. `HealthConnectTest` exercises real Android record builders plus controlled asynchronous sources/writers. `HealthConnectUiTest` covers the native permission screen, no automatic write on permission request and old-Android availability. These host tests do not establish real Health Connect service, OEM or S23 behaviour.

Before a production release, use a real device to test separate read-only/write-only grants, partial denial/revocation, a known external run/walk import, an OpenRun run and walking-pad export, time-only export, corrected-distance re-export, duplicate prevention, background permission support and in-place upgrade with the intended signing certificate. No production release is published by this branch.

Official references: [read data](https://developer.android.com/health-and-fitness/health-connect/read-data), [write/upsert data](https://developer.android.com/health-and-fitness/health-connect/write-data), [framework permissions](https://developer.android.com/reference/android/health/connect/HealthPermissions), [metadata/version conflict rules](https://developer.android.com/reference/android/health/connect/datatypes/Metadata.Builder).
