#!/usr/bin/env bash
# Publication guard. Never mutate a device, old release or user data.
set -euo pipefail
: "${GH_REPO:?Set GH_REPO}"
: "${ANDROID_HOME:?Set ANDROID_HOME}"
baseline_dir=$(mktemp -d "$RUNNER_TEMP/openrun-baseline.XXXXXX")
trap 'rm -rf "$baseline_dir"' EXIT
# Failure to query/download is a blocked release, not permission to change keys.
prior=$(gh release view --repo "$GH_REPO" --json tagName --jq '.tagName')
gh release download "$prior" --repo "$GH_REPO" --pattern '*.apk' --dir "$baseline_dir"
shopt -s nullglob
previous=("$baseline_dir"/*.apk)
candidate=(release-assets/*.apk)
if [ "${#previous[@]}" != 1 ] || [ "${#candidate[@]}" != 1 ]; then
  echo '::error::Expected one baseline and one candidate APK. Publication blocked.'
  exit 1
fi
mkdir -p build/reports/upgrade
python3 scripts/verify-apk-upgrade.py "${previous[0]}" "${candidate[0]}" "$ANDROID_HOME/build-tools/35.0.0" > build/reports/upgrade/identity.json
mkdir -p build/upgrade
cp "${previous[0]}" build/upgrade/baseline.apk
printf 'Verified package, certificate and increasing versionCode against %s\n' "$prior"
