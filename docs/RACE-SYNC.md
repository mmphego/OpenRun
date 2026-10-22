# Race discovery and local synchronization

`OpenRunApplication` detects foreground sessions with Activity lifecycle callbacks and a one-second background debounce. Activity changes and configuration recreation do not schedule a fresh job per screen. It enqueues persisted, network-constrained `RaceSyncJob` job 82 using the existing JobScheduler infrastructure. No additional WorkManager dependency is introduced.

Scheduling returns immediately. RaceDiscoveryActivity reads only Room on a worker executor and observes changes; provider fetches never gate startup/navigation. The job seeds the small offline catalogue, then the coordinator attempts configured adapters with two workers, bounded HTTP/time/page limits, an in-process atomic guard and a 12-minute expiring DB lease. Job cancellation cancels HTTP and worker futures. Network failures retain existing catalogue and saved races.

| Provider | Endpoint | Default status |
| --- | --- | --- |
| Top Events | `https://topevents.co.za/wp-json/tribe/events/v1/events?per_page=50&page=1` | Enabled adapter; public feed availability varies |
| Bield | `https://app.bield.run/api/races/public.json?page=1` | Enabled adapter; international endurance coverage |
| RunSignup | `https://api.runsignup.com/rest/races?format=json&country=ZA&events=T&results_per_page=100&page=1` | Disabled pending caching approval and credential proxy |

The RunSignup adapter exposes its blocker instead of claiming a successful fetch. The operator must review provider terms and authorisation separately. No secrets go in the APK.

Room stores editions, distance options, provider references, last-seen observations, HTTP validators, provider status and saved user choices separately. Provider identity includes the edition year. Duplicate candidates require matching normalised name/date/country and corroborating venue/source or city plus distance. Ambiguous editions remain separate. Source priority is Top Events, Bield, RunSignup, then starter entries. Unknown locations, surfaces and distances are not guessed.

Identical source hashes skip content rewrites; observations and last-seen still change. A changed source updates content transactionally without deleting saved choices or plan references. Withdrawn distance options become unavailable rather than disappearing. ETag/Last-Modified conditional requests reuse page metadata. Incremental requests are only used by the documented RunSignup adapter, with periodic full reconciliation. Page caps and repeated-page checks report partial coverage instead of wiping the DB.

The current search screen supports text search, pagination, saved races, source links and distance-to-plan handoff. The repository query supports further location/country/surface/distance/date filters, but those controls are not yet exposed in the new catalogue screen. Unknown-distance entries can be inspected through their source; supported road-running distances can enter plan preview. No race registration is purchased by the app.

Tests in `RaceRepositoryTest` cover Room behaviour, content identity, merge references, saved choice preservation and lease exclusion. Additional HTTP/cancellation/provider fixture tests are roadmap work. Live endpoints were not all revalidated as part of publication preparation; local tests do not establish nationwide race coverage.
