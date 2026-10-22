# V1 status

OpenRun 1.0 adds mixed-activity calendar planning, onboarding preview consistency, widget/notification conveniences, generated coaching, private sharing and beta tools. It remains a beta, not Runna parity. Current limitations are authoritative in [REMAINING-GAPS.md](REMAINING-GAPS.md). Earlier comparisons below describe their original release scope.

# OpenRun functional parity backlog

The goal is an independent running coach with comparable useful workflows, not copied Runna code, assets or proprietary plan content. No percentage-complete claim is made: feature labels are not evidence of equivalent coaching quality or reliability.

| Capability | 0.2 baseline | 0.3 work | Remaining acceptance gate |
| --- | --- | --- | --- |
| Pace personalisation | No targets | Recent-race benchmark, transparent pace estimates, editable profile | Qualified coaching review; validate against varied runners |
| Structured workouts | Easy run plus prose | Step-by-step intervals, tempo, recoveries, warm-up/cool-down | Wider periodisation, race-specific blocks, hills and track mode |
| Live coaching | Milestone speech | Rolling GPS pace, step targets, sustained/throttled pace alerts, split cues | Physical audio/GPS accuracy and battery tests |
| Adaptive training | Single-run workload reduction | Multi-session pace review with user approval, conservative load adjustments | Validate performance model, scheduling interactions and recovery assumptions |
| Calendar | Fixed schedule | Skip/unskip and reschedule within plan, collision checks | Complex holiday/race scheduling and long-run preference wizard |
| Data resilience | Local history, GPX | Full portable backup/restore of profile, plan, history and routes | Cloud account, encrypted sync and conflict resolution |
| Garmin | GPX/TCX and BLE HR | Retained interoperability | Official Garmin API approval for automatic activity/workout sync; physical model tests |
| Strength/mobility | Absent | 0.4: two original bodyweight routines, completion checklists, persistent sessions and backup | Coach review, equipment-aware progressions, expanded library and instructional video |
| Health Connect | Absent | 0.6: official Android 14+ read permissions, manual 30-day import and provider record-ID deduplication | Actual platform/device tests; background sync, writes and older Android provider |
| UX/accessibility | Basic native UI | Clearer workout steps, target ranges and run states | Full screen review, accessibility and real-user feedback |
| Reliability | Build and host tests | 32 Android instrumentation assertions and ADB UI checks on API 26 | Physical watch/phone tests, broader Android matrix and release monitoring |

Completed implementation work and executed validation are recorded separately in VALIDATION.md. External Garmin access cannot be substituted by a fake Connect button or an untested integration stub.
