#!/usr/bin/env python3
"""Describe the exact signed APK; GitHub appends the commit/PR change notes."""
import json
import sys
from pathlib import Path

assets = Path(sys.argv[1])
variant = sys.argv[2] if len(sys.argv) > 2 else 'release'
if variant not in ('debug', 'release'):
    raise SystemExit('Expected debug or release APK variant')
metadata = json.loads(Path(f'app/build/outputs/apk/{variant}/output-metadata.json').read_text())
element, = metadata['elements']
apk, = assets.glob('*.apk')
print(f"## Android APK\n\nVersion: **{element['versionName']}** "
      f"(versionCode **{element['versionCode']}**).")
print(f"\nDownload **{apk.name}** from this release's assets. "
      f"This is a {variant}-signed APK for Android 8.0+, for direct installation; Google Play is not used.")
if variant == "debug":
    print("\n**Debug build:** debuggable and signed with the runner debug key. "
          "Keys may differ between builds. An incompatible certificate blocks in-place updates. "
          "This is not a supported distribution update. Keep the installed app if its certificate conflicts; never uninstall or clear data to apply this build.")
print(f"\nVerify the download with `sha256sum -c {apk.name}.sha256`. "
      "Updates require the same signing certificate; back up your data before installing.")
print("\nAutomated build checks do not establish physical-device acceptance.")
identity = Path('build/reports/upgrade/identity.json')
if identity.exists() and json.loads(identity.read_text()).get('fresh_install_required'):
    print("\n## One-time fresh installation required\n\n"
          "This release establishes the permanent signing key. It cannot update old "
          "temporary-key installations. If you accept losing local data, uninstall the old "
          "app once and install this release. Export anything you want to retain first. "
          "Future release APKs use this same permanent key and must pass upgrade checks. "
          "This build tests a fresh install and same-key reinstall, not migration from the old APK.")
print("\n## Included changes\n")
