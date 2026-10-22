# OpenRun 1.4.0 · planning and offline coaching

The planning path now follows the supplied visual references: Plan return hub → goal cards → race search with horizontal filter chips → event or dedicated manual race form → actual running → weekday choices → concrete plan preview. A goal, distance or event selection immediately moves forward. Manual race entry validates before Add enables. Back and rotation preserve the draft. The wizard uses a black canvas, slate cards, teal details, a compact progress track and separate back/close controls, with no duplicate bottom navigation.

The same wizard handles distance goals, start/return to running, 5 km improvement and general training. Date-anchored race plans keep the selected weekdays and long-run day; non-race goals do not retain a stale race target. Existing history, exact-preview storage, stale-preview checks and training holds remain. Running and walking remain the app's scope.

Optional Offline Coach adds a real LiteRT-LM native integration for a separately imported, checksum-pinned Gemma 3 1B INT4 model. Generation is isolated in its own app process, cancellable and bounded. It explains the reviewed summary; deterministic rules still create the schedule. A model manager handles download consent, publisher licence gates, verification, installation and removal. Read OFFLINE-COACH.md for setup and the unverified hardware/generation limits.

A small, source-attributed offline Cape Town catalogue remains available alongside manual entry. The city cover is original illustrative artwork; no participant/popularity counts or comprehensive South African feed are invented. Catalogue refresh still needs an operator's configured HTTPS service.

Version 1.4.0 / code 18; min API 26, target API 36. Inference binaries: ARM64 and x86_64. Development signed using the retained certificate, so install over the previous development build without uninstalling. Export a backup before restore experiments. This is a testable development candidate, not a Play production release.
