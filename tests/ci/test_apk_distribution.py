"""Exercise the packaging control flow without SDK, secrets or a device."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/package-release-apk.sh'


class ApkDistributionTest(unittest.TestCase):
    def package(self, secrets=0, fail=False, mismatch=False, pin=True):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for variant in ('debug', 'release'):
                output = root / f'app/build/outputs/apk/{variant}'
                output.mkdir(parents=True)
                (output / f'app-{variant}.apk').write_bytes(b'synthetic APK')
                (output / 'output-metadata.json').write_text(json.dumps({'elements': [
                    {'versionName': '1.7.0', 'versionCode': 100037}]}))
            tools = root / 'sdk/build-tools/35.0.0'
            tools.mkdir(parents=True)
            for name in ('apksigner', 'zipalign'):
                path = tools / name
                path.write_text('#!/bin/sh\necho \"Signer #1 certificate SHA-256 digest: ' + 'a'*64 + '\"\nexit 0\n')
                path.chmod(0o700)
            gradle = root / 'gradlew'
            gradle.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> gradle-args\nexit ' + ('1' if fail else '0') + '\n')
            gradle.chmod(0o700)
            env = dict(os.environ, ANDROID_HOME=str(root/'sdk'), RUNNER_TEMP=temp,
                       RWUN_RELEASE_VERSION='v1.7.0', OPENRUN_VERSION_CODE='100037',
                       GITHUB_OUTPUT=str(root/'outputs'), EXPECTED_SIGNING_SHA256=('b' if mismatch else 'a')*64 if pin else '')
            keys = ['KEYSTORE_BASE64', 'RWUN_KEYSTORE_PASSWORD', 'RWUN_KEY_ALIAS', 'RWUN_KEY_PASSWORD']
            for i, key in enumerate(keys):
                env[key] = ('dGVzdA==' if i == 0 else 'synthetic') if i < secrets else ''
            result = subprocess.run(['bash', str(SCRIPT)], cwd=root, env=env, capture_output=True, text=True)
            if secrets == 4 and not fail and not mismatch and pin:
                self.assertEqual(result.returncode, 0, result.stderr)
                variant = 'release'
                name = f'OpenRun-1.7.0-100037-{variant}.apk'
                self.assertTrue((root/'release-assets'/name).exists())
                subprocess.run(['sha256sum', '-c', name+'.sha256'], cwd=root/'release-assets', check=True, capture_output=True)
                notes = subprocess.check_output(['python3', str(ROOT/'scripts/release-notes.py'), 'release-assets', variant], cwd=root, text=True)
                self.assertIn(name, notes)
                self.assertIn(f'{variant}-signed', notes)
                self.assertIn(f'assemble{variant.title()}', (root/'gradle-args').read_text())
            else:
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((root/'release-assets').exists())
            self.assertFalse((root/'openrun-release.keystore').exists())

    def test_no_secrets_never_publishes_a_random_key(self):
        self.package()

    def test_complete_secrets_select_release(self):
        self.package(4)

    def test_partial_configuration_rejected(self):
        for count in (1, 2, 3):
            self.package(count)

    def test_signing_failure_has_no_fallback(self):
        self.package(4, fail=True)

    def test_changed_certificate_rejected(self):
        self.package(4, mismatch=True)

    def test_unpinned_certificate_rejected(self):
        self.package(4, pin=False)


if __name__ == '__main__':
    unittest.main()
