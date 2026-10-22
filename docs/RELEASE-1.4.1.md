# OpenRun 1.4.1 · smaller typography

Reduced app text through a shared compact sp scale. Planning headings: 30 → 24 sp; card titles and inputs: 19 → 16 sp; body: 16 → 14 sp; buttons: 15 → 14 sp. Small captions/navigation remain at their existing sizes. Recording/progress auto-size limits follow the new scale. Android accessibility font scaling remains enabled and touch areas remain unchanged.

Version 1.4.1, code 19. Install over the existing development build without uninstalling. Plan generation, recording, stored history, permissions and model behavior were not changed.

Validation: standalone API 36 build succeeds; APK alignment and retained signing certificate pass. Five existing Robolectric Native Graphics interaction tests pass, with twelve native host renders. Goal, race/manual forms and 160% font renders were visually inspected. No new business-logic tests were added for this presentation-only change. Test Android Apps target check found no connected adb device; these are host renders, not emulator or physical S23 results. Existing live Gemma/S23/integration limitations remain in REMAINING-GAPS.md.
