# AI Coach with your own API key

AI Coach uses OpenAI or Google Gemini with your own API key. Local Gemma support
has been removed; no model download or inference runtime is included. Open **AI Connections** from Profile settings to configure and test a provider without creating a plan. The Coach tab and plan onboarding continue to open the coaching screen. Select **OpenAI** or **Gemini**,
enter a model ID and your own API key, optionally set a default goal pace in min:sec/km,
read and enable data-sharing consent, then save settings. Defaults are `gpt-5-mini`
and `gemini-3.8-flash`; model access and availability depend on the provider account.
The model ID is editable. API usage is billed by the provider; ChatGPT subscription
billing is separate. No server deployment or downloaded model is needed.

With no saved plan, **Create my AI running plan** opens **Your AI running goal**.
Enter target distance (5–42.2 km) and target pace (3:00–30:00 min/km), for example
10 km at 6:30 min/km. A race can also supply the distance and deadline.
Continue through recent-running baseline, weeks and available training days; select
**AI personalised plan · OpenAI / Gemini**, then **Generate AI plan**. The selected
goal appears before generation and in the accepted preview. Inspect **Review all
workouts**, choose **Create my plan**, and confirm. No existing plan is required:
the internal deterministic schedule supplies a safety envelope; only the accepted
AI schedule is saved. Provider failure leaves a clearly labelled standard-plan
alternative that requires its own preview and acceptance.

With an existing plan, **Personalise my existing plan with AI** proposes changes to
upcoming easy sessions; review and apply them explicitly. **Create a new AI goal
plan** starts the distance/pace flow and only replaces the current schedule after
acceptance. Both goals are stored with the plan and survive reopening and backup
restore. Provider/default-pace edits do not change this saved goal. Older plans
without a saved pace retain their previous provider-default behaviour. Standard
plan replacement clears a previous plan's pace goal.

**Ask coach** is a separate read-only conversation: it works without a plan,
without eligible easy workouts, and during a recovery hold. It sends recent run
summaries and, when available, the saved goal, phase, completed/expected workouts
and next-run context. An answer cannot apply a schedule proposal. Questions are
single-turn; conversation history is not stored or uploaded. Errors and results
scroll into view instead of remaining above the question field.

See the [illustrated AI flow in the user manual](USER-MANUAL.md#ai-flow-in-screenshots) for native screenshots of provider setup, goal entry, training days, plan preview and coach answers.

## Temporary provider failures

Foreground connection tests, plan generation and coach questions make at most **three
attempts** for HTTP 408, 429, 500, 502, 503 and 504. Retries wait 1 then 2 seconds,
with 0–500 ms of random jitter. A valid `Retry-After` seconds or HTTP-date header
is honoured up to 30 seconds; a longer requested delay ends this foreground request
so it can be retried later rather than sending prematurely. The status shows the
HTTP code, wait and next attempt number and is a polite accessibility live region.

Authentication/access errors, unsupported models, malformed or incomplete answers,
and ambiguous network/timeout failures are not automatically resent by this foreground
loop. Cancelling the screen or changing/revoking saved credentials stops subsequent
attempts. Retries use the same provider/model and never silently switch or change keys.
Persistent errors leave the saved plan unchanged. A 503 message identifies the selected
provider/model as unavailable; it does not declare the API key invalid.

Background reviews use a single HTTP attempt per JobScheduler execution and retain
the selected **Keep current plan / Retry automatically / Pause AI reviews** policy.
They do not multiply foreground retries with scheduled backoff. Connection tests use
saved settings, so save a changed key/model before testing.

Synthetic HTTP regressions cover recovery after 503s, exhaustion, transient/permanent
status handling, header waits, jitter, cancellation, credential revocation, connection
cleanup and unchanged stored plans. Native UI checks cover readable large-font progress
and accessibility announcements. Live provider outages and physical-device interruption
remain separate acceptance checks.

## Automatic post-run reviews

Enable **After each saved or imported run** to allow validated updates without
confirming each one. Newly persisted running history or explicit run reflections schedule one network-constrained,
persisted Android JobScheduler job. Foreground entry and reboot/update also check for
unreviewed history. Runs imported from Health Connect or files use the same storage
listener; walking remains excluded. Unknown-distance/time-only runs cannot supply pace.
Android controls when the job runs; this is not an immediate-delivery guarantee.
Several runs saved before a job executes are reviewed together.

The job uses the saved distance and pace goal, the current phase and newly recorded running evidence to update upcoming easy sessions in the next seven days, stores a processed
history token in the same transaction as the plan changes, and optionally notifies
you. Changes to history/plan while the request is running invalidate its proposal.
A stopped job cannot apply its result; pending work is retried by Android. Connection
and timeout failures, HTTP 429 and provider outages request Android backoff only when
**Retry automatically** is selected. **Keep current plan** leaves the schedule intact
and waits for a later trigger or manual retry; **Pause AI reviews** turns automatic
reviews off. Authentication and invalid/incomplete outputs never request automatic
retries. Failures from an old settings revision cannot pause newly saved settings. Failed calls preserve workouts. The screen retains the latest
review/status. Notifications use a separate private channel and Android permission;
a blocked notification never prevents a saved review.

Inspect **View plan changes and undo** for before/after workouts, provider and reason.
Existing rules decide whether undo is still eligible after later activities/changes.
Remove the key to cancel pending jobs and disable future cloud coaching. A request
already transmitted may still complete at the provider; its result cannot apply after
settings revocation. Removing AI settings leaves saved plans and activities intact.

## Validation boundaries

These are conservative software limits, not validated exercise prescriptions:

- The existing goal, training days, session IDs/dates and frequency are retained.
  Past, completed, skipped and active sessions are protected. A recovery hold blocks
  AI plan changes. AI cannot clear it.
- Quality workouts and beginner run/walk stages retain their existing prescriptions.
- Per-run distance proposals may reduce an easy run to at least half its current
  distance, with an absolute 0.1 km minimum. Increases require at least three recent
  reviewed runs on distinct days, no pain, effort at most 5/10 and fatigue at most
  3/5, with the latest within seven days. Increase is capped at 4% of both the current
  workout and its original baseline, preventing compounding across reviews.
- The last fortnight before a race cannot receive distance increases. Latest high
  effort/fatigue caps proposed distances at 80% of their current distance. Missing feedback
  cannot establish readiness for an increase. Imported placeholder effort/fatigue/pain
  values remain unknown unless the runner explicitly reflects on that activity.
- Observed pace is total recorded seconds divided by distance over recent runs
  with both known distance and duration; time-only activities are excluded from
  both totals,
  not a race benchmark or readiness estimate. No numeric AI pace is accepted without
  observed pace, or faster than that observation. A previous AI pace cannot become
  more than 2% faster in one revision. The goal pace informs the explanation, never
  overrides these limits. Easy workout steps use the accepted pace, with an 8% slower
  range; effort/recovery guidance takes priority.
- Invalid, incomplete or oversized provider responses fail closed. No partial
  proposal is applied. An empty proposal can mean the current plan is appropriate.

## Data and credentials

Post-run/manual coaching includes up to 40 runs from the last 28 days; full-plan
onboarding includes up to 30 runs from that window: dates, distance, duration,
computed pace, and available effort/fatigue/pain flags, alongside goal and upcoming
workouts. No routes, names, raw heart-rate records, sleep or Movement & recovery
check-ins are included. User-typed coach questions are sent verbatim.

Calls go directly over HTTPS to `api.openai.com/v1/responses` (Bearer authorization)
or `generativelanguage.googleapis.com/v1beta/models/<model>:generateContent`
(`x-goog-api-key` header). Redirects are disabled. Credentials never enter URLs,
logs or training backups. OpenAI response storage is disabled with `store:false`;
provider processing/retention and billing terms still apply.

Settings, consent, API key and the latest coach result are AES-GCM encrypted through
the existing vault adapter, using a separate Android Keystore alias `rwun.coach`
and private preference file `coach_vault`. Existing sync credentials retain their
original alias/file. Device transfer requires re-entering keys and consent.
The package ID and existing training storage/backup identifiers are unchanged.
Old schedule overrides remain readable; new overrides add optional instructions.
The processed-run token is an additive validated string setting in training storage.

## Evidence and acceptance

`tests/run-ai-coach-tests.sh` covers missing data, history privacy, bounded changes,
malformed/unsupported output, stale responses, atomic update/token persistence,
completed activity protection, workout pace propagation, history/undo and both
provider response formats. `AiCoachUiTest` exercises missing-key/consent states,
large fonts, recreation and notification opt-out with synthetic data.

Current PR validation, acceptance evidence and external limitations are recorded in
[PR #30 validation](PR-30-VALIDATION.md). Earlier host-only results do not substitute
for the current Android release checks.

No API keys or personal running data are used in tests. Live generation for either
provider, Android Keystore persistence across upgrade, actual JobScheduler timing,
notifications and physical-device coaching remain acceptance work. Host tests and
renders do not establish these properties.

Protocol references checked 28 October 2026:

- [OpenAI Responses](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [GPT-5 mini](https://developers.openai.com/api/docs/models/gpt-5-mini)
- [Gemini GenerateContent](https://ai.google.dev/api/generate-content)
- [Gemini 3.8 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash)
- [ChatGPT Plus and separate API billing](https://help.openai.com/en/articles/6950777-what-is-chatgpt-plus)

## Upgrade from Gemma

The old model screen, publisher/download/import controls and inference service are
removed. At process start, background cleanup removes only the retired private
model and inference-cache directories. Existing running/walking history, active
plans, recorded routes and API settings remain intact. This cleanup is idempotent
and avoids symlink targets; tests use synthetic files. See [retired coach](OFFLINE-COACH.md).


## Hybrid plan creation

Race and goal onboarding offers two planning methods: the existing offline deterministic plan, or an AI-personalised full schedule. The AI method first generates the deterministic schedule as a safety envelope, then sends only run summaries and workout descriptions to the configured provider. The provider must explain the pace/distance gap and progression and return exactly one entry for each planned session. Validation preserves dates, IDs, workout types, recovery days, and quality-session distances/instructions; easy-run distances may only be reduced to 70–100% of baseline. Easy workouts may include validated numeric paces no faster than observed recent running; effort-only guidance is mandatory without evidence or during a hold. First-week instructions and the complete workout review show the accepted guidance. Use **Review all workouts** to inspect the complete schedule and instructions before saving. The validated preview and its original data fingerprint survive rotation; new running evidence or changed AI credentials require a fresh preview. Full-plan distances use the existing 0.1 km schedule precision; validation normalises them before preview. New post-run overrides retain the exact accepted distance, while legacy override rounding remains unchanged. Accepted previews are saved unchanged, and the provider, explanation and running evidence are recorded atomically in plan-change history. Plan creation/replacement cannot be undone into an older plan; saved activities remain available. No existing plan is replaced if generation or validation fails.

Settings → AI Connections remains the permanent provider configuration home. During onboarding, missing credentials direct the user there without discarding their draft. User-selectable outage policies are keep current plan, retry automatic review on connection errors, or pause automatic reviews. The existing post-run AI Coach retains its separate conservative easy-run adjustment policy.

**Limitations:** AI full-plan generation is intentionally bounded by the deterministic schedule, not an unconstrained workout generator. It does not create new training days or workout types. Live provider/device testing is required before release. Imported run feedback remains unknown until explicitly reported and is not interpreted as proof of readiness. Goal pace informs the explanation; it cannot authorise faster training targets. Full-plan responses cannot introduce the executable `Coach pace:` marker; easy-session numeric pace targets must be supplied in the separate paceSecondsPerKm field and pass evidence bounds before the app adds that marker. No pace is accepted without observed timed-distance history, on demanding/run-walk workouts or during a hold.


## Coaching progress and release acceptance

Open Training → Training progress & next run for a read-only summary: planned versus actual distance through today, completed versus due workouts, current phase, next uncompleted run, recovery context, and the latest applied plan-change reason. View all plan changes opens the existing audit trail. Only linked or explicitly matched runs count toward plan completion; a match counts once and is excluded from the next-run recommendation. Skipped due workouts remain in the planned denominator so skipping cannot inflate adherence. Recent pain in any saved run, including unrelated runs, suppresses the next-run prescription. Unrelated runs and walks are excluded from completion totals. This is descriptive progress, not a predicted finish time or medical readiness score.

### Release gates (required before marking PR ready)

1. Android assembleDebug, testDebugUnitTest (including TrainingProgressUiTest), and lint must pass.
2. Test both providers with real user-owned credentials; invalid JSON, timeouts, rate limits and credential rotation must leave saved schedules intact.
3. Verify onboarding draft preservation, plan preview, save/reopen, replacement, offline fallback and preservation of completed runs.
4. Complete a planned run, verify metrics count it once, then verify idempotent post-run adaptation and a readable applied-change explanation.
5. Verify recovery holds, recent pain, taper weeks, large fonts, rotation and empty state. Missing recovery data must not imply readiness.
6. Test on a physical Android device, including notification permissions, offline interruptions and app restart. Robolectric does not replace device acceptance.
7. Verify an installable signed release without credentials or personal health data. Keep the PR draft until all gates have evidence.

## Goal-driven flow regressions

Native Robolectric journey tests click through goal entry, generation, rotation,
acceptance and reopening of the exact AI schedule, including executable validated
pace targets and durable goals. A synthetic provider/settings boundary is used;
these are actual Android Views and storage, not a live Gemini/Keystore test.
A second journey asks for a recent-run breakdown without a plan and during a pain
hold, verifies a conversational answer and proves storage is unchanged. Host
regressions also reject malformed answers and unsupported paces and cover goal
backup/restore, automatic-review context, preserved activities and recovery holds.

These bounds allow pace targets to follow improving recorded performance, with
the existing 2% per-review pace limit. They do not promise to reach an arbitrary
goal or pre-authorise faster training based only on the goal. Imported runs without
reported recovery feedback do not justify distance increases. Enable **After each
saved or imported run** in AI Coach settings for automatic adaptation; otherwise
request changes manually. Qualified coaching review and physical-device/live
provider acceptance remain outstanding.
