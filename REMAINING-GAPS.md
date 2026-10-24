# OpenRun 1.4.4 — remaining gaps

- No physical S23/adb target was connected. The 59 host tests and inspected native renders do not prove hardware sensor sensitivity, audible screen-off delivery, battery life, Bluetooth/call handling or Samsung recovery. Use tests/s23/CHECKLIST.md.
- Garmin automatic two-way account/workout sync still requires official partner approval, credentials/documentation and an activated HTTPS service. Activity-file import and compatible Bluetooth heart-rate sensors work separately; live provider/device compatibility is unverified here.
- Health Connect uses Android 14+ framework APIs. New possible duplicate imports are held for review, but existing saved duplicates are not automatically merged; provider edits/deletions and route import remain incomplete. Original outdoor and indoor walks can now be explicitly exported. Real permission/import/export behavior needs device testing.
- Walking-pad distance is estimated from a carried phone. The starter 0.70 m step length is an explicit assumption. Personal calibration and repeated speed/placement checks are needed. A phone on the desk is not supported; FTMS machine control and sitting detection remain absent.
- Indoor timed audio now runs in the foreground session with explicit Profile preference, bounded wake lock and focus handling. Force-stop/reboot can still interrupt it. Distance steps require manual confirmation. Audio is bounded to 12 hours, and battery/audio acceptance is pending.
- Reminder scheduling is inexact and Android-controlled. Pending recordings now get a review prompt under the configured policy/window; no session is automatically discarded or completed.
- The race catalogue remains a small starter dataset. Comprehensive authorised SA race coverage and backend deployment are not activated; manual race entry is available. Supported plans remain road-running 5–42.2 km and 4–24 weeks, without ultra/trail/multi-race qualification-specific programming.
- Optional Gemma needs verified model import and physical S23 inference/JNI/memory/latency/quality validation. Optional cloud Gemini/Strava/account services need hosting and credentials. Rules generate schedules; AI explanations and original coaching material still need qualified review.
- Production signing, final device/release acceptance and production service/policy setup remain outstanding. This development APK is not certified market-ready.
- Running and walking remain the only active activity workspaces. Existing archived gym/cycling data is retained for backup compatibility.

Upgrade without uninstalling. Save active sessions first. Export a local backup before restore experiments. New backup fields are not designed for older app versions.
