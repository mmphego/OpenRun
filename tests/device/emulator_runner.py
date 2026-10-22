#!/usr/bin/env python3
"""Start a locally installed API 36 AOSP image and run UI QA in the same session."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--sdk', type=Path, required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--boot-timeout', type=int, default=300)
    p.add_argument('--software', action='store_true', help='Explicit slow fallback when no hypervisor exists')
    a = p.parse_args()
    sdk, output = a.sdk.resolve(), a.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    adb, emulator = sdk/'platform-tools/adb', sdk/'emulator/emulator'
    abi = 'arm64-v8a' if os.uname().machine in ('arm64', 'aarch64') else 'x86_64'
    image = f'system-images/android-36/default/{abi}/'
    if not (sdk/image/'system.img').exists():
        raise SystemExit(f'Install system-images;android-36;default;{abi} with sdkmanager first.')
    avd_home = output/'avds'
    avd = avd_home/'OpenRunS23Display.avd'
    avd.mkdir(parents=True, exist_ok=True)
    (avd_home/'OpenRunS23Display.ini').write_text(f'avd.ini.encoding=UTF-8\npath={avd}\ntarget=android-36\n')
    (avd/'config.ini').write_text(f'''avd.ini.encoding=UTF-8
AvdId=OpenRunS23Display
avd.ini.displayname=OpenRun AOSP Android 16 S23-sized display
abi.type={abi}
hw.cpu.arch={'arm64' if abi == 'arm64-v8a' else 'x86_64'}
hw.cpu.ncore=2
hw.ramSize=2048
vm.heapSize=256
hw.lcd.width=1080
hw.lcd.height=2340
hw.lcd.density=420
hw.mainKeys=no
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=4G
showDeviceFrame=no
skin.name=1080x2340
image.sysdir.1={image}
tag.id=default
PlayStore.enabled=false
''')
    env = dict(os.environ, ANDROID_SDK_ROOT=str(sdk), ANDROID_AVD_HOME=str(avd_home))
    subprocess.run([str(adb), 'start-server'], check=True, env=env)
    cmd = [str(emulator), '-avd', 'OpenRunS23Display', '-port', '5580', '-no-window', '-no-audio',
           '-no-boot-anim', '-no-snapshot', '-no-metrics', '-gpu', 'swiftshader', '-feature', '-Vulkan']
    if a.software:
        cmd += ['-accel', 'off']
    result = {'suite': 'emulator-ui', 'status': 'blocked', 'emulator': True,
              'apk_sha256': hashlib.sha256(a.apk.read_bytes()).hexdigest(), 'checks': []}
    with (output/'emulator.log').open('w') as log:
        proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, env=env)
        started = time.monotonic()
        try:
            while time.monotonic()-started < a.boot_timeout:
                if proc.poll() is not None:
                    result['error'] = f'Emulator exited before boot: {proc.returncode}'
                    break
                try:
                    boot = subprocess.check_output([str(adb), '-s', 'emulator-5580', 'shell', 'getprop',
                                                    'sys.boot_completed'], timeout=5, stderr=subprocess.DEVNULL)
                    if boot.strip() == b'1':
                        print('Android boot completed; starting UI suite.', flush=True)
                        subprocess.run([str(adb), '-s', 'emulator-5580', 'shell', 'input', 'keyevent', '224'], check=True, timeout=15)
                        subprocess.run([str(adb), '-s', 'emulator-5580', 'shell', 'wm', 'dismiss-keyguard'], check=True, timeout=15)
                        return subprocess.call([sys.executable, str(Path(__file__).with_name('ui_smoke.py')),
                                                '--adb', str(adb), '--serial', 'emulator-5580', '--apk', str(a.apk.resolve()),
                                                '--output', str(output)], env=env)
                except (subprocess.SubprocessError, OSError):
                    pass
                print(f'Waiting for Android boot: {int(time.monotonic()-started)}s', flush=True)
                time.sleep(10)
            result.setdefault('error', f'Android did not complete boot within {a.boot_timeout}s; UI suite not executed.')
            (output/'result.json').write_text(json.dumps(result, indent=2)+'\n')
            print(result['error'], flush=True)
            return 2
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()


if __name__ == '__main__':
    raise SystemExit(main())
