"""Synthetic fixtures test the gate, never certify an Android device."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from release_gate import evaluate, REQUIRED_UI, REQUIRED_PHYSICAL
from package_candidate import package


class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root/'candidate.apk'
        self.apk.write_bytes(b'synthetic gate test only')
        self.digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.ui = {'suite': 'emulator-ui', 'status': 'passed', 'emulator': True,
                   'apk_sha256': self.digest, 'checks': sorted(REQUIRED_UI)}
        self.walk = {'suite': 's23-locked-walk', 'status': 'awaiting-review', 'emulator': False,
                     'model': 'SM-S911B', 'apk_sha256': self.digest,
                     'samples': [{'host_monotonic': i*30, 'seconds': i*30, 'steps': i*45,
                                  'session': 'synthetic', 'boot': 1, 'paused': False, 'partial': False,
                                  'step_length_m': .7, 'screen_asleep': True, 'service_present': True}
                                 for i in range(64)]}
        self.walk['start'] = dict(self.walk['samples'][0], estimate_km=0, personally_calibrated=True)
        self.walk['end'] = dict(self.walk['samples'][-1], estimate_km=1.95, personally_calibrated=True)
        self.review = {'apk_sha256': self.digest, 'reviewed_by': 'Fixture, not a real review',
                       'reviewed_at': '2026-10-21', 'measurements': {'observed_steps': 200, 'detected_steps': 195,
                       'pad_distance_km': 2.0, 'estimated_distance_km': 1.95, 'personally_calibrated': True},
                       'checks': {key: {'status': 'passed', 'notes': 'Synthetic fixture only',
                                  'evidence': ['observation.txt']} for key in REQUIRED_PHYSICAL}}
        (self.root/'observation.txt').write_text('Synthetic fixture, not device evidence')

    def write(self):
        for name, obj in [('result.json', self.ui), ('locked-walk.json', self.walk), ('physical-review.json', self.review)]:
            (self.root/name).write_text(json.dumps(obj))

    def blocked(self):
        self.write()
        self.assertEqual('blocked', evaluate(self.apk, self.root)['recording_release_gate'])

    def test_complete_synthetic_evidence_satisfies_gate_logic(self):
        self.write()
        self.assertEqual([], evaluate(self.apk, self.root)['blockers'])

    def test_missing_evidence_blocks(self):
        self.assertEqual('blocked', evaluate(self.apk, self.root)['recording_release_gate'])

    def test_different_apk_invalidates_evidence(self):
        self.apk.write_bytes(b'changed build')
        self.blocked()

    def test_blocked_ui_does_not_pass(self):
        self.ui['status'] = 'blocked'
        self.blocked()

    def test_emulator_cannot_satisfy_physical_requirement(self):
        self.walk['emulator'] = True
        self.blocked()

    def test_missing_screen_off_interval_blocks(self):
        self.walk['samples'][30]['screen_asleep'] = False
        self.walk['samples'][31]['screen_asleep'] = False
        self.walk['samples'][32]['screen_asleep'] = False
        self.blocked()

    def test_service_loss_blocks(self):
        self.walk['samples'][30]['service_present'] = False
        self.blocked()

    def test_timer_stall_blocks(self):
        self.walk['end']['seconds'] -= 60
        self.blocked()

    def test_no_steps_blocks(self):
        for s in self.walk['samples']:
            s['steps'] = 0
        self.walk['end']['steps'] = 0
        self.blocked()

    def test_failed_manual_check_blocks(self):
        self.review['checks']['notification-finish']['status'] = 'failed'
        self.blocked()

    def test_unknown_review_or_missing_attachment_blocks(self):
        self.review['reviewed_by'] = ''
        (self.root/'observation.txt').unlink()
        self.blocked()

    def test_inaccurate_sensor_measurement_blocks(self):
        self.review['measurements']['detected_steps'] = 150
        self.blocked()

    def test_inaccurate_distance_measurement_blocks(self):
        self.review['measurements']['estimated_distance_km'] = 1
        self.blocked()

    def test_blocked_gate_cannot_create_candidate_archive(self):
        target = self.root/'candidate.zip'
        with self.assertRaises(ValueError):
            package(self.apk, self.root, target)
        self.assertFalse(target.exists())

    def test_new_build_cannot_reuse_previously_passing_gate(self):
        self.write()
        self.apk.write_bytes(b'new recording implementation')
        with self.assertRaises(ValueError):
            package(self.apk, self.root, self.root/'candidate.zip')

    def test_nonfinite_measurements_cannot_bypass_gate(self):
        self.walk['samples'][20]['host_monotonic'] = float('nan')
        self.blocked()

    def test_complete_gate_package_retains_review_attachments(self):
        self.write()
        target = package(self.apk, self.root, self.root/'candidate.zip')
        with zipfile.ZipFile(target) as archive:
            self.assertIn('evidence/observation.txt', archive.namelist())
        with self.assertRaises(ValueError):
            package(self.apk, self.root, target)


if __name__ == '__main__':
    unittest.main()
