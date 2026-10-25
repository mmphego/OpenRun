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

class SigningTransitionTest(unittest.TestCase):
    def identities(self):
        return ({'package': 'app.rwun', 'version_code': 100048, 'certificates': ['a'*64]},
                {'package': 'app.rwun', 'version_code': 100060,
                 'certificates': [guard.PERMANENT_CERTIFICATE]})

    def test_exact_legacy_binary_allows_fresh_install(self):
        old, new = self.identities()
        result = guard.release_policy(old, new, guard.LEGACY_APK_SHA256)
        self.assertTrue(result['fresh_install_required'])
        self.assertFalse(result['compatible_identity'])

    def test_unknown_legacy_binary_is_blocked(self):
        old, new = self.identities()
        with self.assertRaises(ValueError):
            guard.release_policy(old, new, 'b'*64)

    def test_future_key_change_blocked_even_if_repository_pin_changes(self):
        old, new = self.identities()
        old = dict(new)
        new = dict(new, version_code=100061, certificates=['c'*64])
        with self.assertRaises(ValueError):
            guard.release_policy(old, new, 'b'*64)

    def test_next_release_requires_normal_upgrade(self):
        _, old = self.identities()
        new = dict(old, version_code=100061)
        self.assertFalse(guard.release_policy(old, new, 'b'*64)['fresh_install_required'])
        with self.assertRaises(ValueError):
            guard.release_policy(old, old, 'b'*64)

    def test_transition_still_rejects_package_change_or_downgrade(self):
        old, new = self.identities()
        for invalid in (dict(new, package='another.app'), dict(new, version_code=100048)):
            with self.assertRaises(ValueError):
                guard.release_policy(old, invalid, guard.LEGACY_APK_SHA256)
