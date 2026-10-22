# Race data and optional AI service

## What works without a server

From Welcome, Create your plan or Manage plan, choose Race. Search the offline catalogue or add a road race manually (name, city, country, date, distance). Training plans support 5 km through 42.2 km, 4–24 weeks and 2–5 selected days. The exact preview is saved with the race in one preference commit. Existing runs, walks and training holds are retained. The build ends before race day; race day is a separate event marker. Selection does not enter the race.

The bundled catalogue is deliberately small: four dated variants in Cape Town, checked 18 October 2026. It includes the Cape Town Marathon, 5/10 km Peace Runs and Two Oceans Half Marathon. It is NOT a complete live South African calendar. Event availability, fees, travel, qualification and entry status must be confirmed with the organiser. Dates can change. No race photography, popularity counts, availability claims or copied race descriptions are supplied.

Fact sources:
- https://capetownmarathon.com/FAQ/ — marathon 23 May 2027.
- https://capetownmarathon.com/peace-runs/ — 5/10 km events 22 May 2027.
- https://www.discovery.co.za/corporate/discovery-two-oceans-marathon-sponsor — sponsor confirmation of Two Oceans half marathon, 3 April 2027.

## Backend catalogue

`GET /v1/races` is public on the optional OpenRun backend. Parameters: `q`, `country`, `distance` (km), `from`, `until` (ISO dates). It returns `updated`, `scope`, `races` and AI availability. Android downloads and validates the catalogue only when Refresh is pressed, atomically caches a successful response, and keeps the old catalogue on failure. Filtering then works offline. Set the HTTPS backend URL under Profile → Account & cloud backup; catalogue reads do not require sign-in.

The default server serves `races.json`. An operator may set `RWUN_RACE_FEED_URL` to an **authorised, HTTPS, normalised JSON feed** with the same schema. This is not an implemented vendor-specific Racepass/Runna API adapter. The fixed operator URL is never supplied by a search request. Redirects are refused, JSON is bounded to 4 MiB/5,000 variants, and a 15-minute in-memory cache/backoff preserves the last valid catalogue. Restart resets that cache to the bundled snapshot. No arbitrary pages are scraped. A provider offering a different schema needs an adapter after its contract and actual response are available.

A variant is `{id,name,city,country,surface,date,km,source,verified}`. Dates are ISO local event dates; each distance has its own date. `source` must be HTTPS. `verified` is when the source was checked, not a guarantee of entry availability. Feed scope and age remain visible.

Research found no verified, comprehensive, freely reusable South African endpoint ready for this build. RunningCalendar's terms require written permission for programmatic scraping/commercial reuse: https://runningcalendar.co.za/terms . Run This says it has machine-readable pages but no general public API: https://www.runthis.run/help/developers/getting-started . Bield documents a public feed at https://bield.run/api but the attempted endpoint request returned HTML rather than JSON; South African coverage and a working integration were not established. Race Roster APIs are private-access APIs. Do not infer coverage or permission from a public calendar page.

## History-aware rules

The race plan shows observed running totals over 28 days and days since the last recorded run. Three or more runs with at least a 4 km weekly average can cap an overstated manual baseline. A 14-day gap proposes a 20% reduction; 28 days proposes 35% and a run/walk return programme. Two recent high-effort/fatigue reports or the existing workload-spike rule can propose a lighter week. These thresholds are original software rules and need qualified coach review; they are not validated readiness scores. Imported effort is not inferred. Missing history remains unknown.

Coach → Adapt plan from recent running previews reductions for tomorrow through the next seven days. Past, completed and skipped sessions stay protected. The user applies the preview. A hash receipt prevents repeating the reduction with the same plan/history evidence. A hold or recent reported pain prevents recommendations; no hold is automatically cleared. Reductions do not increase mileage. Base progression remains deterministic; improvement does not trigger automatic mileage increases or finish-time promises. GPS/elapsed pace, terrain and missing wearable data do not silently become a readiness assessment.

## Optional Gemini explanations

`POST /v1/coach/explain` requires a signed-in OpenRun account, explicit `consent:true` and a strictly bounded numeric `summary`: `runs28`, `km28`, `daysSinceLast`, `factor`, `hold`, `goalKm`, `weeks`. The dialog discloses the recipient and fields. No names, routes, HR samples or individual activities are sent in this request. The server does not persist prompts/responses, and its access logger omits request details. Provider processing terms still apply. Operator proxy logging must also be configured appropriately.

The Gemini key exists only in the server environment, never in the APK:
```
GEMINI_API_KEY=<server-secret>
GEMINI_MODEL=<currently-supported-model-for-your-account>
```
No model is silently selected. Follow https://ai.google.dev/api/generate-content for the official REST contract. No SDK dependency is added. The service requests short explanatory text, validates complete provider output, bounds output size, and returns `changes_plan:false`. AI cannot supply/apply workouts or clear holds. Limits: three requests/minute and twenty/day per account, in memory; a production gateway should also enforce durable quotas. No retry loop creates additional provider calls.

Provider failures, absent keys/model, invalid requests or absent hosting return explicit errors. No live Gemini request was performed and no provider output is bundled as if generated for the user. A mocked transport verifies the contract only. OpenAI integration has not been implemented because Gemini is the selected optional provider for this release.

## Deployment

Use `server/compose.yaml`, HTTPS reverse proxy and existing account setup. The image now copies `race_services.py` and `races.json`. Set `RWUN_RACE_FEED_URL` only once the feed is authorised and follows the documented schema. Hosting, provider access, keys and production privacy setup are still operator work. There is no deployed OpenRun service bundled with the app.
