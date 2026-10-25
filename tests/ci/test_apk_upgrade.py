import importlib.util
from pathlib import Path
import unittest

path=Path(__file__).resolve().parents[2]/'scripts/verify-apk-upgrade.py'
spec=importlib.util.spec_from_file_location('upgrade_guard',path)
guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard)


class ApkUpgradeTest(unittest.TestCase):
    def previous(self):
        return {'package':'app.rwun','version_code':100048,'certificates':['a'*64]}

    def test_normal_upgrade_allowed(self):
        old=self.previous();new=dict(old,version_code=100049)
        self.assertTrue(guard.verify(old,new)['compatible_identity'])

    def test_changed_key_rejected_even_with_higher_version(self):
        old=self.previous();new=dict(old,version_code=100049,certificates=['b'*64])
        with self.assertRaisesRegex(ValueError,'do not ask users to uninstall'):guard.verify(old,new)

    def test_changed_package_rejected(self):
        old=self.previous();new=dict(old,version_code=100049,package='app.openrun')
        with self.assertRaisesRegex(ValueError,'Package identity'):guard.verify(old,new)

    def test_equal_or_lower_version_rejected(self):
        for code in (100048,100047):
            with self.assertRaisesRegex(ValueError,'must increase'):guard.verify(self.previous(),dict(self.previous(),version_code=code))
