# OpenRun 0.6 theme and flow

The supplied Runna screenshots inform charcoal surfaces, slate grouped rows, pale CTAs and restrained teal accents. OpenRun uses its own mark, original native code and licensed Material Symbols. No Runna imagery, customer profile, records or plan data are copied.

Main destinations: Today, Plan, Calendar, Progress and Profile. Record a free run from Today; workout details start scheduled sessions. Profile groups training preferences, backup and Connections. Connections separates Health Connect read access, Garmin account availability, activity files and live Bluetooth HR.

Progress uses actual saved totals, weekly bars and longest saved distance; it does not invent race-distance personal bests. Health imports display elapsed duration and unavailable feedback explicitly. Garmin account status remains unavailable until an official integration is configured.

The native dark theme is the default in 0.6. Emulator boot failed before runtime visual inspection; screenshot, accessibility and font-scale review remain device tasks.

# OpenRun 0.5 UI redesign

The supplied Samsung screenshots showed oversized headings, a single dense recording text block, repeated large filled buttons, legacy rectangular navigation and detail content beginning at a previous scroll offset.

The redesign introduces shared `UiKit` presentation primitives and a separate `RunDashboard` that binds directly to `TrackingService`. Navigation uses official Material Symbols Rounded vectors with selected pills and text labels. Filled buttons identify key actions; tonal and destructive variants keep secondary actions distinct. The header and content spacing respect window insets and the scrolling region ends above bottom navigation.

Detail content explicitly resets its parent scroll offset. Body text remains responsive to system font scale; numeric workout metrics auto-size within their own cells. The system night-mode palette covers surfaces, controls, icons, route lines and system-bar appearance. Launcher resources include Android 8+ adaptive layers and an Android 13+ monochrome layer using OpenRun's original mark.

Weekly progress is computed from saved activities and the current plan; no placeholder activity, heart-rate or pace data was added. Short history distances display as metres to avoid 0.0 km records.

Superdesign's CLI preflight was rejected by automatic review because it attempted unverified external PostHog telemetry. The redesign was implemented locally; no Superdesign canvas or generated design draft is claimed.

Icons: https://github.com/google/material-design-icons ; current Android recommendation: https://developer.android.com/develop/ui/compose/graphics/images/material . License and source hashes are in `docs/icons/`.
