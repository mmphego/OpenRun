# Device acceptance matrix — not executed here

Use an emulator for navigation and permissions, and physical devices for GPS, power and audio. Host tests and APK compilation are not evidence of physical reliability.

| Flow | Required evidence |
|---|---|
| Update 0.4–0.9 → 1.0 | Plan/history/holds/routes retained; new options default safely |
| Fresh onboarding | Starter/returning/regular paths; back/rotation; preview exactly matches saved first week |
| Offline run | GPS distance/time and route; spoken phase changes; save and reopen |
| Screen off 45–90 minutes | Continuity against reference; battery delta; wake-lock and foreground notification state |
| Music and telephone call | Cue requests audio focus; interrupts/denials do not overlap call; music resumes after speech |
| Notification | Correct paused/running label; pause/resume; View / finish opens Run; no accidental discard |
| Process removal | Recorded checkpoint recovered; no duplicate activity or joined route across interruption |
| Permissions revoked | Precise location, notifications, Bluetooth and Health Connect fail with useful recovery actions |
| Widget | Pin/manual add; next workout and hold; launcher resize; updates after saving a run |
| Mixed activities | Add/edit/delete, day boundaries, future completion rejection, warnings without silent plan changes |
| Share card | Preview; optional date; no route/location/HR; SAF cancellation/rotation; slow/cloud provider remains responsive |
| Accessibility | TalkBack labels/focus, 48dp actions, 1.0/1.3/2.0 fonts, display scaling, keyboard/insets on API 36 |
| Backup | Mixed activities round trip; metrics and consent excluded; corrupted input rejected |
| Beta privacy | Off by default; no automatic metrics traffic; report preview; disable erases counts |

The app does not automatically resume recording after process death or a force-stop: users review and recover the checkpoint. Up to the latest five-second checkpoint interval may be missing; Android and storage failures can cause additional loss. This is an implementation constraint, not a measured recovery guarantee.

Use adb scripts/capture-device-evidence.sh to collect app-scoped evidence. Do not commit participant logs, routes, screenshots or health data into source control.
