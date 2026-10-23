#!/usr/bin/env python3
"""Resolve Room's Java processor/runtime for the existing standalone APK build."""
import os,subprocess,urllib.parse,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent
runtime=root/'build/race-runtime';compiler=root/'build/race-compiler'
if not (runtime/'room-runtime-2.6.1.aar').exists() or not (compiler/'room-compiler-2.6.1.jar').exists():
 proxy=[]
 if os.environ.get('HTTPS_PROXY'):
  u=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
  for p in ['https','http']:proxy += [f'-D{p}.proxyHost={u.hostname}',f'-D{p}.proxyPort={u.port}']
 gradle=[os.environ['GRADLE_BIN']] if os.environ.get('GRADLE_BIN') else ['bash',str(root/'gradlew')]
 subprocess.run([*gradle,*proxy,'-p',str(root/'build-support'),'--no-daemon','fetch'],check=True)
for f in runtime.glob('*.aar'):
 with zipfile.ZipFile(f) as z:
  if 'classes.jar' in z.namelist():f.with_suffix('.jar').write_bytes(z.read('classes.jar'))
# LiteRT already brings a newer Kotlin stdlib and JetBrains annotations. Never dex both versions.
for f in runtime.glob('*.jar'):
 if f.name.startswith(('kotlin-','annotations-')):continue
 target=root/'build/runtime-deps'/('race-'+f.name);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(f.read_bytes())
print('Room runtime and annotation processor ready')
