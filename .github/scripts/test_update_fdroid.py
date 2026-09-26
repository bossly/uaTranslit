#!/usr/bin/env python3
import os
import tempfile
import unittest
from pathlib import Path

from update_fdroid import (
    resolve_version_info,
    update_metadata_content,
)

SAMPLE_METADATA = """Categories:
  - Internet
  - Translation & Dictionary
License: MIT
AuthorName: Oleg Baidalka
SourceCode: https://github.com/bossly/uaTranslit
IssueTracker: https://github.com/bossly/uaTranslit/issues

AutoName: Трансліт

RepoType: git
Repo: https://github.com/bossly/uaTranslit.git

Builds:
  - versionName: 1.5.1
    versionCode: 38
    commit: 25c36fd5c4acc89ac1fddc258cddface86cda0bd
    subdir: app
    gradle:
      - yes

  - versionName: 1.6.0
    versionCode: 39
    commit: a9a5d2bd6ebfd3b9e5b1fbfdc38323acf5aed61d
    subdir: app
    gradle:
      - yes
    prebuild: sed -i -e '/agp/s/8.12.2/8.11.1/' ../gradle/libs.versions.toml

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.6.0
CurrentVersionCode: 39
"""


class TestUpdateFdroid(unittest.TestCase):
    def test_update_metadata_new_version(self):
        new_content, modified = update_metadata_content(
            content=SAMPLE_METADATA,
            version_name="2.0.0",
            version_code="43",
            commit_ref="2.0.0",
        )
        self.assertTrue(modified)
        self.assertIn("versionName: 2.0.0", new_content)
        self.assertIn("versionCode: 43", new_content)
        self.assertIn("commit: 2.0.0", new_content)
        self.assertIn("CurrentVersion: 2.0.0", new_content)
        self.assertIn("CurrentVersionCode: 43", new_content)

        # Make sure previous builds are preserved
        self.assertIn("versionName: 1.6.0", new_content)
        self.assertIn("versionCode: 39", new_content)
        self.assertIn("AutoUpdateMode: Version", new_content)

    def test_update_metadata_idempotent(self):
        # Update once
        new_content, modified1 = update_metadata_content(
            content=SAMPLE_METADATA,
            version_name="2.0.0",
            version_code="43",
            commit_ref="2.0.0",
        )
        self.assertTrue(modified1)

        # Update again with identical version
        second_content, modified2 = update_metadata_content(
            content=new_content,
            version_name="2.0.0",
            version_code="43",
            commit_ref="2.0.0",
        )
        self.assertFalse(modified2)
        self.assertEqual(new_content, second_content)

    def test_resolve_version_info_from_args(self):
        vn, vc, ref = resolve_version_info(
            version_name_arg="2.1.0",
            version_code_arg="44",
            commit_arg="v2.1.0",
        )
        self.assertEqual(vn, "2.1.0")
        self.assertEqual(vc, "44")
        self.assertEqual(ref, "v2.1.0")

    def test_resolve_version_info_from_gradle_properties(self):
        # When args and env are empty, should read from gradle.properties
        orig_vn = os.environ.pop("APP_VERSION_NAME", None)
        orig_vc = os.environ.pop("APP_VERSION_CODE", None)
        try:
            vn, vc, ref = resolve_version_info()
            self.assertEqual(vn, "2.0.0")
            self.assertEqual(vc, "43")
        finally:
            if orig_vn is not None:
                os.environ["APP_VERSION_NAME"] = orig_vn
            if orig_vc is not None:
                os.environ["APP_VERSION_CODE"] = orig_vc


    def test_dry_run_cli(self):
        import subprocess
        import sys
        with tempfile.NamedTemporaryFile("w", delete=False, suffix=".yml") as f:
            f.write(SAMPLE_METADATA)
            tmp_path = f.name
        try:
            cmd = [
                sys.executable,
                str(Path(__file__).parent / "update_fdroid.py"),
                "--dry-run",
                "--metadata-file", tmp_path,
                "--version-name", "2.0.0",
                "--version-code", "43",
                "--commit", "2.0.0",
            ]
            res = subprocess.run(cmd, capture_output=True, text=True)
            self.assertEqual(res.returncode, 0)
            self.assertIn("Metadata modified: True", res.stdout)
            self.assertIn("CurrentVersion: 2.0.0", res.stdout)
            self.assertIn("CurrentVersionCode: 43", res.stdout)
        finally:
            Path(tmp_path).unlink()


if __name__ == "__main__":
    unittest.main()
