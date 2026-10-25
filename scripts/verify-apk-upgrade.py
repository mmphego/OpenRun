#!/usr/bin/env python3
"""Fail closed unless candidate is a normal, same-identity upgrade of baseline."""
import hashlib
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


# Owner-approved fresh-install transition from the last temporary-key release.
LEGACY_APK_SHA256 = 'd17b23794947f8672efe762e4ee6587d355221180c3aa73323f919a6c361f510'
PERMANENT_CERTIFICATE = '68af5585f41ce61edcb7abf4befa4ffff4e3638d551ab39a014347d7a04263a4'


def release_policy(previous, candidate, baseline_sha256):
    if candidate['certificates'] != [PERMANENT_CERTIFICATE]:
        raise ValueError('Candidate must use the permanent OpenRun signing key')
    if (baseline_sha256 == LEGACY_APK_SHA256
            and previous['package'] == 'app.rwun'
            and previous['version_code'] == 100048
            and candidate['package'] == 'app.rwun'
            and candidate['version_code'] > previous['version_code']):
        return {'baseline': previous, 'candidate': candidate,
                'compatible_identity': False, 'fresh_install_required': True}
    result = verify(previous, candidate)
    result['fresh_install_required'] = False
    return result


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
        print(json.dumps(release_policy(inspect(old, tools), inspect(new, tools),
                                        hashlib.sha256(old.read_bytes()).hexdigest()), indent=2))
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))


if __name__ == '__main__':
    main()
