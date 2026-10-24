#!/usr/bin/env python3
"""Reproduce host native renders after build-standalone.sh. JDK21 required."""
from pathlib import Path
import os,subprocess,shutil,urllib.parse,zipfile
root=Path(__file__).resolve().parents[2];work=root/'build/native-qa';work.mkdir(parents=True,exist_ok=True)
java_home=os.environ.get('JAVA_HOME');java=str(Path(java_home)/'bin/java') if java_home else shutil.which('java');javac=str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
if not java or not javac:raise SystemExit('Set JAVA_HOME to JDK21')
proxy=[]
if os.environ.get('HTTPS_PROXY'):
 url=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
 for protocol in ('https','http'):proxy += [f'-D{protocol}.proxyHost={url.hostname}',f'-D{protocol}.proxyPort={url.port or 80}']
lib=Path(os.environ.get('RWUN_NATIVE_QA_LIB',work/'lib')).resolve()
if not os.environ.get('RWUN_NATIVE_QA_LIB'):
 (work/'settings.gradle').write_text("rootProject.name='rwun-native-qa'\n")
 shutil.copyfile(root/'tests/native/host-deps.gradle',work/'build.gradle')
 subprocess.run(['bash',str(root/'gradlew'),*proxy,'-p',str(work),'--no-daemon','fetch'],check=True)
for file in lib.glob('*.aar'):
 with zipfile.ZipFile(file) as archive:
  if 'classes.jar' in archive.namelist():file.with_suffix('.jar').write_bytes(archive.read('classes.jar'))
classes=work/'classes';config=classes/'com/android/tools/test_config.properties';config.parent.mkdir(parents=True,exist_ok=True)
config.write_text('\n'.join(f'{key}={value}' for key,value in {
 'android_merged_manifest':root/'build/standalone/AndroidManifest.xml','android_merged_resources':root/'app/src/main/res','android_merged_assets':root/'app/src/main/assets','android_custom_package':'app.rwun','android_resource_apk':root/'build/standalone/resources.apk'}.items())+'\n')
classpath=':'.join(str(x) for x in [*lib.glob('*.jar'),*(root/'build/runtime-deps').glob('*.jar'),root/'build/standalone/classes',classes])
subprocess.run([javac,'-cp',classpath,'-d',str(classes),*[str(f) for f in (root/'tests/native').glob('*.java')]],check=True)
evidence=Path(os.environ.get('RWUN_NATIVE_QA_OUTPUT',work/'evidence')).resolve()
total=0
for name in ['NativePlanningTest','VoiceCoachTest','ProgressUiTest','GpsRecordingTest','IndoorRecordingTest','ReliabilityTest','RaceSyncTest','RaceDiscoveryUiTest']:
 expected=(root/'tests/native'/(name+'.java')).read_text().count('@Test')
 result=subprocess.run([java,*proxy,'-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2',f'-Drwun.root={root}',f'-Drwun.evidence={evidence}','-cp',classpath,'org.junit.runner.JUnitCore','app.rwun.'+name],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 print(result.stdout,flush=True)
 if result.returncode!=0 or f'OK ({expected} tests)' not in result.stdout:
  raise SystemExit(f'{name}: test process did not report all {expected} tests passed (exit {result.returncode})')
 total+=expected
print(f'Verified {total} completed host tests across isolated JVMs.',flush=True)
print('Host native renders:',evidence,'(not emulator/S23 screenshots)')
