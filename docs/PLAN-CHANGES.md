# Plan-change history and safe undo

Open **Plan → Plan changes** (also available in the training schedule) or **Coach → Plan changes**.

## What is recorded

Only committed changes are recorded: adaptations from workout feedback/recent running,
reviewed weekly adjustments, workout moves and training setting changes. Each event has a
stable ID, wall-clock timestamp, plain-language reason, supporting evidence summary and
actual affected workout targets before/after (date, km, title, instructions and skipped state).
Pain holds and explicit recovery reviews are visible too; neither is undoable.
A clock correction can change displayed timestamps; stored event order determines the newest event.

Previews and rejected suggestions perform no writes. The preview uses the existing native
planner against a read-only candidate. Applied history records the resulting rounded/capped
targets, not merely the requested values. Past/completed planned targets are retained when
a new adjustment would recalculate them. Actual saved activities, routes and walking totals
are never rewritten by plan undo.

History starts with this version. Earlier installations and backups without history show an
honest empty state. Creating/restarting/replacing a plan keeps existing events, marks earlier
plan events as such and invalidates their undo; it does not fabricate earlier adaptations.

## Undo policy

1. Review the newest event's inverse workout/date/distance consequences.
2. Choose **Apply undo**. Choosing **Keep change** leaves everything unchanged.
3. A new event links to the original using `undoOf`; neither event is erased.

Undo is deliberately one level: only the newest recorded eligible event can be reversed.
It cannot jump over newer work, undo an undo, or reverse a recovery event. It is not a
general version restore or redo mechanism.

Eligibility requires the same plan, current plan revision and stored after-state, no pending
recording/hold, no affected past/completed/matched workout, and no schedule collision.
The inverse is recomputed from current data and must exactly match the recorded consequences,
including after a backup import. Any changed state while the confirmation is open rejects it.
A fresh review can still undo while retaining unrelated new activities or reminder settings.

**Undo never clears a pain hold.** Use **Profile → Review and resume training** when appropriate.
That review also checks stale state. The app does not assess injury readiness.

## Persistence and compatibility

The plan mutation, new revision and event are staged and written in one synchronous
SharedPreferences editor commit under `RunStore.LOCK`. The existing `rwun` preference file,
Android package `app.rwun`, cloud encryption and portable `app: Rwun / schema: 1` backup
discriminator are retained. No Room race schema change or destructive migration is needed.

Additive text preference fields: `planChanges` (JSON event array), `planRevision`,
`planEpoch` (invalidates in-flight previews on restore) and `protectedTargets`.
Backups validate event/snapshot shapes and bounds before restore; legacy backups omit them.
Restoring history does not invent a new event. A fresh eligible undo works after restore,
while an old open confirmation is rejected. Older app versions are not expected to accept
the new fields; export before experimenting with downgrades.

There is a 10,000-event safety bound, with no automatic truncation. At the bound further
recorded mutations fail before writing; export a backup and contact the maintainer rather
than deleting audit history silently. Large-history pagination/storage scaling is future work.

## Validation

`python3 scripts/run-host-tests.py` includes `run-plan-change-tests.sh`: before/after
accuracy, zero-write previews, a single mutation/event commit, stale state, eligible undo,
completed and matched workouts, holds, replacement plans, pending sessions, backup round
trips/legacy/corrupt data and preservation of completed targets/newer unrelated work.

`./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
includes native Android UI/backup tests and host renders. Run the documented service/device
utility checks as well. Host tests and APK compilation do not establish device installation,
launch, Samsung persistence or Health Connect behavior. Device acceptance remains required.

## Reference and provenance

openGym's [visible progression and user-approved coach changes](https://github.com/DuarteSantos8/openGym)
informed the capability brief. This implementation independently extends OpenRun's Java,
Android Views and storage architecture. No openGym implementation or assets were copied.
Third-party notices are preserved. [Project licensing remains unresolved](../LICENSING.md).
