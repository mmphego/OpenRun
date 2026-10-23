# OpenRun 1.5.0 candidate

OpenRun 1.5.0 uses Android versionCode **24**, above the recovered Rwun 1.5.0 build's 23. This prepares a pull request; it does not publish a production APK or version tag.

## Included work

- Reconciles the saved Rwun 1.5.0 race implementation with the existing OpenRun repository, contributor documentation, compatibility fixes and CI/release workflows.
- Uses OpenRun in the Today header, widget, exported share card and feature graphic. APK names, install examples, QA defaults and release-tag examples use 1.5.0.
- Restores the full local race search/filter screen and its result handoff into the planning wizard, multi-day date ranges, conservative matching, provider event IDs, bounded Bield redirects and failed-provider isolation.
- Adds a Room schema 1→2 migration for both previous layouts. Catalogue IDs, saved distances, reminders and training-goal references survive; training preferences and backup identity remain unchanged.
- Runs the recovered native Android host suites through Gradle in isolated worker processes, alongside the existing Room tests and two migration tests. Live-provider validation stays opt-in. The standalone build retains Room annotation processing.

## Upgrade and release limits

Package `app.rwun`, database name `race-catalogue.db`, storage keys, external record identifiers and portable backup discriminator `Rwun` are retained. Save active sessions and export a backup before testing. An in-place APK upgrade requires the original signing certificate; a new CI debug key cannot replace a differently signed installation. Do not uninstall or clear data to force an upgrade.

No signing key is included. The original four-secret, signed version-tag release workflow remains in place. Project-wide licensing, production signing configuration and physical S23 acceptance remain unresolved. Garmin account sync is not implemented, Gemma/device performance is unverified, and RunSignup remains approval/proxy-gated. Saved catalogue choices are still outside the older RunStore/cloud-backup export.

## Source and evidence

The recovered `Rwun-1.5.0-source.zip` has SHA-256 `8ac95be64199eb698aa9e9d07b4b4f3cc38acd52ba27a0446fb71b1f20e418f0`. Its signing key, machine files, generated logs and historical evidence were excluded from the repository. Existing notices and the published Git history remain intact.

Use the PR's Actions results for this candidate's tests and APK artifact. Older claims of 380 live-feed editions or retained development signatures are historical observations, not validation of this build. Native renders are host evidence; no emulator installation or physical S23 test is claimed.
