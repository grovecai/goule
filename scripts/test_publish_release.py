"""Real Git history regressions for scheduled/retried Android publication."""

import json
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import publish_release as publish


class ReleasePlannerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.patch = patch.object(publish, "ROOT", self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Release regression")
        self.git("config", "user.email", "test@example.invalid")
        self.write("app/build.gradle.kts", 'android {\n defaultConfig {\n versionCode = 16\n versionName = "0.3.13"\n }\n}\n')
        self.write("app/src/main/Rule.kt", "original app code\n")
        self.commit("baseline")
        self.git("tag", "v0.3.13")
        self.releases = [{"tag_name": "v0.3.13", "draft": False}]

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True, stderr=subprocess.PIPE).strip()

    def write(self, name, content):
        file = self.root / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "-qm", message)

    def plan(self, **kwargs):
        return publish.plan_release(self.releases, publish.Options(**kwargs))

    def test_unchanged_tree_skips_without_a_version_bump(self):
        self.assertEqual(self.plan()["mode"], "skip")

    def test_documentation_and_completed_bot_state_do_not_loop(self):
        self.write("README.md", "Updated download links\n")
        self.write("PROGRESS.md", "Verified release\n")
        self.write("docs/release-v0.3.13.md", "release notes\n")
        self.write(".github/release-state.json", json.dumps({"schema": 1, "complete": True, "tag": "v0.3.13"}))
        self.commit("bot completion and documentation")
        self.assertEqual(self.plan()["mode"], "skip")

    def test_application_change_advances_patch_and_android_code(self):
        self.write("app/src/main/Rule.kt", "corrected app code\n")
        self.commit("fix rule")
        plan = self.plan()
        self.assertEqual((plan["tag"], plan["version_code"]), ("v0.3.14", 17))
        self.assertEqual(plan["changed_files"], ["app/src/main/Rule.kt"])

    def test_build_pipeline_change_is_a_code_update(self):
        self.write(".github/workflows/publish-release.yml", "updated build pipeline\n")
        self.commit("fix build")
        self.assertEqual(self.plan()["mode"], "new")

    def test_force_supports_manual_stage_release_without_changes(self):
        plan = self.plan(force=True)
        self.assertEqual(plan["tag"], "v0.3.14")
        self.assertEqual(plan["changed_files"], [])

    def test_manual_version_and_stable_status_are_supported(self):
        plan = self.plan(force=True, version="0.4.0", prerelease=False)
        self.assertEqual(plan["tag"], "v0.4.0")
        self.assertFalse(plan["prerelease"])

    def test_manually_advanced_source_version_is_preserved(self):
        file = self.root / "app/build.gradle.kts"
        file.write_text(publish.replace_app_version(file.read_text(), "0.4.0", 40))
        self.commit("prepare next series")
        plan = self.plan()
        self.assertEqual((plan["version"], plan["version_code"]), ("0.4.0", 41))

    def test_reusing_a_version_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "must advance"):
            self.plan(force=True, version="v0.3.13")

    def test_existing_tag_is_never_replaced(self):
        self.git("tag", "v0.3.14")
        with self.assertRaisesRegex(RuntimeError, "already exists"):
            self.plan(force=True)

    def test_existing_draft_is_never_overwritten_as_a_new_release(self):
        self.releases.append({"tag_name": "v0.3.14", "draft": True})
        with self.assertRaisesRegex(RuntimeError, "already exists"):
            self.plan(force=True)

    def pending(self):
        plan = self.plan(force=True)
        state = {key: value for key, value in plan.items() if key != "mode"}
        file = self.root / "app/build.gradle.kts"
        file.write_text(publish.replace_app_version(file.read_text(), plan["version"], plan["version_code"]))
        self.write(publish.STATE.as_posix(), json.dumps(state))
        self.commit("release: prepare v0.3.14")
        self.git("tag", "v0.3.14")
        return state, self.git("rev-parse", "HEAD")

    def test_retry_freezes_version_commit_and_prerelease_status(self):
        _, commit = self.pending()
        self.write("app/src/main/Rule.kt", "new work during pending publication\n")
        self.commit("later work")
        plan = self.plan(prerelease=False)
        self.assertEqual((plan["mode"], plan["tag"], plan["version_code"], plan["commit"]),
                         ("resume", "v0.3.14", 17, commit))
        self.assertTrue(plan["prerelease"])

    def test_cannot_skip_an_interrupted_release_for_another_version(self):
        self.pending()
        with self.assertRaisesRegex(RuntimeError, "Finish pending"):
            self.plan(version="v0.4.0")

    def test_altered_pending_state_is_rejected(self):
        state, _ = self.pending()
        state["version_code"] = 99
        self.write(publish.STATE.as_posix(), json.dumps(state))
        self.commit("unexpected state edit")
        with self.assertRaisesRegex(RuntimeError, "immutable tag"):
            self.plan()

    def test_unrelated_tag_history_is_rejected(self):
        self.git("checkout", "--orphan", "unrelated")
        self.git("rm", "-rf", ".")
        self.write("app/build.gradle.kts", 'versionCode = 16\nversionName = "0.3.13"\n')
        self.commit("unrelated history")
        with self.assertRaises(subprocess.CalledProcessError):
            self.plan(force=True)

    def test_version_edit_preserves_all_other_build_configuration(self):
        text = 'prefix\n    versionCode = 16\n    versionName = "0.3.13"\nsuffix\n'
        self.assertEqual(publish.replace_app_version(text, "0.3.14", 17),
                         text.replace("16", "17").replace("0.3.13", "0.3.14"))

    def test_missing_signing_material_stops_before_gradle(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(publish, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "signing Secrets"):
                publish.build_signed_apk(self.root / "app.apk", {}, "a" * 40)
            run.assert_not_called()

    def test_foreign_certificate_is_rejected(self):
        with patch.object(publish, "android_tool", return_value="apksigner"), \
                patch.object(publish, "run", return_value="Signer #1 certificate SHA-256 digest: " + "b" * 64):
            with self.assertRaisesRegex(RuntimeError, "original release certificate"):
                publish.certificate(self.root / "app.apk")

    def test_missing_published_asset_does_not_rebuild_or_overwrite(self):
        release = {"draft": False, "assets": []}
        with patch.object(publish, "github_release", return_value=release), \
                patch.object(publish, "build_signed_apk") as build:
            with self.assertRaisesRegex(RuntimeError, "Published release has missing assets"):
                publish.ensure_github_release(self.root, {"tag": "v0.3.14"}, "a" * 40)
            build.assert_not_called()

    def test_failed_atomic_push_cannot_create_a_release(self):
        plan = self.plan(force=True)

        def build(file, *args):
            file.write_bytes(b"signed APK")

        with patch.object(publish, "github_release", return_value=None), \
                patch.object(publish, "build_signed_apk", side_effect=build), \
                patch.object(publish, "git", side_effect=subprocess.CalledProcessError(1, ["git", "push"])), \
                patch.object(publish, "run") as run:
            with self.assertRaises(subprocess.CalledProcessError):
                publish.ensure_github_release(self.root, plan, "a" * 40)
            run.assert_not_called()

    def test_partial_draft_reuses_verified_apk_and_only_uploads_missing_checksum(self):
        plan = {**self.plan(force=True), "mode": "resume"}
        apk_name = "goule-v0.3.14.apk"
        checksum_name = apk_name + ".sha256"
        content = b"previously uploaded original signed APK"
        checksum = f"{hashlib.sha256(content).hexdigest()}  {apk_name}\n".encode()
        calls = []

        def run(*args, **kwargs):
            calls.append(args)
            if args[:3] == ("gh", "release", "download"):
                output = Path(args[args.index("--dir") + 1])
                output.mkdir(exist_ok=True)
                for index, arg in enumerate(args):
                    if arg == "--pattern":
                        name = args[index + 1]
                        (output / name).write_bytes(content if name == apk_name else checksum)
            return ""

        draft = {"draft": True, "assets": [{"name": apk_name}]}
        published = {"draft": False, "prerelease": True}
        with patch.object(publish, "github_release", side_effect=[draft, published]), \
                patch.object(publish, "verify_apk") as verify, \
                patch.object(publish, "build_signed_apk") as build, \
                patch.object(publish, "run", side_effect=run):
            apk, _ = publish.ensure_github_release(self.root, plan, "a" * 40)
        build.assert_not_called()
        verify.assert_called_once_with(apk, plan, "a" * 40)
        uploads = [args for args in calls if args[:3] == ("gh", "release", "upload")]
        self.assertEqual(len(uploads), 1)
        self.assertEqual(Path(uploads[0][-1]).name, checksum_name)
        self.assertFalse(any("--clobber" in args for args in calls))


if __name__ == "__main__":
    unittest.main()
