# Runna APK inspection and OpenRun implementation boundary

Input: Runna v8.57.0.apk, package `com.runbuddy.prod`, version 8.57.0 (2080).
SHA-256: `97d70dd81340271a24573145c7db7c60f1bca5c5bf93c487fd2632da294d72eb`.

## Methods and limitations

REA 6.5.0 was installed from the official morluto/rea project. Its `inspect-android-package` command refused to launch JADX because the sandbox's procps ownership check fails with `fatal library error, lookup self`. Its original failure evidence is retained in `evidence/rea-package.txt`; no successful REA APK decompilation is claimed.

Fallback inspection used the pinned jadx-headless-mcp 0.7.1 engine JAR (SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`) directly through the JADX Java API. It decoded the manifest and enumerated 29,154 top-level classes (55,671 loaded classes including nested classes). Hermes string-table parsing identified compiled bytecode version 96, 507 route identifiers, and the endpoint references in `evidence/apk-evidence.json`. String-table offsets/indices distinguish complete strings from misleading concatenated results produced by generic `strings` extraction.

Runna's authenticated product was not exercised. Static references do not prove live endpoint behavior, active feature availability, exact screen hierarchy, training algorithms or server-side data contracts. Routes include experimentation and internal/developer paths; 507 identifiers does not mean 507 user-visible screens.

## Backend and integration references

| APK observation | Likely responsibility / confidence | Independent OpenRun implementation |
| --- | --- | --- |
| Cognito eu-west-1 endpoint; Amplify/AppSync client references | Identity and AWS-backed data access; exact active schema unknown | Offline local profile and private storage; cloud accounts and API backend unimplemented |
| `coaching.platform.runna.com/cci` and staging endpoint | Remote coaching integration; endpoint reference confirmed, server logic unknown | Original local plan engine, structured workout state machine, workload/pace review rules |
| RevenueCat endpoint and native classes | Billing/subscriptions integration | Independent app has no billing or subscription gate |
| Runna CDN references | Assets and content delivery | Original UI, icon, cues and strength routines; no copied assets/content |
| Mixpanel, Statsig and Sentry references | Analytics, feature configuration and diagnostics | No telemetry or network dependency in OpenRun |
| Garmin route identifiers, external-integration references | Wearable onboarding/auth flow; official partner contracts not recoverable | Manual GPX/TCX import and compatible BLE HR; automatic Connect sync unavailable |
| Health Connect permissions, Bluetooth, foreground location, GPS | Android/device integrations declared in manifest | Foreground GPS recorder, local route sketch and BLE HR; Health Connect and ANT+ unimplemented |

No Runna endpoints were called, credentials recovered or reused, paid plans extracted, paywall patched, or original APK modified. Android signing verification of the supplied Runna APK was not performed. The dependency table distinguishes package evidence from inferred responsibilities.

## Navigation coverage

The APK declares routes for Today, Plan, Calendar, Activities, Progress, Support, strength, recording, pace insights, injury/recovery, holidays, races and Garmin authorization, among others. OpenRun provides a simplified Today / Plan / Run / History / Settings navigation with a separate strength workflow. Functional and visual parity with the full Runna product is incomplete.

## Official Garmin blocker

Official Training API documentation describes publishing workouts and training plans to Garmin Connect. Official Activity API documentation describes receiving completed wearable activities. Both require approved program access; Garmin describes the Connect Developer Program as being for business use. Partner credentials/documentation, OAuth 2.0 user consent, and a deployed HTTPS integration service are not available here. No endpoints or OAuth scopes were guessed, and no successful automatic sync is simulated. The public documentation does not establish that OpenRun has program approval.

References:
- https://github.com/morluto/rea
- https://developer.garmin.com/gc-developer-program/training-api/
- https://developer.garmin.com/gc-developer-program/activity-api/
- https://developer.garmin.com/gc-developer-program/program-faq/
