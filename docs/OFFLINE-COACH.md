# Retired Offline Coach

Local Gemma model support has been removed in PR #27. The model manager, download
and import actions, isolated inference process, LiteRT dependency and standalone
runtime fetch/lock files are no longer part of the app.

Use [AI Coach](AI-COACH.md) with your own OpenAI or Google Gemini API key. No model
download is needed. Core running plans, recording and voice guidance still work
offline; AI generation and plan personalisation require internet and explicit consent.

On process start, a background upgrade cleanup removes only the old private
`files/offline-coach` model directory and `cache/gemma` runtime cache, including
interrupted downloads. It never clears training preferences, recorded routes,
activities or BYOK settings. Unremovable files are retried on a later start.
The previous runtime's third-party notices are preserved for provenance.

Older release notes refer to the historical implementation; they do not describe
current capabilities. No local inference or model installation should appear in the
current app or acceptance checklist.
