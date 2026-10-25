"""Execute the CI launcher with SDK doubles; real device checks run in PR CI."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
FAKE = '''#!/usr/bin/env python3
import os, sys, time
from pathlib import Path
name = Path(sys.argv[0]).name
args = sys.argv[1:]
root = Path(os.environ['RUNNER_TEMP'])
with (root/'calls').open('a') as f: f.write(name+' '+' '.join(args)+'\\n')
if name == 'sdkmanager': sys.exit(0)
if name == 'avdmanager':
    assert os.environ['ANDROID_EMULATOR_HOME'] == os.environ['ANDROID_USER_HOME']
    if os.environ['CASE'] != 'missing':
        (Path(os.environ['ANDROID_AVD_HOME'])/'openrun-upgrade.ini').write_text('path=fixture')
    sys.exit(0)
if name == 'emulator':
    if '-list-avds' in args:
        print('openrun-upgrade'); sys.exit(0)
    if os.environ['CASE'] == 'crash':
        print('simulated emulator startup failure', flush=True); sys.exit(1)
    time.sleep(60)
if name == 'adb':
    if 'get-state' in args: sys.exit(1)
    if 'getprop' in args:
        if os.environ['CASE'] != 'crash': print('1')
    if 'instrument' in args:
        print('INSTRUMENTATION_RESULT: upgradeCheck='+('failed' if os.environ['CASE']=='instrument' else 'passed'))
'''

class EmulatorLauncherTest(unittest.TestCase):
    def run_case(self, case):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tools = root/'bin'; tools.mkdir()
            for name in ('adb', 'avdmanager', 'sdkmanager', 'emulator'):
                p = tools/name; p.write_text(FAKE); p.chmod(0o755)
            sdk = root/'sdk/emulator'; sdk.mkdir(parents=True)
            (sdk/'emulator').symlink_to(tools/'emulator')
            (root/'release-assets').mkdir()
            (root/'release-assets/candidate.apk').touch()
            env = dict(os.environ, PATH=str(tools)+os.pathsep+os.environ['PATH'],
                       GITHUB_ACTIONS='true', ANDROID_HOME=str(root/'sdk'),
                       RUNNER_TEMP=temp, CASE=case)
            result = subprocess.run(['bash', str(ROOT/'scripts/test-apk-upgrade.sh')],
                                    cwd=root, env=env, capture_output=True, text=True, timeout=15)
            return result, (root/'calls').read_text()

    def test_shared_avd_home_and_in_place_install(self):
        result, calls = self.run_case('success')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('install -r release-assets/candidate.apk', calls)
        self.assertIn('upgradePhase verify', calls)
        self.assertNotIn('uninstall', calls)

    def test_missing_avd_fails_before_launch(self):
        result, calls = self.run_case('missing')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('emulator -avd', calls)

    def test_emulator_crash_reports_original_error(self):
        result, calls = self.run_case('crash')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('simulated emulator startup failure', result.stdout)
        self.assertNotIn('install -r', calls)

    def test_instrumentation_failure_blocks_upgrade(self):
        result, calls = self.run_case('instrument')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('upgradePhase verify', calls)
