#!/usr/bin/env python3
"""Pin the dex compiler to the Kotlin level used by the local-model runtime."""
from pathlib import Path
import hashlib,json,urllib.request
root=Path(__file__).resolve().parent;row=json.loads((root/'build-tools-lock.json').read_text())['r8'];target=root/'build/tools/r8.jar';target.parent.mkdir(parents=True,exist_ok=True)
if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=row['sha256']:
 part=target.with_suffix('.part');urllib.request.urlretrieve(row['url'],part)
 if hashlib.sha256(part.read_bytes()).hexdigest()!=row['sha256']:part.unlink();raise ValueError('R8 compiler checksum mismatch')
 part.replace(target)
print(target)
