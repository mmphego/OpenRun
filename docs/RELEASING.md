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

These secrets are not available in this prepared checkout. Release workflow preflight fails explicitly when any is missing; it never publishes an unsigned or debug-signed APK. PR/default-branch CI has no secret references. The release job uses a trusted version tag in the upstream repository only. Restrict tag creation/workflow edits to trusted maintainers; use repository rules and protected environments if your account supports them.

No personal access token is needed for the release: only its publish step receives the workflow `GITHUB_TOKEN` with `contents: write`.

## Version and tag

1. Increment `versionCode` and `versionName` in `app/build.gradle`; update the changelog and remove release blockers only with evidence.
2. Run `bash scripts/check.sh`, then install and test the exact candidate on a device. Complete [S23 acceptance](../tests/s23/CHECKLIST.md). Store only sanitised evidence.
3. Push the reviewed commit to `main`. Confirm CI is green.
4. Tag the matching version and push without force:

```bash
git tag -a v1.7.0 -m "OpenRun 1.7.0"
git push origin v1.7.0
```

The workflow verifies a semantic `vMAJOR.MINOR.PATCH` tag, exact versionName match and membership in `main`, runs host/unit/lint/service checks, builds the release with the committed wrapper, verifies the signature, and stages `OpenRun-<version>.apk` plus `OpenRun-<version>.apk.sha256`. It creates a draft GitHub Release, attaches both files and only then publishes it. Existing releases are not overwritten.

Missing secrets, failed checks or bad tag provenance stop publication. Repair the cause before retrying; do not move a published version tag or silently replace its binary. Issue a new version for changed source. Release reports retain for 7 days; signed release assets remain on the Release.

## Local signing

Use a private shell/secret manager to set `RWUN_KEYSTORE_PATH` (absolute path), `RWUN_KEYSTORE_PASSWORD`, `RWUN_KEY_ALIAS` and `RWUN_KEY_PASSWORD`, then run:

```bash
./gradlew :app:verifyReleaseSigning :app:assembleRelease
```

Do not put passwords in shell history, CLI arguments or committed properties. With no signing environment variables, `assembleRelease` alone produces an unsigned local build; the publishing workflow does not permit that path. Debug builds use the machine's default Android debug key and are not production releases.
