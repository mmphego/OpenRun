# Screenshot-led UX corrections — 0.7

Evidence: user-supplied Samsung screenshots of OpenRun 0.6, Health Connect permission denial, and a Runna plan landing page. The uploaded photos/profile and Runna assets are not bundled in OpenRun.

| Observed issue | Change |
| --- | --- |
| Manage access exposes Permission Denial / GRANT_RUNTIME_PERMISSIONS | Replace restricted intent with public HEALTH_HOME_SETTINGS; guarded general-settings fallback and manual help |
| Performance word wraps with arrow and selected suffix | Dedicated tabs with no compound icons, no visible suffix, one-line text and accessible selection |
| Giant branding/back button consumes detail screen | Centred 56dp app bar; contextual icon Back; original OpenRun mark and functional run shortcut |
| Plan immediately dumps a week of sessions | Plan hub with the three reference actions; continue opens schedule; new plan has four steps and preview |
| Empty performance screen repeats zero bars | Explain empty state and offer Record/Import |
| Connection pages expose backend implementation paragraphs | Concise availability state and optional details |
| Restart/new plan can clear training hold | Preserve hold and history; only explicit resume clears hold |

Reference layout: 20dp horizontal margins; centred welcome and 40dp Material waving-hand icon; 64dp minimum choice rows with 14dp corner radius and chevrons; charcoal/slate palette. All content remains scrollable and font scaling is retained. OpenRun uses original code and branding.

Native layout regression checks were added for tab text at font scale 1.0, 1.3 and 2.0. They require an Android runtime to execute. The Android build is separate from visual verification; see VALIDATION-0.7.md for executed results.
