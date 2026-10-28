# AI Coach with your own API key

AI Coach is a cloud alternative to installing Gemma. Open **AI Coach** from the plan
start screen or Coach tab. Create a goal/race plan, select **OpenAI** or **Gemini**,
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
confirming each one. Newly persisted running history schedules one network-constrained,
persisted Android JobScheduler job. Foreground entry and reboot/update also check for
unreviewed history. Runs imported from Health Connect or files use the same storage
listener; walking remains excluded. Unknown-distance/time-only runs cannot supply pace.
Android controls when the job runs; this is not an immediate-delivery guarantee.
Several runs saved before a job executes are reviewed together.

The job updates upcoming easy sessions in the next seven days, stores a processed
history token in the same transaction as the plan changes, and optionally notifies
you. Changes to history/plan while the request is running invalidate its proposal.
A stopped job cannot apply its result; pending work is retried by Android. Connection
and timeout failures request backoff; authentication, quota or invalid outputs need
attention in AI Coach. Failed calls preserve workouts. The screen retains the latest
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
  cannot establish readiness for an increase.
- Observed pace is total recorded seconds divided by distance over recent runs,
  not a race benchmark or readiness estimate. No numeric AI pace is accepted without
  observed pace, or faster than that observation. A previous AI pace cannot become
  more than 2% faster in one revision. The goal pace informs the explanation, never
  overrides these limits. Easy workout steps use the accepted pace, with an 8% slower
  range; effort/recovery guidance takes priority.
- Invalid, incomplete or oversized provider responses fail closed. No partial
  proposal is applied. An empty proposal can mean the current plan is appropriate.

## Data and credentials

Only up to 40 runs from the last 28 days are included: dates, distance, duration,
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

Local validation on 28 October 2026:

- All 14 host regression suites passed, including 30 new AI Coach assertions.
- All 14 service tests and 20 device-QA utility tests passed.
- `git diff --check` passed.
- Android lint, Gradle unit tests, APK builds and native UI renders could not run:
  the Gradle distribution download failed with `Network is unreachable`. No SDK or
  emulator was available in this workspace. The added native UI tests are unexecuted.
- Remote CI, live provider calls and physical-device acceptance are separate from
  these local validation results.

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
