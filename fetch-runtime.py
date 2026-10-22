#!/usr/bin/env python3
"""Fetch checksum-pinned native runtime dependencies for the standalone builder."""
from pathlib import Path
import hashlib,json,urllib.request,zipfile,concurrent.futures
root=Path(__file__).resolve().parent;cache=root/'build/runtime-deps';cache.mkdir(parents=True,exist_ok=True)
licenses=root/'app/src/main/assets/licenses';licenses.mkdir(parents=True,exist_ok=True)
def fetch(row):
 file=cache/row['file']
 if not file.exists() or hashlib.sha256(file.read_bytes()).hexdigest()!=row['sha256']:
  part=file.with_suffix('.part');urllib.request.urlretrieve(row['url'],part)
  if hashlib.sha256(part.read_bytes()).hexdigest()!=row['sha256']:part.unlink();raise ValueError('Runtime checksum mismatch: '+row['file'])
  part.replace(file)
 if file.suffix=='.aar':
  with zipfile.ZipFile(file) as z:
   (cache/'litertlm.jar').write_bytes(z.read('classes.jar'))
   for item in z.namelist():
    if item.startswith('jni/') and item.endswith('.so'):
     target=cache/'native'/item.replace('jni/','lib/',1);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(item))
   (licenses/'LiteRT-LM-LICENSE.txt').write_bytes(z.read('LICENSE'))
   (licenses/'LiteRT-LM-THIRD_PARTY_NOTICE.txt').write_bytes(z.read('THIRD_PARTY_NOTICE.txt'))
 if file.suffix=='.jar':
  with zipfile.ZipFile(file) as z:
   for name in z.namelist():
    if name.upper().startswith('META-INF/') and any(word in name.upper() for word in ['LICENSE','NOTICE','COPYRIGHT']) and not name.endswith('/'):
     (licenses/(file.stem+'-'+name.replace('/','-'))).write_bytes(z.read(name))
with concurrent.futures.ThreadPoolExecutor(6) as pool:list(pool.map(fetch,json.loads((root/'runtime-lock.json').read_text())))
