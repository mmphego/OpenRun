# Build and publish a release

## GitHub APK distribution

No Google Play account, service account, package upload or Play deployment is used. Every main build publishes an installable APK directly on GitHub Releases after checks. Publication requires one persistent private signing key. Runner-generated debug keys are never published because successive runners produce incompatible certificates. PR/debug artifacts remain test builds, separate from distribution. Google Play is not involved.

## Maintainer setup on your Mac

With a JDK (`keytool`), GitHub CLI (`gh auth login`), Python 3 and OpenSSL installed, run from the reviewed repository checkout:

```sh
bash scripts/configure-signing.sh
```

The script creates a private keystore and password under `~/.config/openrun/signing`, sends four repository secrets through stdin and sets the public `ANDROID_SIGNING_CERT_SHA256` repository variable. It never commits or prints the private key/password. Keep an encrypted offline backup of this directory. Existing repository signing cannot be replaced with a newly generated key by this script. If existing signing secrets have no certificate pin (including an interrupted first setup), it refuses all updates until the original signing certificate is independently verified and pinned; do not pin an unrelated local key. To use an existing signing key, configure the four secrets manually and pin its certificate SHA-256; use the original key if compatible upgrades are required.

## Existing installations signed by old runner keys

Published debug builds 100046 and 100048 have different certificates. If an original runner private key was not retained, it cannot be recovered from the APK. A newly established key cannot update those installations in place. Use Profile → Export full backup, save it outside app storage and verify that the file exists before any migration. This local backup is a ZIP, not automatically encrypted; protect it because it contains plans, history and routes. Local-only wellness check-ins and export identity state are excluded. A one-time uninstall/reinstall may be necessary after that backup; uninstalling deletes app data. Restore and verify the backup afterward. Do not suggest repeated uninstalling as a normal upgrade strategy. Subsequent signed releases must retain `app.rwun`, the same certificate and increasing versionCode. Physical-device upgrade acceptance still needs verification.

## Stable signing configuration

Resolve [project licensing](../LICENSING.md) and production/device acceptance first. Generate a stable signing keystore privately; never commit it. Losing it prevents compatible updates to installed releases. Keep an offline backup and limit who can edit release workflows and signing secrets.

Create these GitHub **repository Actions Secrets**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload/release keystore bytes, without logging them |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias inside that keystore |
| `ANDROID_KEY_PASSWORD` | Key password |

All four secrets plus the public certificate pin are required. Missing or partial configuration fails early; invalid credentials never fall back to debug signing. Packaging verifies the APK certificate against the pinned SHA-256 and refuses publication on mismatch. PR checks have no signing secrets. Only trusted upstream main/tag pushes can sign. Protect main, workflow edits, secrets and the pin variable.

## Automatic releases after merge

Every push to `main` runs the signed release workflow, including merged PRs. It runs the full host, Android lint/unit/APK and Python checks before signing. Releases use the committed `versionName` and a deterministic Android `versionCode` of `100000 + git rev-list --count HEAD`. Full Git history is required. This gives successive main builds increasing codes for updates with a compatible certificate without version-bump commits or conflicts with parallel feature PRs. Local builds retain the committed versionCode; use the Actions APK for signed upgrades. Do not rewrite main history. A later manual code must exceed the installed automatic code.

Each main build gets a unique tag `v<versionName>-build.<versionCode>`, a signed `OpenRun-<versionName>-<versionCode>-release.apk`, and a SHA-256 file. Release notes identify the actual APK version/code and download filename, with GitHub-generated merged PR/commit notes. The release is staged as a draft, assets uploaded, then published as Latest. Signing and ZIP alignment are verified. These are automated builds; device acceptance and licensing status remain separate.

The workflow serializes releases without cancelling active signing runs. GitHub concurrency may coalesce pending pushes during a burst; the newest pending main build includes preceding merges. Published assets are never overwritten on reruns. Failed drafts can be repaired by rerunning the same commit; only draft assets can be replaced. A workflow-created tag does not trigger a second workflow with `GITHUB_TOKEN`.

Optional maintainer tags `vMAJOR.MINOR.PATCH` still work: the tag must match the committed versionName and point to a commit on main. Use new versions for changed source; never move published tags. Do not publish an old main commit as Latest. Update CHANGELOG.md for product changes. No manual tag or version bump is needed for automatic build releases.

Get the newest APK from [Latest release](https://github.com/mmphego/OpenRun/releases/latest). Stable signing configuration is required before publishing. Test and report artifacts are retained for 7 days; signed release assets persist.

## Local signing

Use a private shell/secret manager to set `RWUN_KEYSTORE_PATH` (absolute path), `RWUN_KEYSTORE_PASSWORD`, `RWUN_KEY_ALIAS` and `RWUN_KEY_PASSWORD`, then run:

```bash
./gradlew :app:verifyReleaseSigning :app:assembleRelease
```

Do not put passwords in shell history, CLI arguments or committed properties. With no signing environment variables, `assembleRelease` alone produces an unsigned local build; the publishing workflow does not permit that path. Debug builds use the machine's default Android debug key and are not production releases.
