#!/usr/bin/env python3
"""Run existing standalone Java regressions and retain logs plus a JUnit report."""
from pathlib import Path
import hashlib
import os
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEPENDENCIES = [
    ('json.jar', 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'),
    ('kxml.jar', 'https://repo.maven.apache.org/maven2/net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0.jar', 'f264dd9f79a1fde10ce5ecc53221eff24be4c9331c830b7d52f2f08a7b633de2'),
]

def main():
    cache = ROOT / '.ci'
    reports = ROOT / 'build/reports/host'
    cache.mkdir(exist_ok=True)
    reports.mkdir(parents=True, exist_ok=True)
    for name, url, expected in DEPENDENCIES:
        target = cache / name
        if not target.exists():
            with urllib.request.urlopen(url, timeout=60) as response:
                target.write_bytes(response.read())
        if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
            raise SystemExit(f'Checksum mismatch: {name}. Remove it and retry.')
    env = dict(os.environ, JSON_JAR=str(cache / 'json.jar'), KXML2_JAR=str(cache / 'kxml.jar'))
    suite = ET.Element('testsuite', name='OpenRun host regressions')
    failures = 0
    scripts = sorted((ROOT / 'tests').glob('run-*.sh'))
    for script in scripts:
        started = time.monotonic()
        result = subprocess.run(['bash', str(script)], cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        (reports / f'{script.stem}.txt').write_text(result.stdout)
        case = ET.SubElement(suite, 'testcase', name=script.stem, time=str(time.monotonic() - started))
        ET.SubElement(case, 'system-out').text = result.stdout
        if result.returncode:
            failures += 1
            ET.SubElement(case, 'failure', message=f'exit {result.returncode}').text = result.stdout
        print(f'{script.stem}: {"PASS" if result.returncode == 0 else "FAIL"}', flush=True)
        if result.returncode: print(result.stdout)
    suite.set('tests', str(len(scripts)))
    suite.set('failures', str(failures))
    ET.ElementTree(suite).write(reports / 'junit.xml', encoding='utf-8', xml_declaration=True)
    return bool(failures)

if __name__ == '__main__':
    sys.exit(main())
