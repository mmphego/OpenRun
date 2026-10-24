# Build and publish a release

## One-time maintainer setup

Resolve [project licensing](../LICENSING.md) and production/device acceptance first. Generate a stable signing keystore privately; never commit it. Losing it prevents compatible updates to installed releases. Keep an offline backup and limit who can edit release workflows and signing secrets.

Create these GitHub **repository Actions Secrets**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload/release keystore bytes, without logging them |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias inside that keystore |
| `ANDROID_KEY_PASSWORD` | Key password |

These secrets are required for automatic publication. Missing secrets fail the release job explicitly; it never substitutes a debug or unsigned APK. PR checks have no signing secrets. Only trusted pushes to upstream `main` or version tags can run the signing workflow. Protect `main` and workflow edits.

## Automatic releases after merge

Every push to `main` runs the signed release workflow, including merged PRs. It runs the full host, Android lint/unit/APK and Python checks before signing. Releases use the committed `versionName` and a deterministic Android `versionCode` of `100000 + git rev-list --count HEAD`. Full Git history is required. This keeps successive main builds upgradeable without version-bump commits or conflicts with parallel feature PRs. Local builds retain the committed versionCode; use the Actions APK for signed upgrades. Do not rewrite main history. A later manual code must exceed the installed automatic code.

Each main build gets a unique tag `v<versionName>-build.<versionCode>`, a signed `OpenRun-<versionName>-<versionCode>.apk`, and a SHA-256 file. Release notes identify the actual APK version/code and download filename, with GitHub-generated merged PR/commit notes. The release is staged as a draft, assets uploaded, then published as Latest. Signing and ZIP alignment are verified. These are automated builds; device acceptance and licensing status remain separate.

The workflow serializes releases without cancelling active signing runs. GitHub concurrency may coalesce pending pushes during a burst; the newest pending main build includes preceding merges. Published assets are never overwritten on reruns. Failed drafts can be repaired by rerunning the same commit; only draft assets can be replaced. A workflow-created tag does not trigger a second workflow with `GITHUB_TOKEN`.

Optional maintainer tags `vMAJOR.MINOR.PATCH` still work: the tag must match the committed versionName and point to a commit on main. Use new versions for changed source; never move published tags. Do not publish an old main commit as Latest. Update CHANGELOG.md for product changes. No manual tag or version bump is needed for automatic build releases.

Get the newest APK from [Latest release](https://github.com/mmphego/OpenRun/releases/latest). Signing secrets must be configured before the first merge. Test and report artifacts are retained for 7 days; signed release assets persist.

## Local signing

Use a private shell/secret manager to set `RWUN_KEYSTORE_PATH` (absolute path), `RWUN_KEYSTORE_PASSWORD`, `RWUN_KEY_ALIAS` and `RWUN_KEY_PASSWORD`, then run:

```bash
./gradlew :app:verifyReleaseSigning :app:assembleRelease
```

Do not put passwords in shell history, CLI arguments or committed properties. With no signing environment variables, `assembleRelease` alone produces an unsigned local build; the publishing workflow does not permit that path. Debug builds use the machine's default Android debug key and are not production releases.
