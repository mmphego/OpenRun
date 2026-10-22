# OpenRun 1.4.3 — walking-pad recording fix

The reported 2 km walk was on a walking pad with the S23 in a pocket. The screenshot was the outdoor GPS recorder. A stationary walking pad does not produce the changing phone coordinates needed by that mode. The delayed-GPS fix in 1.4.2 remains useful for outdoor sessions but does not resolve indoor belt distance. The app's mode selection and indoor defaults were inadequate.

## Changed behavior

- Today, first-time setup and walking history's generic Start walk now ask **Walking pad · phone steps** or **Outdoors · GPS**. Walking-pad selection opens the indoor recorder without a location permission request. Explicit outdoor actions remain clearly labelled.
- Indoor phone steps are selected by default when Android exposes a step counter. Start requests Physical activity permission. A denial leaves the user on the start screen with an explanation; recording time only requires explicitly disabling phone steps. Notification permission is separate.
- Walking distance starts with a **clearly disclosed 0.70 m per detected step** assumption, unless a personal walking calibration already exists. This is a convenience estimate, not a measured personal stride or a guaranteed distance. Indoor running still requires its separate calibration for a step-distance estimate.
- Active indoor recording shows detected steps, approximate distance (prefixed ~), duration, source and calibration status. Before distance is available it displays a dash, not a misleading GPS 0.00. The UI and ongoing notification warn after 30 seconds with no steps; notification refresh depends on the process and Android scheduling. No spoken warning is claimed.
- Step status changes to Steps detected only when a real counter delta has been accepted. Counts before the session, paused movement, duplicates and future events are excluded. Raw Android counter totals are re-baselined on start/resume; some boundary steps can be lost. Restarts retain persisted counts and mark estimates partial. No missing movement is invented.
- Finish and review can use the step estimate, a known machine distance, or time only. Machine distance takes precedence. A matching machine distance and at least 100 detected steps can calibrate future sessions when the sample is not partial. Walking history stays separate from running mileage.
- Returning to Move with a pending indoor session offers Return to indoor session instead of an unrelated GPS dashboard. Compact typography and the existing running plan are retained.

## Install and verify on the S23

Save any current walk before updating. Install 1.4.3 / version code 21 over the existing development-signed app **without uninstalling**. Use **Today → Start walk → Walking pad · phone steps → Start indoor session**, allow Physical activity permission, and carry the phone in the pocket. Verify an increasing step count over 100–200 steps before a longer walk. Counter updates may arrive with a delay. Compare a longer session with the pad's display, enter that known distance at finish, and select calibration for future walks. Estimates vary with speed and phone placement.

For the previously saved outdoor-mode walk, **Progress → Walks → Correct walking distance** can store the known 2.0 km while retaining its recorded time and marking the correction manual. The app cannot reconstruct steps or a GPS route it did not record. An unsaved session must first be saved using the retained review flow.

## Validation — 21 October 2026

- **29 Robolectric host tests pass**: 5 existing planning/UI, 10 outdoor GPS, and **14 indoor recorder/flow tests**. Controlled SensorEvent callbacks test approximately 2 km without any location grant, default step selection, actual start-button flow, indoor/outdoor choice, denial without silent time-only start, existing calibration, no-step UI/notification warning, old/duplicate/future rejection, pause/resume, restart partial status, estimated walking save, machine-distance override and future calibration, revoked permission and return-to-session UI.
- **63 indoor/state assertions**, **49 storage/backup**, **10 navigation/error**, **68 recording-start**, **29 feature** and **96 workout/coaching** assertions pass. Running plan invariants cover **84 configurations**.
- Standalone API 36 APK builds using the retained development key. v2/v3 signature and 16 KiB alignment pass. Instrumentation APK compilation is supplied separately; it was not run on a device.
- Native Android host renders of indoor setup, a controlled 2 km session and the no-steps warning were visually inspected. They are simulations, not S23 screenshots or hardware accuracy evidence.
- Test Android Apps inventory: `adb devices -l` found **no connected Android target**. Physical S23 step-counter sensitivity, Samsung screen-off delivery, estimation error and battery impact remain **unverified**. The source and S23 test kit contain a non-destructive acceptance checklist.

APK SHA-256: `f1b6cc3e0d4e9a537f885b7239d1dea2d38010fc7679deb4d53b0829ccc22cd7`

## Android contracts checked

- Motion sensors and step-counter permission/timing: https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
- Health foreground-service prerequisites: https://developer.android.com/develop/background-work/services/fgs/service-types#health
