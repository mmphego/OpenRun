#!/usr/bin/env python3
"""Fail closed unless this exact APK has UI and reviewed physical recording evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import math

REQUIRED_UI = {'welcome-plan-entry', 'race-search-manual-validation', 'distance-choice-auto-advances',
               'plan-preview-without-saving', 'large-font-navigation', 'indoor-entry-without-gps'}
REQUIRED_PHYSICAL = {'pocket-step-accuracy', 'locked-walk-distance', 'notification-finish',
                     'pause-resume', 'single-save-walking-totals', 'interrupted-recovery'}


def evaluate(apk, evidence):
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    blockers = []
    docs = {}
    for key, name in [('ui', 'result.json'), ('walk', 'locked-walk.json'), ('review', 'physical-review.json')]:
        try:
            doc = json.loads((evidence/name).read_text())
            if not isinstance(doc, dict):
                raise ValueError('Expected object')
            docs[key] = doc
            if doc.get('apk_sha256') != digest:
                blockers.append(f'{name}: evidence is missing or belongs to a different APK')
        except (OSError, ValueError):
            blockers.append(f'{name}: missing or invalid evidence')
    ui = docs.get('ui', {})
    if ui.get('suite') != 'emulator-ui' or ui.get('status') != 'passed' or ui.get('emulator') is not True:
        blockers.append('Emulator UI suite has not passed')
    if not REQUIRED_UI.issubset(set(x for x in ui.get('checks', []) if isinstance(x, str))):
        blockers.append('Required UI checks are incomplete')
    walk = docs.get('walk', {})
    if walk.get('suite') != 's23-locked-walk' or walk.get('status') != 'awaiting-review' or walk.get('emulator') is not False:
        blockers.append('Physical locked-walk collection is missing or failed')
    if not re.fullmatch(r'SM-S91[168][A-Za-z0-9/]*', str(walk.get('model', ''))):
        blockers.append('Recording evidence is not from an S23-family device')
    samples = walk.get('samples', [])
    try:
        if len(samples) < 61:
            raise ValueError('Too few samples')
        for sample in samples:
            if not all(isinstance(sample.get(key), (int, float)) and not isinstance(sample.get(key), bool) and
                       math.isfinite(sample[key]) and sample[key] >= 0
                       for key in ('host_monotonic',)):
                raise ValueError('Invalid numeric sample')
            if not all(isinstance(sample.get(key), bool) for key in
                       ('screen_asleep', 'service_present')):
                raise ValueError('Invalid boolean sample')
        first, last = walk['start'], walk['end']
        for endpoint in (first, last):
            if not all(isinstance(endpoint.get(key), (int, float)) and not isinstance(endpoint.get(key), bool) and
                       math.isfinite(endpoint[key]) and endpoint[key] >= 0
                       for key in ('host_monotonic', 'seconds', 'steps', 'estimate_km')):
                raise ValueError('Invalid UI endpoint')
            if not all(isinstance(endpoint.get(key), bool) for key in ('paused', 'partial', 'personally_calibrated')):
                raise ValueError('Invalid UI endpoint flags')
        if not re.fullmatch(r'\d+', str(first['boot'])) or first['boot'] != last['boot']:
            blockers.append('Device rebooted or boot identity is unavailable')
        if first['host_monotonic'] > samples[0]['host_monotonic'] or last['host_monotonic'] < samples[-1]['host_monotonic']:
            blockers.append('UI endpoints do not bracket the locked-screen collection')
        locked = 0
        for previous, current in zip(samples, samples[1:]):
            interval = current['host_monotonic']-previous['host_monotonic']
            if interval <= 0 or interval > 45:
                raise ValueError('Missing or non-monotonic sample interval')
            if current['screen_asleep'] and previous['screen_asleep']:
                locked += interval
        if locked < 1800:
            blockers.append('Less than 30 minutes of sampled screen-off time')
        if any(s['paused'] or s['partial'] for s in (first, last)) or any(not s['service_present'] for s in samples):
            blockers.append('Walk paused, became partial, or its service was absent during sampling')
        if last['steps']-first['steps'] < 200 or last['estimate_km'] <= first['estimate_km']:
            blockers.append('Insufficient walking steps or no distance estimate')
        drift = abs((last['seconds']-first['seconds'])-(last['host_monotonic']-first['host_monotonic']))
        if drift > 30:
            blockers.append('Timer drift exceeds 30 seconds')
    except (KeyError, TypeError, ValueError, IndexError):
        blockers.append('Locked-walk measurements are incomplete or invalid')
    review = docs.get('review', {})
    if not review.get('reviewed_by') or not review.get('reviewed_at'):
        blockers.append('Physical results need a named, dated reviewer')
    try:
        metrics = review['measurements']
        observed, detected = metrics['observed_steps'], metrics['detected_steps']
        machine, estimated = metrics['pad_distance_km'], metrics['estimated_distance_km']
        if not all(isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x)
                   for x in (observed, detected, machine, estimated)):
            raise ValueError('Invalid measurement')
        if observed < 200 or detected < 0 or abs(detected-observed)/observed > .10:
            blockers.append('Observed pocket-step error exceeds 10%, or fewer than 200 observed steps')
        if machine <= 0 or estimated <= 0 or abs(estimated-machine)/machine > .15:
            blockers.append('Walking-pad distance error exceeds 15%, or distance measurements are missing')
        if metrics.get('personally_calibrated') is not True:
            blockers.append('Repeat distance acceptance after personal calibration')
    except (KeyError, TypeError, ValueError, ZeroDivisionError):
        blockers.append('Physical step-count and distance measurements are incomplete')
    for check in REQUIRED_PHYSICAL:
        row = review.get('checks', {}).get(check, {})
        if row.get('status') != 'passed' or not row.get('notes'):
            blockers.append(f'Physical check pending or failed: {check}')
        paths = row.get('evidence', [])
        if not paths:
            blockers.append(f'No evidence for physical check: {check}')
        for path in paths:
            candidate = (evidence/path).resolve()
            if not candidate.is_relative_to(evidence.resolve()) or not candidate.is_file() or candidate.stat().st_size == 0:
                blockers.append(f'Missing or unsafe evidence for physical check: {check}')
    return {'apk_sha256': digest, 'recording_release_gate': 'blocked' if blockers else 'passed',
            'blockers': blockers,
            'scope': 'Recording/UI gate only; does not approve Play policy, production signing, coaching or external integrations.'}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--apk', required=True, type=Path)
    p.add_argument('--evidence', required=True, type=Path)
    p.add_argument('--output', type=Path)
    a = p.parse_args()
    result = evaluate(a.apk, a.evidence)
    text = json.dumps(result, indent=2)+'\n'
    if a.output:
        a.output.write_text(text)
    print(text, end='')
    return 0 if result['recording_release_gate'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
