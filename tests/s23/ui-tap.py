#!/usr/bin/env python3
"""Tap exact text/content description using a freshly dumped UI tree, never screenshot coordinates."""
import os,re,subprocess,sys,xml.etree.ElementTree as ET
adb=os.environ.get('ADB','adb'); serial=os.environ['ANDROID_SERIAL']; label=sys.argv[1]
raw=subprocess.check_output([adb,'-s',serial,'exec-out','uiautomator','dump','/dev/tty']).decode()
start=raw.index('<?xml'); end=raw.index('</hierarchy>')+len('</hierarchy>'); root=ET.fromstring(raw[start:end])
nodes=[n for n in root.iter('node') if label in (n.get('text'),n.get('content-desc')) and n.get('enabled')=='true']
if len(nodes)!=1: sys.exit('Target missing or ambiguous. Scroll if needed, then re-dump and retry; do not guess coordinates.')
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds','')))
subprocess.run([adb,'-s',serial,'shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
