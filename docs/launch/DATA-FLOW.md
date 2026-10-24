# Data-flow review for Play declarations — draft, not a submitted declaration

| Data | Default destination | Optional transfer |
|---|---|---|
| Running plan, feedback, other activities | App-private local preferences | User-exported backup; encrypted snapshot to configured service |
| GPS route and timestamps | App-private local route file | Explicit GPX export, Strava upload or encrypted backup |
| Bluetooth heart rate | Recording session and optional local average | Selected recorded-run exports/backups; inspect enabled integration |
| Health Connect exercise/distance/optional HR | Permission-gated recent run/walk import | Local history/backups; no automatic Strava forwarding |
| Original OpenRun exercise/distance | Separate write consent + per-activity review | On-device Health Connect; stable IDs, no routes/HR samples, no imported-activity echo |
| Local usage event counts | Off by default; app-private, rolling 90 days | Only explicit plain-text summary sharing; excluded from backups |
| Beta feedback | Form in memory | User previews text and selects a sharing app; no support endpoint |
| Activity card | User-selected document destination | Explicit image share; distance/time and optional date only |
| Map tile requests | None until online map consent | Tile provider sees network requests and requested map tiles; tiles imply area |
| Account email/password and provider tokens | No preconfigured service | Optional configured HTTPS service; account auth and provider OAuth |

No advertising, advertising SDK, ad identifier, automatic analytics uploader or crash-collection SDK is included. Do not infer that this makes every Play Data safety answer "not collected": actual hosting, account features, provider handling, tile services, support process and production configuration must be reviewed by the publisher. A document picker can point at cloud storage, and the chosen sharing app has its own privacy policy.

Core use needs no account. Local history can be deleted in-app and removed by uninstalling; backups/shared images/cloud account data persist separately. Cloud account deletion is implemented in the optional service, but a real public deletion/support URL is not supplied. Revoke Health Connect/provider grants separately. Human-reviewed public privacy wording and store declarations remain a release gate.


## 1.0.3 indoor and reminders
Optional physical activity permission → Android step counter → user-started health foreground service → active-session private checkpoints → labelled indoor history. Calibration is local; no step data is sent to a third party by this feature. Optional user-initiated training backups include saved indoor history/calibration. Active timers, undo and notification consent are excluded. Local reminder alarms → notification → user opens a session; there is no sitting detector or automatic recording.


## 1.1 cycling
Road/MTB: precise GPS and optional Bluetooth HR → shared location/connected-device foreground recorder → private cycling route/history. Indoor: monotonic timer and optional entered machine distance, without GPS or step-based distance. Explicit file/Health Connect imports → separate cycling history → optional reviewed plan link. Only original GPS rides can be explicitly written to Health Connect with stable IDs/revisions; imported activities are not echoed and routes are not written. Cycling history/routes are included in chosen private training backups. No new hosted service, cadence/power metrics or automatic Garmin pairing is implied.
