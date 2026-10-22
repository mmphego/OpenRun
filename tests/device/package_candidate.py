#!/usr/bin/env python3
"""Create a recording-QA-cleared package only after the exact APK passes its gate."""
import argparse
import json
from pathlib import Path
import zipfile
from release_gate import evaluate


def package(apk, evidence, output):
    result = evaluate(apk, evidence)
    if result['recording_release_gate'] != 'passed':
        raise ValueError('Recording/UI gate blocked: '+'; '.join(result['blockers']))
    output = output.resolve()
    if output.exists():
        raise ValueError('Choose a new output path; existing packages are never overwritten.')
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive create: a failed/blocked attempt cannot replace an earlier package.
    with output.open('xb') as stream, zipfile.ZipFile(stream, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(apk, apk.name)
        z.writestr('recording-gate.json', json.dumps(result, indent=2)+'\n')
        for name in ('result.json', 'locked-walk.json', 'physical-review.json'):
            z.write(evidence/name, 'evidence/'+name)
        review = json.loads((evidence/'physical-review.json').read_text())
        attachments = {name for check in review['checks'].values() for name in check.get('evidence', [])}
        for name in sorted(attachments-{'result.json', 'locked-walk.json', 'physical-review.json'}):
            z.write(evidence/name, 'evidence/'+name)
    return output


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--evidence', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    try:
        print(package(a.apk, a.evidence, a.output))
    except ValueError as error:
        p.exit(1, str(error)+'\n')


if __name__ == '__main__':
    main()
