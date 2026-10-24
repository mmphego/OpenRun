#!/usr/bin/env python3
"""Describe the exact signed APK; GitHub appends the commit/PR change notes."""
import json
import sys
from pathlib import Path

assets = Path(sys.argv[1])
metadata = json.loads(Path('app/build/outputs/apk/release/output-metadata.json').read_text())
element, = metadata['elements']
apk, = assets.glob('*.apk')
print(f"## Android APK\n\nVersion: **{element['versionName']}** "
      f"(versionCode **{element['versionCode']}**).")
print(f"\nDownload **{apk.name}** from this release's assets. "
      "This is a release-signed APK for Android 8.0+, not a debug build.")
print(f"\nVerify the download with `sha256sum -c {apk.name}.sha256`. "
      "Updates require the same signing certificate; back up your data before installing.")
print("\nAutomated build checks do not establish physical-device acceptance.")
print("\n## Included changes\n")
