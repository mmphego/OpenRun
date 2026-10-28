# Personal voice coach

Open **Coach → Beginner sessions & personal progress** for next-run lessons,
reviewed beginner sessions and personal progress. Voice preferences are also in
Profile. Core decisions and speech run on the phone without an account, network
request or model. Install an offline Android TTS voice for offline playback.
Optional [AI Coach](AI-COACH.md) uses your own OpenAI or Gemini key for reviewed running-plan personalisation and separately opted-in automatic updates. Voice coaching and base planning continue offline without AI.

## Prepare → practise → reflect → next run

Planned workout briefings now include a lesson derived from personal reflections.
Pain/holds, high effort or fatigue, and returning after at least 14 days take priority
over encouragement. A main-set slowdown can suggest practising a gentler start,
without assuming that terrain, alcohol, illness or fitness caused it.

Beginner sessions are a separate, optional timed programme. They do not replace,
reschedule or complete the distance-based plan. Stage 1 has six one-minute running
sections, 90-second walking recoveries between them and five minutes each of warm-up
and cool-down. Seventeen repeatable stages lead to a prescribed 30-minute continuous
running section. Stages have no calendar deadline, distance or finish-time promise.
Leave rest days between beginner runs and repeat until manageable. This is original
software guidance, not a clinically validated training or injury-recovery programme.
Qualified coaching review remains outstanding.

Outdoor and treadmill modes use the same beginner definition. Indoor distance stays
optional; time-only running reflections are included without inventing GPS or distance.
Indoor completion is self-reported. GPS does not prove continuous running. Existing
checkpoints retain outdoor stage, workout type and completion receipts after interruption.

When private recovery check-ins are enabled, their existing review replaces the quick
coaching check-in, without copying private answers into coaching backups or AI context.
Returning from that review retains the selected beginner workout. Otherwise,
the start review includes fatigue and pain. Saving pain saves a recovery hold with the
check-in. Activity, reflection and any running pain hold are saved in one preferences
commit. A new hold pauses an active outdoor recording; starting/resuming remains blocked.
Only the existing explicit recovery-review flow can clear a hold.

Run reviews ask optional **manageable?** and **voice cues helpful?** questions. Unanswered
feedback stays unknown. **Your running progress → Reflect on a saved run** lets users
add their own feedback to previous/imported runs without changing source, distance,
duration or route. Imported runs never invent beginner-stage completion evidence.
Editing feedback cannot clear an existing recovery hold.

Personal progress includes reviewed running days, manageable sessions, comparable
reported effort/distance, and the longest prescribed section in completed beginner
sessions. Walking totals stay separate. Comparable runs need the same workout type
and distance within 10%, or a completed session at the same beginner stage, on an
earlier date within 90 days. Different routes, terrain and weather are not normalised;
these comparisons do not establish fitness gains.

Simple easy/free runs can retain first/latest complete main-set kilometre pace and split
variation. Warm-up, cool-down and tempo/interval sessions are excluded. Gapped/delayed
GPS suppresses that evidence. More than 10% slower later pacing can suggest a gentler
start, with an explicit statement that the cause is unknown.

## Reviewed progression and safe undo

A next-stage preview requires three completed, explicitly manageable sessions at the
current stage on separate dates in the last 28 days, each with effort ≤5/10, fatigue
≤3/5 and no pain. Same-day repeats count once. Later demanding sessions prevent
cherry-picking old successes. A current high-fatigue check-in, recovery hold, pending
recording or return gap blocks progression. Repeat without changing anything, or
review an easier stage while not held. Thresholds are transparent original rules,
not a readiness assessment. Missed mileage is never added as debt.

Only applied beginner-stage changes enter history. Events include timestamp, reason,
supporting evidence, before/after definitions and an evidence fingerprint. Apply checks
that fingerprint again. Undo requires a consequence review, restores only the newest
eligible stage change and adds a new event. New training, feedback, settings, plan changes,
pending recordings and holds reject unsafe undo. Completed activities remain saved;
undo never clears a hold. This history concerns beginner stages; the separate plan-history
feature owns distance-plan mutations.

## Voice controls and playback

- Quiet: workout transitions and explicitly enabled HR reminders.
- Standard: adds effort/talk checks and optional kilometre updates.
- Detailed: adds longer relaxation/effort reminders. Checks are configurable from
  3–15 minutes, default five. Profile's audio switch disables all speech.
- The outdoor recorder offers **Repeat current instruction**, using the current phase
  rather than replaying old pace advice. Existing pace alerts remain optional.
- Cues carry category, priority, phase and expiry. A four-item mailbox coalesces duplicate
  keys, discards old phases and expires advice before playback. Phase changes interrupt
  obsolete utterances. Pause, audio loss and shutdown clear pending speech; audio-focus
  denial creates no backlog. Current instructions remain visible without speech.
- Live HR reminders are disabled by default. A user may choose a 100–220 bpm alert
  threshold; the app does not estimate personal zones/max HR. Easy-running reminders
  need 30 seconds of observations with HR no older than ten seconds, with at least two
  minutes between reminders. Stale data, pauses and observation gaps reset that window.
  This is an effort reminder, not an emergency monitor or diagnosis.
- **Health Connect history is not a live HR feed.** Live Bluetooth needs compatible
  hardware and permissions. Garmin account/workout sync remains unimplemented.

## Persistence and acceptance

Validated, additive `v103.coaching` JSON uses schema version 1. Package/storage keys,
completed activity IDs and Room schemas remain compatible. Explicit portable backups
include coaching settings, reflections and stage events. Reflections resolve against
existing activities: deletion excludes evidence, restoration can recover it, and corrected
totals invalidate the old completion/pacing receipt while preserving subjective answers
and the recovery hold. No cached fitness claim survives a correction.

`tests/run-voice-coach-tests.sh` covers rules and the existing storage adapter.
`tests/native/VoiceCoachTest.java` exercises real Android Views and framework storage
with synthetic fixtures. Host renders are not device screenshots or proof of playback.
Physical acceptance is documented in `tests/s23/CHECKLIST.md`: screen-off operation,
offline TTS, speaker/headset/music/call routing, stale HR, interruptions and restoration.

References informing effort-based beginner guidance:
- [NHS Couch to 5K](https://www.nhs.uk/better-health/get-active/get-running-with-couch-to-5k/couch-to-5k-running-plan/)
- [CDC relative intensity and talk test](https://www.cdc.gov/physical-activity-basics/measuring/index.html)

No third-party app code, assets or training schedule was copied.
