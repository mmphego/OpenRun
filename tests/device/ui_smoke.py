#!/usr/bin/env python3
"""Non-saving UI checks on a disposable AOSP emulator; taps come from UI trees."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


class Device:
    def __init__(self, adb, serial, output):
        self.prefix = [adb, '-s', serial]
        self.output = output
        self.index = 0

    def run(self, *args, timeout=60):
        return subprocess.check_output(self.prefix + list(args), timeout=timeout)

    def tree(self):
        raw = self.run('exec-out', 'uiautomator', 'dump', '/dev/tty').decode()
        xml = raw[raw.index('<?xml'):raw.index('</hierarchy>') + len('</hierarchy>')]
        return xml, ET.fromstring(xml)

    def capture(self, label):
        self.index += 1
        stem = self.output / f'{self.index:02}-{label}'
        xml, root = self.tree()
        stem.with_suffix('.xml').write_text(xml)
        stem.with_suffix('.png').write_bytes(self.run('exec-out', 'screencap', '-p'))
        return root

    def find(self, label, root, include_disabled=False):
        nodes = [n for n in root.iter('node') if (include_disabled or n.get('enabled') == 'true') and
                 (label in (n.get('text'), n.get('content-desc')) or
                  n.get('content-desc', '').startswith(label+', '))]
        clickable = [n for n in nodes if n.get('clickable') == 'true']
        return clickable or nodes

    def assert_label(self, label, root=None):
        if root is not None and self.find(label, root):
            return
        self.reveal(label)

    def reveal(self, label, include_disabled=False):
        # Scroll at least once if missing. Swipe bounds also come from the tree.
        for attempt in range(7):
            _, root = self.tree()
            nodes = self.find(label, root, include_disabled)
            if len(nodes) == 1:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds', '')))
                if x2 > x1 and y2 > y1:
                    return (x1+x2)//2, (y1+y2)//2
            scroll = next((n for n in root.iter('node') if n.get('scrollable') == 'true'), None)
            if scroll is None:
                break
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', scroll.get('bounds', '')))
            self.run('shell', 'input', 'swipe', str((x1+x2)//2), str(y1+(y2-y1)*3//4),
                     str((x1+x2)//2), str(y1+(y2-y1)//4), '400')
        raise AssertionError(f'Missing or ambiguous target after scrolling: {label}')

    def tap(self, label):
        x, y = self.reveal(label)
        self.run('shell', 'input', 'tap', str(x), str(y))
        time.sleep(1)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adb', default=os.environ.get('ADB', 'adb'))
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    device = Device(args.adb, args.serial, args.output)
    result = {'suite': 'emulator-ui', 'status': 'failed', 'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'checks': [], 'scope': 'AOSP UI only; no Samsung sensor or background assurance'}
    original_scale = None
    try:
        is_emulator = device.run('shell', 'getprop', 'ro.kernel.qemu').decode().strip() == '1'
        result['emulator'] = is_emulator
        if not is_emulator:
            raise RuntimeError('Use a disposable emulator for this UI suite; no physical-device settings changed.')
        result['android'] = device.run('shell', 'getprop', 'ro.build.version.release').decode().strip()
        result['fingerprint'] = device.run('shell', 'getprop', 'ro.build.fingerprint').decode().strip()
        original_scale = device.run('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
        device.run('install', '-r', str(args.apk), timeout=180)
        device.run('shell', 'am', 'start', '-W', '-n', 'app.rwun/.MainActivity')
        root = device.capture('welcome')
        device.assert_label('Create my plan', root)
        result['checks'].append('welcome-plan-entry')
        device.tap('Create my plan')
        device.assert_label('What is your goal?', device.capture('goals'))
        device.tap('Race')
        device.assert_label('Find your race', device.capture('race-search'))
        device.tap("Don't see your race?")
        device.assert_label('Add new race')
        device.reveal('Add race', include_disabled=True)
        root = device.capture('manual-race')
        add = [n for n in root.iter('node') if n.get('text') == 'Add race']
        if not add or add[0].get('enabled') != 'false':
            raise AssertionError('Incomplete race must not be addable')
        result['checks'].append('race-search-manual-validation')
        device.run('shell', 'input', 'keyevent', '4')
        device.run('shell', 'input', 'keyevent', '4')
        device.tap('Run a specific distance')
        device.tap('10 km')
        device.assert_label('Start with your actual running', device.capture('fitness-auto-advance'))
        result['checks'].append('distance-choice-auto-advances')
        device.tap('Continue')
        device.capture('availability')
        device.tap('Preview my plan')
        device.assert_label('Create my plan')
        device.capture('preview')
        result['checks'].append('plan-preview-without-saving')
        # Return to the goal list, then check large-font entry and race form.
        for _ in range(4):
            device.run('shell', 'input', 'keyevent', '4')
        device.run('shell', 'settings', 'put', 'system', 'font_scale', '1.6')
        time.sleep(2)
        device.capture('goals-large-font')
        device.tap('Race')
        device.capture('race-large-font')
        device.tap("Don't see your race?")
        device.assert_label('Add new race', device.capture('manual-large-font'))
        result['checks'].append('large-font-navigation')
        for _ in range(3):
            device.run('shell', 'input', 'keyevent', '4')
        device.tap('Walking pad & treadmill')
        device.assert_label('Start indoor session')
        device.capture('indoor-ready')
        result['checks'].append('indoor-entry-without-gps')
        result['status'] = 'passed'
    except Exception as error:
        result['error'] = str(error)
        try:
            device.capture('failure')
        except Exception:
            pass
    finally:
        if original_scale is not None:
            if original_scale == 'null':
                device.run('shell', 'settings', 'delete', 'system', 'font_scale')
            else:
                device.run('shell', 'settings', 'put', 'system', 'font_scale', original_scale)
        try:
            pid = device.run('shell', 'pidof', '-s', 'app.rwun').decode().strip()
            if pid:
                (args.output/'app-logcat.txt').write_bytes(device.run('logcat', '--pid', pid, '-d'))
            (args.output/'crashes.txt').write_bytes(device.run('logcat', '-b', 'crash', '-d'))
        except Exception:
            pass
        (args.output/'result.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
