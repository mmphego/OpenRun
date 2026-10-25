#!/usr/bin/env bash
# GitHub sideload distribution. No Google Play account or credentials required.
set -euo pipefail
configured=0
for variable in KEYSTORE_BASE64 RWUN_KEYSTORE_PASSWORD RWUN_KEY_ALIAS RWUN_KEY_PASSWORD; do
  if [ -n "${!variable:-}" ]; then configured=$((configured + 1)); fi
done
if [ "$configured" != 4 ]; then
  echo '::error::Stable signing requires all four ANDROID signing secrets. Run scripts/configure-signing.sh on your own machine; see docs/RELEASING.md. Random runner keys cannot produce compatible updates.'
  exit 1
fi
if [[ ! "${EXPECTED_SIGNING_SHA256:-}" =~ ^[[:xdigit:]]{64}$ ]]; then
  echo '::error::Set repository variable ANDROID_SIGNING_CERT_SHA256 using scripts/configure-signing.sh.'
  exit 1
fi
variant=release
export RWUN_KEYSTORE_PATH="$RUNNER_TEMP/openrun-release.keystore"
trap 'rm -f "$RWUN_KEYSTORE_PATH"' EXIT
umask 077
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$RWUN_KEYSTORE_PATH"
./gradlew --no-daemon :app:verifyReleaseSigning :app:assembleRelease
apk="app/build/outputs/apk/$variant/app-$variant.apk"
certificate=$("$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$apk")
printf '%s\n' "$certificate"
actual=$(printf '%s\n' "$certificate" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')
if [ "$actual" != "${EXPECTED_SIGNING_SHA256,,}" ]; then
  echo '::error::APK signing certificate changed. Refusing publication to protect installed-app upgrades.'
  exit 1
fi
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 "$apk"
mkdir -p release-assets
name="OpenRun-${RWUN_RELEASE_VERSION#v}-${OPENRUN_VERSION_CODE}-${variant}.apk"
cp "$apk" "release-assets/$name"
(cd release-assets && sha256sum "$name" > "$name.sha256")
echo "variant=$variant" >> "$GITHUB_OUTPUT"
