# OpenRun 1.4.2 · walking distance recording

## Fixed

The old recorder discarded every location delivered more than 10 seconds after its measurement. A valid delayed sequence recorded during an active walk could therefore produce zero distance. A regression test against the 1.4.1 recorder reproduced 100 measured metres as 0.0 km. This confirms a code defect; the user's exact walk environment/device delivery pattern is not confirmed from the screenshot alone.

The recorder now processes timestamp-ordered batches, accepts samples measured within the current recording segment, and rejects pre-start/pre-resume, duplicate, out-of-order and future samples. Recent provider sample time, rather than callback arrival, determines signal freshness and pace. Existing accuracy, speed and gap filters remain. Long missing intervals are not bridged with an invented route. Delayed samples do not inherit a current heart-rate reading, and delayed recordings do not claim verified quality-workout metrics.

Android 12+ requests high-accuracy GPS with zero requested batching delay. Older Android retains the existing location request. This requests timely delivery; it does not bypass Android or Samsung restrictions.

The dashboard and notification warn if distance is not advancing. A spoken warning follows existing audio settings. GPS recording details show sample counts, delays, gaps and last sample age without logging coordinates remotely. Outdoor GPS and walking-pad/indoor distance sources are explicitly distinguished. Open-ended workout copy says “Open distance”.

## Recover a known distance

For an existing saved outdoor walk: **Progress → Walks → Correct walking distance → enter 2.0 → Save distance**. The existing recorded time, feedback and real route points remain; the distance is labelled **Manual walk distance**, with original source retained. Running mileage and training holds are unchanged.

When finishing/recovering a walk, an optional known-distance field offers the same manual provenance. No missing GPS route or unrecorded duration is invented. A new zero-distance walk is saved as time only. An indoor walking-pad session still needs carried-phone step counts with calibration, or the machine's distance; outdoor GPS cannot measure belt movement.

Install version 1.4.2 / code 20 over the retained development-signed app. Finish/save an active session before updating when possible. Do not uninstall to update.

## Validation (21 October 2026)

- Reproduced the old delayed-sample failure against the old recorder class before the fix.
- **15 Robolectric host tests pass**: 5 existing planning/UI tests and 10 actual recorder tests. GPS cases include a normal 2 km walk, a 2 km walk delivered in delayed batches, delayed 100 m, stale/pre-start/future fixes, duplicates/out-of-order delivery, pause/resume, poor accuracy, teleport rejection, long missing intervals, no-distance warnings and saving a known 2 km without inventing a route. Some tests combine related checks; the 10 recorder test methods are the authoritative count.
- **34 walking/migration assertions**, including persisted manual correction, unchanged time/source provenance and no invented route/running mileage; invalid corrections leave data unchanged.
- **49 storage/backup**, **10 navigation/error**, **68 recording-start**, **29 feature** and **96 workout/coaching** assertions pass; plan invariants cover **84 configurations**.
- API 36 standalone APK builds, alignment and retained certificate checks pass. Instrumentation APK compiles; it was not executed on a device.
- Native Android host renders of the distance warning and recorded 2 km were inspected. They use controlled test locations and are not physical GPS evidence.

Test Android Apps target inventory found no connected adb device. **No emulator/S23 field walk, Samsung screen-off behavior or hardware GPS accuracy pass is claimed.** A short outdoor field check remains required. The 10-second discard bug may explain the screenshot, but indoor GPS mode, missing samples or other device-specific causes cannot be ruled out without the new diagnostics.

Upstream contracts: https://developer.android.com/reference/android/location/LocationListener and https://developer.android.com/reference/android/location/LocationRequest.Builder .
