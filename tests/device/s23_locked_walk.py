#!/usr/bin/env python3
"""Collect a carried-phone locked-screen walk using UI and public adb diagnostics."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def parse_recording(xml):
    root = ET.fromstring(xml)
    text = [n.get('text', '') for n in root.iter('node')]
    if 'Your walking-pad walk' not in text or 'PHONE STEPS · NO GPS' not in text:
        raise ValueError('Open the active indoor walking screen and scroll to the top with steps and timer visible.')
    step_label = next((t for t in text if re.match(r'^\d+ detected steps ·', t)), '')
    duration = [t for t in text if re.fullmatch(r'\d{2}:\d{2}:\d{2}', t)]
    distance = [t for t in text if re.fullmatch(r'~\d+\.\d+', t)]
    phase = next((t for t in text if t.startswith('Recording time ·') or t.startswith('Paused ·')), '')
    if not step_label or len(duration) != 1 or not phase:
        raise ValueError('Step count/timer/status not visible. Scroll to the top and retry without pausing.')
    h, m, s = map(int, duration[0].split(':'))
    return {'steps': int(step_label.split()[0]), 'seconds': h*3600+m*60+s,
            'estimate_km': float(distance[0][1:]) if len(distance) == 1 else 0,
            'partial': 'may be partial' in step_label, 'paused': phase.startswith('Paused'),
            'personally_calibrated': 'personal calibration' in step_label}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adb', default=os.environ.get('ADB', 'adb'))
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--minutes', type=int, default=31)
    p.add_argument('--finish', action='store_true', help='Capture end values after unlocking; does not finish the app session')
    a = p.parse_args()
    a.output.mkdir(parents=True, exist_ok=True)
    prefix = [a.adb, '-s', a.serial]

    def shell(*args):
        return subprocess.check_output(prefix+['shell', *args], timeout=20).decode()

    model = shell('getprop', 'ro.product.model').strip()
    if not re.fullmatch(r'SM-S91[168][A-Za-z0-9/]*', model) or shell('getprop', 'ro.kernel.qemu').strip() == '1':
        raise SystemExit('This test requires a physical S23/S23+/Ultra.')
    installed_path = shell('pm', 'path', 'app.rwun').strip().removeprefix('package:')
    if '\n' in installed_path or not installed_path.startswith('/'):
        raise SystemExit('Expected one installed APK; install the kit before starting a recording.')
    installed = subprocess.check_output(prefix+['exec-out', 'cat', installed_path], timeout=60)
    digest = hashlib.sha256(a.apk.read_bytes()).hexdigest()
    if hashlib.sha256(installed).hexdigest() != digest:
        raise SystemExit('Installed APK differs from the candidate. Save your session before updating.')

    def endpoint(name):
        raw = subprocess.check_output(prefix+['exec-out', 'uiautomator', 'dump', '/dev/tty'], timeout=45).decode()
        xml = raw[raw.index('<?xml'):raw.index('</hierarchy>')+len('</hierarchy>')]
        values = parse_recording(xml)
        values.update(host_monotonic=time.monotonic(), boot=shell('settings', 'get', 'global', 'boot_count').strip())
        (a.output/(name+'.xml')).write_text(xml)
        (a.output/(name+'.png')).write_bytes(subprocess.check_output(prefix+['exec-out', 'screencap', '-p'], timeout=30))
        if values['paused'] or values['partial']:
            raise ValueError('Use a fresh, unpaused walk for this uninterrupted test.')
        return values

    target = a.output/'locked-walk.json'
    if a.finish:
        result = json.loads(target.read_text())
        if result.get('status') != 'awaiting-endpoint' or result.get('apk_sha256') != digest or result.get('model') != model:
            raise SystemExit('No matching completed collection is awaiting end values.')
        result['end'] = endpoint('walk-end')
        result['status'] = 'awaiting-review'
        target.write_text(json.dumps(result, indent=2)+'\n')
        print('End values captured. Use notification Finish, review/save once, and complete physical-review.json.')
        return 0
    if a.minutes < 31:
        raise SystemExit('Use at least 31 minutes to collect 30 minutes of observed screen-off time.')
    if target.exists():
        raise SystemExit('Existing evidence found. Archive it or choose a new RWUN_QA_OUTPUT folder.')
    result = {'suite': 's23-locked-walk', 'status': 'collecting', 'emulator': False, 'model': model,
              'apk_sha256': digest, 'android': shell('getprop', 'ro.build.version.release').strip(),
              'build': shell('getprop', 'ro.build.display.id').strip(), 'samples': [], 'start': endpoint('walk-start'),
              'notes': 'USB functional test, not battery/Doze acceptance. UI endpoints + power/service samples; same-session continuity also requires human review.'}
    target.write_text(json.dumps(result, indent=2)+'\n')
    print('Recording confirmed. Put the phone in your pocket and walk. Turning the screen off now.', flush=True)
    shell('input', 'keyevent', '223')
    deadline = time.monotonic()+a.minutes*60
    try:
        while time.monotonic() < deadline:
            time.sleep(min(30, max(0, deadline-time.monotonic())))
            power = shell('dumpsys', 'power')
            services = shell('dumpsys', 'activity', 'services', 'app.rwun')
            sample = {'host_monotonic': time.monotonic(),
                      'screen_asleep': bool(re.search(r'mWakefulness=(Asleep|Dozing)\b', power)),
                      'service_present': 'app.rwun/.IndoorStepService' in services or 'app.rwun.IndoorStepService' in services}
            result['samples'].append(sample)
            print(f"{int(sample['host_monotonic']-result['start']['host_monotonic'])}s: asleep={sample['screen_asleep']}; service={sample['service_present']}", flush=True)
            target.write_text(json.dumps(result, indent=2)+'\n')
        result['status'] = 'awaiting-endpoint'
    except (Exception, KeyboardInterrupt) as error:
        result['status'] = 'failed'
        result['error'] = str(error) or 'Collection interrupted'
    finally:
        target.write_text(json.dumps(result, indent=2)+'\n')
    print('Unlock manually. Leave this same walk recording with steps/timer visible, then run: bash mac-qa.sh s23-finish', flush=True)
    return 1 if result['status'] == 'failed' else 0


if __name__ == '__main__':
    raise SystemExit(main())
