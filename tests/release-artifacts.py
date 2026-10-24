#!/usr/bin/env python3
"""Check publication failures without an Android SDK or signing credentials."""
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest import mock
import zipfile

api = runpy.run_path(str(Path(__file__).resolve().parents[1] / "scripts/ci/artifacts.py"))


class PublicationTest(unittest.TestCase):
    def test_valid_versions(self):
        for version in ("0.1.0-alpha5", "0.1.0-beta.1", "1.0.0"):
            self.assertEqual(api["validate_version"](version, "10006"), 10006)

    def test_unsafe_or_invalid_version(self):
        for version in ("", "../1.0.0", "v1.0.0", "1.0.0\nasset_name=other", "1.0.0;id"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                api["validate_version"](version, "10006")

    def test_invalid_codes(self):
        for code in ("0", "-1", "1.5", "2100000001", "10\nasset_name=other"):
            with self.subTest(code=code), self.assertRaises(ValueError):
                api["validate_version"]("0.1.0-alpha5", code)

    def test_notes_are_one_version(self):
        text = "# Changes\n\n## 0.1.0\n\n- New version.\n\n## 0.0.9\n\n- Older.\n"
        self.assertEqual(api["release_notes"]("0.1.0", text), "- New version.\n")

    def test_missing_or_empty_notes(self):
        for text in ("## 0.0.9\n\n- Older.\n", "## 0.1.0\n\n## 0.0.9\n\n- Older.\n"):
            with self.assertRaises(ValueError):
                api["release_notes"]("0.1.0", text)

    def test_submission_username_normalization(self):
        prefix = api["github_prefix"]("ChorusFruit-233")
        self.assertEqual(prefix, "io.github.chorusfruit_233.")
        self.assertFalse("io.github.chorusfruit233.colorduo".startswith(prefix))
        self.assertTrue("io.github.chorusfruit_233.colorduo".startswith(prefix))

    def check_module(self, prop=None, entry="io.github.yixing233.hyperduo.HyperDuoModule\n",
                     scope="com.android.systemui\n"):
        if prop is None:
            prop = "minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\n"
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "module.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("META-INF/xposed/module.prop", prop)
                archive.writestr("META-INF/xposed/java_init.list", entry)
                archive.writestr("META-INF/xposed/scope.list", scope)
            return api["module_metadata"](apk)

    def test_modern_xposed_metadata(self):
        self.assertEqual(self.check_module()["properties"]["minApiVersion"], "102")

    def test_missing_xposed_api(self):
        for prop in ("minApiVersion=102\n", "minApiVersion=0\ntargetApiVersion=102\n",
                     "minApiVersion=102\ntargetApiVersion=101\n"):
            with self.subTest(prop=prop), self.assertRaises(ValueError):
                self.check_module(prop=prop)

    def test_missing_entry_or_wrong_scope(self):
        with self.assertRaises(ValueError):
            self.check_module(entry="")
        with self.assertRaises(ValueError):
            self.check_module(scope="android\n")

    def verify(self, badging, signature):
        metadata = {"applicationId": "io.github.chorusfruit233.colorduo",
                    "versionName": "0.1.0-alpha5", "versionCode": 10005}
        with mock.patch("subprocess.check_output", side_effect=[badging, signature]):
            return api["verify_apk"](Path("unused.apk"), "release", metadata, Path("unused-sdk"))

    def test_reject_development_signed_release(self):
        badging = "package: name='io.github.chorusfruit233.colorduo' versionCode='10005' versionName='0.1.0-alpha5'\n"
        with self.assertRaisesRegex(ValueError, "development signing key"):
            self.verify(badging, "Signer #1 certificate SHA-256 digest: " + api["DEVELOPMENT_CERT"])

    def test_reject_debuggable_release(self):
        badging = "package: name='io.github.chorusfruit233.colorduo' versionCode='10005' versionName='0.1.0-alpha5'\napplication-debuggable\n"
        with self.assertRaisesRegex(ValueError, "debuggable"):
            self.verify(badging, "Signer #1 certificate SHA-256 digest: " + "a" * 64)

    def test_reject_wrong_apk_version(self):
        badging = "package: name='io.github.chorusfruit233.colorduo' versionCode='10004' versionName='0.1.0-alpha4'\n"
        with self.assertRaisesRegex(ValueError, "differs from Gradle"):
            self.verify(badging, "")

    def test_signature_verification_failure_propagates(self):
        badging = "package: name='io.github.chorusfruit233.colorduo' versionCode='10005' versionName='0.1.0-alpha5'\n"
        failure = subprocess.CalledProcessError(1, ["apksigner", "verify"])
        with self.assertRaises(subprocess.CalledProcessError):
            self.verify(badging, failure)


if __name__ == "__main__":
    unittest.main()
