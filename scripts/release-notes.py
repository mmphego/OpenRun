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
          "Export a backup first; do not uninstall without preserving your data.")
print(f"\nVerify the download with `sha256sum -c {apk.name}.sha256`. "
      "Updates require the same signing certificate; back up your data before installing.")
print("\nAutomated build checks do not establish physical-device acceptance.")
print("\n## Included changes\n")
