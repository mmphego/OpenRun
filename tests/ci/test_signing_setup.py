"""Real keystore generation against a fake GitHub CLI; never writes remote secrets."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'scripts/configure-signing.sh'


@unittest.skipUnless(shutil.which('keytool') and shutil.which('openssl'), 'JDK and OpenSSL required')
class SigningSetupTest(unittest.TestCase):
    def test_repeated_setup_reuses_private_key_and_pin(self):
        self.run_setup(repeat=True)

    def test_remote_signing_cannot_be_replaced_by_new_local_key(self):
        self.run_setup(remote=True)

    def test_existing_local_key_cannot_overwrite_unpinned_remote_signing(self):
        self.run_setup(legacy=True)

    def test_different_pin_blocks_secret_updates(self):
        self.run_setup(mismatch=True)

    def run_setup(self, repeat=False, remote=False, mismatch=False, legacy=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            cli = root/'gh'
            cli.write_text('''#!/usr/bin/env python3
import hashlib,json,os,sys
from pathlib import Path
args=sys.argv[1:]; state=Path(os.environ['FAKE_GH_STATE'])
data=json.loads(state.read_text()) if state.exists() else {}
if args[:2]==['auth','status']: pass
elif args[:2]==['secret','list']:
    if os.environ.get('FAKE_REMOTE'): print('ANDROID_KEYSTORE_BASE64')
    else:
        for name in data:
            if name.startswith('ANDROID_'): print(name)
elif args[:2]==['variable','list']:
    print('b'*64 if os.environ.get('FAKE_MISMATCH') else data.get('pin',''))
elif args[:2]==['secret','set']:
    value=sys.stdin.buffer.read()
    assert value and not value.endswith(b'\\n') if args[2]!='ANDROID_KEYSTORE_BASE64' else value
    data[args[2]]=hashlib.sha256(value).hexdigest()
elif args[:2]==['variable','set']: data['pin']=args[args.index('--body')+1]
else: raise SystemExit('Unexpected command')
state.write_text(json.dumps(data))
''')
            cli.chmod(0o700)
            env=dict(os.environ, PATH=str(root)+os.pathsep+os.environ['PATH'], OPENRUN_SIGNING_DIR=str(root/'keys'), FAKE_GH_STATE=str(root/'state'))
            if remote: env['FAKE_REMOTE']='1'
            if mismatch: env['FAKE_MISMATCH']='1'
            result=subprocess.run(['bash',str(SCRIPT)],env=env,text=True,capture_output=True)
            state=json.loads((root/'state').read_text())
            if remote or mismatch:
                self.assertNotEqual(0,result.returncode)
                self.assertNotIn('ANDROID_KEYSTORE_BASE64',state)
                if remote: self.assertFalse((root/'keys/openrun-release.keystore').exists())
                return
            self.assertEqual(0,result.returncode,result.stderr)
            self.assertEqual(5,len(state));self.assertEqual(64,len(state['pin']))
            key=(root/'keys/openrun-release.keystore').read_bytes()
            password=(root/'keys/password').read_text().strip()
            self.assertNotIn(password,result.stdout+result.stderr)
            self.assertEqual(0o600,(root/'keys/password').stat().st_mode&0o777)
            if legacy:
                del state['pin'];(root/'state').write_text(json.dumps(state))
                blocked=subprocess.run(['bash',str(SCRIPT)],env=env,text=True,capture_output=True)
                self.assertNotEqual(0,blocked.returncode)
                self.assertIn('no verified certificate pin',blocked.stdout)
                self.assertEqual(state,json.loads((root/'state').read_text()))
            if repeat:
                again=subprocess.run(['bash',str(SCRIPT)],env=env,text=True,capture_output=True)
                self.assertEqual(0,again.returncode,again.stderr)
                self.assertEqual(key,(root/'keys/openrun-release.keystore').read_bytes())
                self.assertEqual(state,json.loads((root/'state').read_text()))
