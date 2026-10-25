#!/usr/bin/env bash
# GitHub sideload distribution. No Google Play account or credentials required.
set -euo pipefail
configured=0
for variable in KEYSTORE_BASE64 RWUN_KEYSTORE_PASSWORD RWUN_KEY_ALIAS RWUN_KEY_PASSWORD; do
  if [ -n "${!variable:-}" ]; then configured=$((configured + 1)); fi
done
if [ "$configured" = 0 ]; then
  variant=debug
  echo 'No private signing key configured; publishing a labelled debug-signed APK.'
  ./gradlew --no-daemon :app:assembleDebug
elif [ "$configured" = 4 ]; then
  variant=release
  export RWUN_KEYSTORE_PATH="$RUNNER_TEMP/openrun-release.keystore"
  trap 'rm -f "$RWUN_KEYSTORE_PATH"' EXIT
  umask 077
  printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$RWUN_KEYSTORE_PATH"
  ./gradlew --no-daemon :app:verifyReleaseSigning :app:assembleRelease
else
  echo '::error::Partial signing configuration. Set all four secrets or remove them all for debug APK distribution.'
  exit 1
fi
apk="app/build/outputs/apk/$variant/app-$variant.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$apk"
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 "$apk"
mkdir -p release-assets
name="OpenRun-${RWUN_RELEASE_VERSION#v}-${OPENRUN_VERSION_CODE}-${variant}.apk"
cp "$apk" "release-assets/$name"
(cd release-assets && sha256sum "$name" > "$name.sha256")
echo "variant=$variant" >> "$GITHUB_OUTPUT"
