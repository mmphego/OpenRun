# AI Coach with your own API key

AI Coach uses OpenAI or Google Gemini with your own API key. Local Gemma support
has been removed; no model download or inference runtime is included. Open **AI Connections** from Profile settings to configure and test a provider without creating a plan. The Coach tab and plan onboarding continue to open the coaching screen. Create a goal/race plan, select **OpenAI** or **Gemini**,
enter a model ID and your own API key, optionally set a goal pace in min:sec/km,
read and enable data-sharing consent, then save settings. Defaults are `gpt-5-mini`
and `gemini-3.8-flash`; model access and availability depend on the provider account.
The model ID is editable. API usage is billed by the provider; ChatGPT subscription
billing is separate. No server deployment or downloaded model is needed.

**Generate my AI running plan** sends the recent running summary and upcoming easy
sessions. It returns an explanation of the current pace, goal gap and progression,
plus proposed distances and easy paces. Review the proposal before applying it.
The plan-creation preview also offers **Create plan and personalise with AI**:
it saves the reviewed baseline first, then opens AI Coach. **Ask coach** uses the
same current training context but never applies its returned workout proposals.
Questions are single-turn; a conversation history is not uploaded.

## Automatic post-run reviews

Enable **After each saved or imported run** to allow validated updates without
confirming each one. Newly persisted running history or explicit run reflections schedule one network-constrained,
persisted Android JobScheduler job. Foreground entry and reboot/update also check for
unreviewed history. Runs imported from Health Connect or files use the same storage
listener; walking remains excluded. Unknown-distance/time-only runs cannot supply pace.
Android controls when the job runs; this is not an immediate-delivery guarantee.
Several runs saved before a job executes are reviewed together.

The job updates upcoming easy sessions in the next seven days, stores a processed
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

Race and goal onboarding offers two planning methods: the existing offline deterministic plan, or an AI-personalised full schedule. The AI method first generates the deterministic schedule as a safety envelope, then sends only run summaries and workout descriptions to the configured provider. The provider must return exactly one entry for each planned session. Validation preserves dates, IDs, workout types, recovery days, and quality-session distances/instructions; easy-run distances may only be reduced to 70–100% of baseline. Use **Review all workouts** to inspect the complete schedule and instructions before saving. The validated preview and its original data fingerprint survive rotation; new running evidence or changed AI credentials require a fresh preview. Full-plan distances use the existing 0.1 km schedule precision; validation normalises them before preview. New post-run overrides retain the exact accepted distance, while legacy override rounding remains unchanged. Accepted previews are saved unchanged, and the provider, explanation and running evidence are recorded atomically in plan-change history. Plan creation/replacement cannot be undone into an older plan; saved activities remain available. No existing plan is replaced if generation or validation fails.

Settings → AI Connections remains the permanent provider configuration home. During onboarding, missing credentials direct the user there without discarding their draft. User-selectable outage policies are keep current plan, retry automatic review on connection errors, or pause automatic reviews. The existing post-run AI Coach retains its separate conservative easy-run adjustment policy.

**Limitations:** AI full-plan generation is intentionally bounded by the deterministic schedule, not an unconstrained workout generator. It does not create new training days or workout types. Live provider/device testing is required before release. Imported run feedback remains unknown until explicitly reported and is not interpreted as proof of readiness. Goal pace informs the explanation; it cannot authorise faster training targets. Full-plan responses cannot introduce the executable `Coach pace:` marker; numeric AI pace targets must pass the separate post-run validator.


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
