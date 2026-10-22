# OpenRun 1.0.2 — free-run start and readiness

- Start free run on Today, empty Progress and the recorder now uses the same start flow, instead of navigation-only buttons on some screens.
- Precise location and device GPS are checked before requesting recording. Disabled location shows an explicit not-started explanation and opens the public Android Location settings screen. If the user enables it and returns, the requested run continues through the remaining checks. Returning without enabling it does not start a recording or repeatedly reopen Settings.
- Denied precise-location permission offers app permission settings. Settings/permission returns recheck readiness and recovery holds before starting. Notification denial is allowed with a clear warning about hidden controls.
- Startup has a request ID and requires actual recorder activity for confirmation. Correlated failures produce an error dialog; stale failures do not contaminate new requests. A ten-second confirmation timeout reports unconfirmed status rather than false success.
- The recorder displays Starting while awaiting confirmation, then active controls. Inactive dashboards distinguish disabled Location, missing precise permission and failed starts from Ready.
- Existing pain holds, pending recordings and active runs remain protected. Start never clears a checkpoint, creates fake distance, or bypasses Android location permissions. The timer begins when recording starts; GPS distance needs valid movement. The existing five-minute warm-up remains part of free-run coaching.
- The previous goal-selection auto-advance change is retained.

Version code 12, version name 1.0.2, retained development signing certificate. Install over 0.4–1.0.1 without uninstalling. No storage migration.

Validation: 68 start-gate/confirmation assertions and 10 navigation/error assertions passed. Clean API 36 build and APK v2/v3 signature checks passed. No Android device/emulator is available here; real Samsung settings return, permission prompts, foreground service startup and GPS recording remain unverified.

Device acceptance: tap Start with GPS off → cancel (no recording); retry → enable GPS → return (confirm active recorder); repeat with permission denial/permanent denial; notification denial; active run; pain hold; existing checkpoint; rotate while starting; disable GPS during permissions; remove/reopen the app while starting. Verify no duplicate run, no false recording state and no overwritten checkpoint.
