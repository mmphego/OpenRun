#!/usr/bin/env python3
"""Fail closed unless candidate is a normal, same-identity upgrade of baseline."""
import json
import re
import subprocess
import sys
from pathlib import Path


def inspect(apk, tools):
    certificates = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', certificates, re.M)
    if not signers:
        raise ValueError('Missing verified signing certificate')
    badging = subprocess.check_output([str(tools/'aapt2'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.M)
    if not package:
        raise ValueError('Missing APK package/version identity')
    if 'application-testOnly' in badging:
        raise ValueError('Test-only APKs cannot be distributed as upgrades')
    return {'package': package[1], 'version_code': int(package[2]), 'certificates': sorted(s.lower() for s in signers)}


def verify(previous, candidate):
    if previous['package'] != 'app.rwun' or candidate['package'] != previous['package']:
        raise ValueError('Package identity changed; publication blocked')
    if candidate['certificates'] != previous['certificates']:
        raise ValueError('Published APK certificate differs. STOP: recover the original signing key; do not ask users to uninstall or clear data.')
    if candidate['version_code'] <= previous['version_code']:
        raise ValueError('Version code must increase; publication blocked')
    return {'baseline': previous, 'candidate': candidate, 'compatible_identity': True}


def main():
    try:
        old, new, tools = map(Path, sys.argv[1:4])
        print(json.dumps(verify(inspect(old, tools), inspect(new, tools)), indent=2))
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))


if __name__ == '__main__':
    main()
