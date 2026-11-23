#!/usr/bin/env python3
"""Save synthetic plans and walks on a fresh, disposable Android emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re

from ui_smoke import Device


PACKAGE = 'app.rwun'


class JourneyDevice(Device):
    def find(self, label, root, include_disabled=False):
        targets = super().find(label, root, include_disabled)
        if targets:
            return targets
        # Platform dialogs may expose the transformed caption in their UI tree.
        return super().find(label.upper(), root, include_disabled)

    def page_nodes(self):
        previous = None
        for _ in range(10):
            xml, root = self.tree()
            if xml == previous:
                break
            yield root.iter('node')
            scroll = next((node for node in root.iter('node') if node.get('scrollable') == 'true'), None)
            if scroll is None:
                break
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', scroll.get('bounds', '')))
            x = str((x1 + x2) // 2)
            self.run('shell', 'input', 'swipe', x, str((y1 + 3 * y2) // 4),
                     x, str((3 * y1 + y2) // 4), '400')
            previous = xml


def has_planned_workout(device):
    return any(node.get('text', '').startswith('Planned · ')
               for page in device.page_nodes() for node in page)


def create_plan(device):
    device.tap('Create my plan')
    device.tap('Run a specific distance')
    device.tap('10 km')
    device.tap('Continue')
    device.assert_label('3 days per week')
    device.tap('Preview my plan')
    device.capture('plan-preview')
    device.tap('Create my plan')
    device.tap('Create')
    device.tap('Calendar')
    device.capture('calendar-after-create')
    if not has_planned_workout(device):
        device.tap('Calendar')  # Reset scroll position while keeping the viewed week.
        device.tap('Next calendar week')
        device.capture('calendar-next-week')
        if not has_planned_workout(device):
            raise AssertionError('No planned workouts in the opening calendar views')


def open_schedule(device):
    device.tap('Plan')
    device.tap('Manage plan')
    device.tap('Edit plan')
    device.assert_label('3 days per week')


def edit_schedule(device):
    open_schedule(device)
    device.tap('Monday')
    device.assert_label('4 days per week')
    device.tap('Review schedule')
    device.tap('Keep editing')
    device.tap('Back')
    open_schedule(device)
    device.tap('Monday')
    device.tap('Review schedule')
    device.capture('schedule-review')
    dates = []
    for page in device.page_nodes():
        for node in page:
            match = re.fullmatch(r'([A-Za-z]{3}) (\d{1,2} [A-Za-z]{3}) · .*', node.get('text', ''))
            if match:
                date = f'{match[1]}, {match[2]}'
                if date not in dates:
                    dates.append(date)
    if len(dates) != 4:
        raise AssertionError(f'Expected four dated workouts in the schedule review, found {len(dates)}')
    device.tap('Save changes')
    device.tap('View in Calendar')
    device.capture('calendar-after-edit')
    for date in dates:
        device.assert_label(date)
    device.tap('Plan')
    device.tap('Manage plan')
    device.tap('Plan changes')
    device.tap('Review undo of latest change')
    device.tap('Apply undo')
    device.tap('Back')
    open_schedule(device)
    device.capture('schedule-after-undo')
    device.tap('Back')


def begin_outdoor_walk(device):
    device.tap('Home')
    device.tap('Start free walk')
    device.tap('Outdoors · GPS · open distance')


def record_walk(device, api_level):
    # A denied system prompt must explain that it did not start recording.
    begin_outdoor_walk(device)
    device.tap("Don't allow")
    device.assert_label('Precise location is needed', device.capture('location-denied'))
    device.tap('Not now')
    for permission in ('ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION'):
        device.run('shell', 'pm', 'grant', PACKAGE, f'android.permission.{permission}')
    if api_level >= 33:
        device.run('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    device.run('shell', 'cmd', 'location', 'set-location-enabled', 'true')
    begin_outdoor_walk(device)
    device.tap('Pause walk')
    device.tap('Resume walk')
    device.capture('walk-recording')
    device.tap('Finish and save')
    device.tap('Save')
    device.assert_label('Walk saved', device.capture('walk-saved'))
    device.tap('Done')
    device.assert_label('1 walks · 1 active days')
    device.assert_label('Correct walking distance', device.capture('walk-progress'))
    # Kill only the newly installed test app after its recorder has checkpointed.
    begin_outdoor_walk(device)
    device.tap('Pause walk')
    device.run('shell', 'am', 'force-stop', PACKAGE)
    launch(device)
    device.tap('Return to your session')
    device.assert_label('Recover recorded portion', device.capture('walk-interrupted'))
    device.tap('Recover recorded portion')
    device.tap('Save')
    device.tap('Done')
    device.assert_label('2 walks · 1 active days')


def launch(device):
    device.run('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default=os.environ.get('ADB', 'adb'))
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--compact', action='store_true', help='Use a 320 dp viewport and 200%% text')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    device = JourneyDevice(args.adb, args.serial, args.output)
    result = {'status': 'failed', 'compact': args.compact, 'journeys': []}
    installed = False
    try:
        if device.run('shell', 'getprop', 'ro.kernel.qemu').decode().strip() != '1':
            raise RuntimeError('This suite requires a disposable emulator.')
        packages = device.run('shell', 'pm', 'list', 'packages', PACKAGE).decode().splitlines()
        if f'package:{PACKAGE}' in packages:
            raise RuntimeError('Use a fresh emulator with no existing OpenRun installation.')
        api = int(device.run('shell', 'getprop', 'ro.build.version.sdk').decode())
        if api < 30:
            raise RuntimeError('Use an API 30 or newer emulator in English.')
        if args.compact:
            device.run('shell', 'wm', 'size', '640x1280')
            device.run('shell', 'wm', 'density', '320')
            device.run('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
        result['apk_sha256'] = hashlib.sha256(args.apk.read_bytes()).hexdigest()
        result['api'] = api
        device.run('install', str(args.apk), timeout=180)
        installed = True
        launch(device)
        device.capture('welcome')
        for journey in (create_plan, edit_schedule):
            journey(device)
            result['journeys'].append(journey.__name__)
        record_walk(device, api)
        result['journeys'].append('record_walk')
        result['status'] = 'passed'
    except Exception as exc:
        result['error'] = str(exc)
        if installed:
            try:
                device.capture('failure')
            except Exception:
                pass
    (args.output / 'journeys.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    return result['status'] != 'passed'


if __name__ == '__main__':
    raise SystemExit(main())
