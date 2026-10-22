# OpenRun 1.2.0 — running and walking

This release narrows OpenRun to running and walking. Gym, strength routines and cycling workspaces, recording, imports, planning and reminder entry points are removed. Their existing stored records remain archived and exportable in backups; they no longer influence running plans. Pending cycling recordings are archived without inventing a completed activity. Existing running and walking checkpoints remain recoverable.

The dark OpenRun design retains original branding with a calmer surface palette and teal primary actions. Four tabs—Today, Plan, Progress and Profile—replace competing destinations. Today prioritises the next planned run, Start run, Start walk and indoor walking/treadmill entry. Plan opens the weekly schedule directly; Calendar and plan management live inside it. Progress separates Runs, Walks and Performance.

Outdoor walks have their own GPS recording identity, history, routes, GPX export and delete/undo. Indoor walking-pad sessions and calibrated phone steps remain supported. Walking totals never inflate running mileage. Health Connect imports running and walking only; missing walk distance remains time only. Walking Health Connect export is not implemented.

Retained: original adaptive running rules, scheduling, offline storage and backup, audio coaching, run/walk workout phases, Bluetooth HR, movement reminders and optional independently hosted services. No Garmin account connection is simulated.

Version 1.2.0 / code 15; Android 8+ (API 26), target API 36. Development signed APK, compatible with the retained development signature. Install over the previous build without uninstalling; export a backup first.
