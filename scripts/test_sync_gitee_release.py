"""Offline regression checks for release integrity and resumable Gitee uploads."""

import copy
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import sync_gitee_release as sync


class FakeGitee:
    def __init__(self):
        self.release = None
        self.attachments = []
        self.contents = {}
        self.uploads = 0
        self.patches = 0

    def request(self, method, path, payload=None, file=None):
        if method == "GET" and path.startswith("/releases/tags/"):
            return copy.deepcopy(self.release)
        if method == "POST" and path == "/releases":
            self.release = {"id": 1, **payload}
            return copy.deepcopy(self.release)
        if method == "GET" and "/attach_files?" in path:
            return copy.deepcopy(self.attachments)
        if method == "POST" and path.endswith("/attach_files"):
            self.uploads += 1
            asset = {"name": file.name, "browser_download_url": f"https://gitee.com/files/{file.name}"}
            self.attachments.append(asset)
            self.contents[asset["browser_download_url"]] = file.read_bytes()
            return asset
        if method == "PATCH":
            self.patches += 1
            self.release.update(payload)
            return copy.deepcopy(self.release)
        raise AssertionError((method, path))

    def download(self, url):
        return self.contents[url]


class ReleaseSyncTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.tag = "v0.3.13"
        self.apk = self.directory / f"goule-{self.tag}.apk"
        self.apk.write_bytes(b"original signed APK\x00\xff")
        self.checksum = self.directory / f"{self.apk.name}.sha256"
        self.checksum.write_text(f"{hashlib.sha256(self.apk.read_bytes()).hexdigest()}  {self.apk.name}\n")
        self.files = [self.apk, self.checksum]
        self.source = {
            "tag_name": self.tag,
            "name": "斩心魔 v0.3.13",
            "body": "国内下载：" + self.github_url(self.apk.name),
            "prerelease": True,
            "assets": [{"name": file.name, "browser_download_url": self.github_url(file.name)}
                       for file in self.files],
        }
        self.client = FakeGitee()

    def github_url(self, name):
        return f"https://github.com/grovecai/goule/releases/download/{self.tag}/{name}"

    def synchronize(self):
        with patch("builtins.print"):
            sync.sync_release(self.client, self.source, self.files, "a" * 40)

    def test_original_apk_and_checksum_are_accepted(self):
        self.assertEqual(sync.validate_artifacts(self.directory, self.tag), self.files)

    def test_changed_apk_is_rejected_before_upload(self):
        self.apk.write_bytes(b"different signed APK")
        with self.assertRaisesRegex(RuntimeError, "does not match"):
            sync.validate_artifacts(self.directory, self.tag)

    def test_checksum_for_another_filename_is_rejected(self):
        self.checksum.write_text(self.checksum.read_text().replace(self.apk.name, "other.apk"))
        with self.assertRaisesRegex(RuntimeError, "name the release APK"):
            sync.validate_artifacts(self.directory, self.tag)

    def test_first_sync_copies_bytes_metadata_and_domestic_links(self):
        self.synchronize()
        self.assertEqual(self.client.uploads, 2)
        self.assertTrue(self.client.release["prerelease"])
        self.assertEqual(self.client.release["target_commitish"], "a" * 40)
        self.assertEqual(self.client.release["name"], self.source["name"])
        self.assertIn("https://gitee.com/files/", self.client.release["body"])
        for file in self.files:
            self.assertEqual(self.client.contents[f"https://gitee.com/files/{file.name}"], file.read_bytes())

    def test_rerun_updates_notes_without_duplicate_uploads(self):
        self.synchronize()
        self.source["body"] = "更新后的中文说明"
        self.synchronize()
        self.assertEqual(self.client.uploads, 2)
        self.assertEqual(self.client.release["body"], self.source["body"])

    def test_interrupted_upload_can_resume(self):
        self.synchronize()
        self.client.attachments.pop()
        self.synchronize()
        self.assertEqual(self.client.uploads, 3)
        self.assertEqual(len(self.client.attachments), 2)

    def test_same_name_with_different_bytes_fails_without_overwriting(self):
        self.synchronize()
        url = self.client.attachments[0]["browser_download_url"]
        self.client.contents[url] = b"corrupt"
        with self.assertRaisesRegex(RuntimeError, "differs"):
            self.synchronize()
        self.assertEqual(self.client.uploads, 2)
        self.assertEqual(self.client.contents[url], b"corrupt")
        self.assertEqual(self.client.patches, 1)

    def test_corrupt_new_upload_is_not_reported_as_success(self):
        self.client.download = lambda url: b"truncated upload"
        with self.assertRaisesRegex(RuntimeError, "differs"):
            self.synchronize()
        self.assertEqual(self.client.patches, 0)

    def test_duplicate_remote_filenames_are_rejected(self):
        self.synchronize()
        self.client.attachments.append(copy.deepcopy(self.client.attachments[0]))
        with self.assertRaisesRegex(RuntimeError, "Duplicate"):
            self.synchronize()

    def test_missing_token_fails_before_network_or_git_changes(self):
        with patch.dict(os.environ, {}, clear=True), patch("sys.argv", ["sync", self.tag]), \
                patch.object(sync, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "GITEE_TOKEN"):
                sync.main()
            run.assert_not_called()

    def test_draft_release_is_rejected(self):
        import json
        source = {**self.source, "draft": True}
        with patch.dict(os.environ, {"GITEE_TOKEN": "test-token"}), \
                patch("sys.argv", ["sync", self.tag]), \
                patch("builtins.print"), \
                patch.object(sync, "verify_repository"), \
                patch.object(sync, "run", return_value=json.dumps(source)) as run:
            with self.assertRaisesRegex(RuntimeError, "Only published"):
                sync.main()
            self.assertEqual(run.call_count, 1)

    def test_repository_missing_or_inaccessible_is_rejected(self):
        with patch.object(self.client, "request", return_value=None):
            with self.assertRaisesRegex(RuntimeError, "missing or inaccessible"):
                sync.verify_repository(self.client)

    def test_private_repository_is_rejected_before_publishing(self):
        with patch.object(self.client, "request", return_value={"public": False}):
            with self.assertRaisesRegex(RuntimeError, "must be public"):
                sync.verify_repository(self.client)

    def test_read_only_repository_access_is_rejected(self):
        repository = {"public": True, "permissions": {"push": False}}
        with patch.object(self.client, "request", return_value=repository):
            with self.assertRaisesRegex(RuntimeError, "write permission"):
                sync.verify_repository(self.client)

    def test_public_repository_with_write_access_is_accepted(self):
        repository = {"public": True, "permissions": {"push": True}}
        with patch.object(self.client, "request", return_value=repository):
            sync.verify_repository(self.client)


if __name__ == "__main__":
    unittest.main()
