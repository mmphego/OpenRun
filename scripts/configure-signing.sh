#!/usr/bin/env bash
# Run once on the maintainer's machine. Secrets travel via stdin, never via Git.
set -euo pipefail
repo=mmphego/OpenRun
signing_dir="${OPENRUN_SIGNING_DIR:-$HOME/.config/openrun/signing}"
keystore="$signing_dir/openrun-release.keystore"
for command in gh keytool openssl python3; do
  command -v "$command" >/dev/null || { echo "Install $command before continuing."; exit 1; }
done
gh auth status >/dev/null
# Never generate a replacement for already-configured repository signing.
remote=$(gh secret list --repo "$repo" --json name --jq '.[].name')
if [ ! -f "$keystore" ] && printf '%s\n' "$remote" | grep -E '^ANDROID_(KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)$' >/dev/null; then
  echo 'Repository signing already exists but this machine has no original keystore. Restore it instead of replacing the key.'
  exit 1
fi
umask 077
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
password_file="$signing_dir/password"
if [ ! -f "$keystore" ]; then
  if [ -e "$password_file" ]; then echo 'Existing password without keystore. Restore the keystore or inspect this directory first.'; exit 1; fi
  openssl rand -hex 32 > "$password_file"
  keytool -genkeypair -keystore "$keystore" -storetype JKS -storepass:file "$password_file" -keypass:file "$password_file" -alias openrun -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=OpenRun Android,O=OpenRun' >/dev/null 2>&1
fi
[ -s "$password_file" ] || { echo 'Restore the matching password file before continuing.'; exit 1; }
# Public fingerprint only; never print key bytes or passwords.
fingerprint=$(keytool -exportcert -keystore "$keystore" -storepass:file "$password_file" -alias openrun 2>/dev/null | openssl dgst -sha256 | awk '{print $NF}')
existing=$(gh variable list --repo "$repo" --json name,value --jq '.[] | select(.name=="ANDROID_SIGNING_CERT_SHA256") | .value')
if [ -n "$existing" ] && [ "$existing" != "$fingerprint" ]; then
  echo 'Repository certificate pin differs. Refusing to replace an existing signing identity.'
  exit 1
fi
python3 - "$keystore" <<'PY' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$repo"
import base64,sys
from pathlib import Path
print(base64.b64encode(Path(sys.argv[1]).read_bytes()).decode())
PY
tr -d '\r\n' < "$password_file" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$repo"
printf '%s' openrun | gh secret set ANDROID_KEY_ALIAS --repo "$repo"
tr -d '\r\n' < "$password_file" | gh secret set ANDROID_KEY_PASSWORD --repo "$repo"
gh variable set ANDROID_SIGNING_CERT_SHA256 --repo "$repo" --body "$fingerprint"
printf 'Configured stable signing for %s. Certificate SHA-256: %s\n' "$repo" "$fingerprint"
printf 'Back up %s and its password securely outside this machine. Never commit or share them.\n' "$signing_dir"
