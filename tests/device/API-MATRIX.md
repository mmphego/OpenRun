# Android device acceptance matrix

Host tests and emulator checks establish deterministic behavior. They do not
prove GPS, background execution, audio focus, Bluetooth, Health Connect or OEM
battery behavior. Complete this matrix before describing a build as a production
candidate.

Use a device with an authorized ADB connection, set `ANDROID_SERIAL`, install
with `adb install -r`, and never clear OpenRun data to force an upgrade. Capture
evidence in a private directory; routes, diagnostics and screenshots may contain
personal information.

| API / device class | Required flows | Evidence |
| --- | --- | --- |
| API 26–29 physical device | Fresh install, offline plan creation, outdoor GPS start/pause/resume/save, interrupted-recording recovery, notification actions, large-font navigation | App version, screenshots, route/result review, `scripts/capture-device-evidence.sh` output |
| API 30–33 physical device | Above, plus screen-off outdoor recording, Bluetooth HR disconnect/reconnect, audio focus interruption and recovery | Timed session notes, recording diagnostics before/after lock, accepted-route count |
| API 34–35 Health Connect-capable device | Above, plus separate Health Connect read/write consent, duplicate review, original-activity export, denied/revoked permission behavior | Permission screens, import/export outcome, no-route Health Connect detail state |
| API 36 representative device | Above, plus notification permission denied/allowed, edge-to-edge/large-font navigation, current target-SDK behavior | Screenshots, accessibility review, log/evidence bundle |

## Required upgrade scenario

For every candidate that can be installed over an existing OpenRun build:

1. Seed a plan, a completed activity with a synthetic route, a paused/recovered
   recording checkpoint, preferences and any supported health/import metadata.
2. Install the candidate with `adb install -r`; do not uninstall, clear data,
   bypass a signature error or downgrade.
3. Confirm the app opens, the stored plan and history remain readable, the route
   still renders, the checkpoint remains recoverable, and a new recording can
   start after review/discard.
4. Record the installed package version and signing identity. A signature mismatch
   is a release blocker, not a reason to erase the existing app.

## Acceptance discipline

- Mark an item passed only after observing it on the named device/API.
- Keep hardware, Android version, OEM build, permission decisions and test date
  with the evidence.
- A skipped or unavailable device remains pending; do not infer a result from an
  emulator or another API level.
- For the detailed S23 locked-screen walking protocol, use
  [CHECKLIST.md](../s23/CHECKLIST.md).
