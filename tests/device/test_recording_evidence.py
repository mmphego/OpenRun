import unittest
import xml.etree.ElementTree as ET
from s23_locked_walk import parse_recording


def ui(*labels):
    root = ET.Element('hierarchy')
    for label in labels:
        ET.SubElement(root, 'node', text=label)
    return ET.tostring(root, encoding='unicode')


class RecordingEvidenceTest(unittest.TestCase):
    def test_calibrated_recording_values_from_ui_tree(self):
        result = parse_recording(ui('Your walking-pad walk', 'PHONE STEPS · NO GPS',
                                    '2400 detected steps · personal calibration', '00:30:00',
                                    '~1.68', 'Recording time · 30 minute goal reached'))
        self.assertEqual(2400, result['steps'])
        self.assertEqual(1800, result['seconds'])
        self.assertEqual(1.68, result['estimate_km'])
        self.assertTrue(result['personally_calibrated'])

    def test_paused_and_partial_must_be_preserved(self):
        result = parse_recording(ui('Your walking-pad walk', 'PHONE STEPS · NO GPS',
                                    '200 detected steps · starter estimate · 0.70 m per step · may be partial',
                                    '00:03:00', '~0.14', 'Paused · 30 minute goal'))
        self.assertTrue(result['paused'])
        self.assertTrue(result['partial'])

    def test_outdoor_or_obscured_ui_cannot_supply_indoor_evidence(self):
        with self.assertRaises(ValueError):
            parse_recording(ui('Your walk', 'GPS ±10 m', '00:30:00', '0.00'))
        with self.assertRaises(ValueError):
            parse_recording(ui('Your walking-pad walk', 'PHONE STEPS · NO GPS', '00:30:00'))


if __name__ == '__main__':
    unittest.main()
