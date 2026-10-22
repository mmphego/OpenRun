# OpenRun 1.3.0 — race-first planning

Version code 17. Original running/walking app with Today, Plan, Calendar, Progress and Coach retained.

- Race entry from Welcome, distance onboarding and Manage plan. Search/filter a small source-checked catalogue or add a road event manually. Selecting a race advances to history/fitness, available training days and an exact preview.
- Race name/date/distance/city saved with the schedule; visible in plan summary and Calendar. Training ends before race day. Includes shorter race-week sessions and warnings about short lead time, low baseline and future build start.
- Reviewed initial volume from entered fitness, recorded 28-day mileage, inactivity and reported feedback. Coach can offer a reviewed return/lighter week from new running evidence. No catch-up mileage or automatic increases/hold clearance.
- Optional independent backend race catalogue API, atomic Android cache, authorised normalised feed support, and opt-in Gemini explanation endpoint. Provider secrets remain on the server.
- Target, schedule and training settings save atomically; stale previews refuse changes. Existing histories and holds remain.

The bundled catalogue has four event variants, not nationwide live coverage. Manual race entry/local planning are usable now. Live catalogue refresh needs a deployed/configured backend; broader coverage needs an authorised feed. Gemini needs hosting, account and server credentials/model. No connection is simulated. See RACE-DATA-AND-AI.md for precise contracts and research.

Android 8+; target API 36; public development signature retained. Install over the previous app without uninstalling. Physical S23 testing and qualified coaching review remain pending.
