# OpenRun 0.9 coaching content

The coaching library contains 18 original generated guides, available offline. Profile → Coaching library opens category/search browsing. Each article has a focus, practical suggestion and reflection prompt. Articles drawing on general guidance offer a browser link; other articles are explicitly OpenRun practice suggestions. Viewing guides sends no training data to a service. Source links require an internet browser.

Plan → workout → Coach briefing explains the session's purpose and preparation. Listen uses the installed text-to-speech voice and existing spoken-cue preference. It is unavailable during an active recording. No generated speech recording or cloned coach voice is bundled.

Saving a run with effort/fatigue/pain produces a reflection in the existing review dialog. The original adjustment logic remains responsible for plan changes; the coaching module does not mutate preferences, clear holds or raise mileage. Recorded duration is labeled recorded time because recording and imports can have different pause semantics. No fitness gain or injury diagnosis is inferred. Imported placeholder feedback must never be passed as reported feedback; the domain API has an explicit flag for that distinction.

## Background references checked 18 October 2026

- NHS Couch to 5K: https://www.nhs.uk/better-health/get-active/get-running-with-couch-to-5k/couch-to-5k-running-plan/ — gentle transitions, beginner rest days, basic preparation.
- CDC activity intensity: https://www.cdc.gov/physical-activity-basics/measuring/index.html — relative effort and talk test; not personalised zones.
- NHS running injuries: https://www.nhs.uk/live-well/exercise/knee-pain-and-other-running-injuries/ — stop painful running and seek qualified assessment where appropriate.

These references do not validate OpenRun's interval prescriptions, adaptation thresholds, strength programming or return-to-running algorithm. General coaching is not professionally reviewed. Qualified coach review, demonstrations, live support and personalised clinical/nutrition assessment remain absent.

## Validation

27 coaching domain assertions cover session routing, hold precedence, absent benchmarks, pain/fatigue feedback, unreported import feedback, no single-session fitness inference, source allowlist, search/categories and invalid inputs. Android compilation and signature verification are performed separately. TTS device playback, screen layout, accessibility and real-device navigation are not verified in this environment.
