#!/usr/bin/env python3
"""Check the packaged manifest, not just the output filename or Gradle metadata."""
import re
import subprocess
import sys


def verify(badging, version_name, version_code):
    package_line = next((line for line in badging.splitlines() if line.startswith('package: ')), '')
    fields = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    expected = {'name': 'app.rwun', 'versionName': version_name, 'versionCode': str(version_code)}
    if any(fields.get(key) != value for key, value in expected.items()):
        raise ValueError(f'APK manifest identity mismatch: expected {expected}, got {fields}')


if __name__ == '__main__':
    aapt2, apk, version_name, version_code = sys.argv[1:]
    try:
        verify(subprocess.check_output([aapt2, 'dump', 'badging', apk], text=True), version_name, version_code)
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
    print(f'APK manifest verified: {version_name} / {version_code}')
