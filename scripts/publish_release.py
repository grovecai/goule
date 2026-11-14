#!/usr/bin/env python3
"""Build, sign and publish main to GitHub, then verify the Gitee copy."""

import base64
from dataclasses import dataclass
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = "grovecai/goule"
STATE = Path(".github/release-state.json")
CONFIG = Path("app/build.gradle.kts")
CERTIFICATE = "a99c6b2900299c459fe1121dc7868762d8afb66ec9f5b4819a471a5b62103559"
APPLICATION_ID = "com.goule.prototype"
CODE_PREFIXES = ("app/", "gradle/", "scripts/", ".github/workflows/")
CODE_FILES = {"gradlew", "gradlew.bat", "settings.gradle.kts", "settings.gradle",
              "build.gradle.kts", "build.gradle", "gradle.properties"}


def run(*args, cwd=None, timeout=300, **kwargs):
    return subprocess.check_output(args, cwd=cwd or ROOT, text=True,
                                   timeout=timeout, **kwargs).strip()


def git(*args):
    return run("git", *args)


def gh_json(path):
    return json.loads(run("gh", "api", path))


def version_tuple(value):
    match = re.fullmatch(r"v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", value)
    if not match:
        raise RuntimeError(f"Invalid version: {value!r}; expected v0.3.14")
    return tuple(map(int, match.groups()))


def app_version(text):
    code = re.search(r"^\s*versionCode = (\d+)\s*$", text, re.MULTILINE)
    name = re.search(r'^\s*versionName = "([^"]+)"\s*$', text, re.MULTILINE)
    if not code or not name:
        raise RuntimeError("Cannot read app versionCode/versionName")
    version_tuple(name[1])
    return name[1], int(code[1])


def replace_app_version(text, name, code):
    app_version(text)
    text = re.sub(r"(?m)^(\s*versionCode = )\d+(\s*)$", rf"\g<1>{code}\2", text)
    return re.sub(r'(?m)^(\s*versionName = ")[^"]+("\s*)$', rf"\g<1>{name}\2", text)


@dataclass
class Options:
    version: str = ""
    force: bool = False
    prerelease: bool = True
    notes: str = ""
    dry_run: bool = False


def list_releases():
    releases = []
    for page in range(1, 101):
        batch = gh_json(f"repos/{REPOSITORY}/releases?per_page=100&page={page}")
        releases.extend(batch)
        if len(batch) < 100:
            return releases
    raise RuntimeError("Too many releases to safely choose the next version")


def plan_release(releases, options):
    current_name, current_code = app_version((ROOT / CONFIG).read_text())
    if (ROOT / STATE).exists():
        state = json.loads((ROOT / STATE).read_text())
        if state.get("schema") != 1:
            raise RuntimeError("Unrecognized release state; refusing to overwrite it")
        if not state["complete"]:
            tag = state["tag"]
            version_tuple(tag)
            if options.version and version_tuple(options.version) != version_tuple(tag):
                raise RuntimeError(f"Finish pending {tag} before starting another version")
            frozen = json.loads(git("show", f"refs/tags/{tag}:{STATE.as_posix()}"))
            if frozen != state:
                raise RuntimeError("Pending release state does not match its immutable tag")
            git("merge-base", "--is-ancestor", f"refs/tags/{tag}", "HEAD")
            return {**state, "mode": "resume", "commit": git("rev-parse", f"refs/tags/{tag}^{{commit}}")}
    published = [release for release in releases if not release["draft"]
                 and re.fullmatch(r"v\d+\.\d+\.\d+", release["tag_name"])]
    if not published:
        raise RuntimeError("An existing semantic-version release is required as the baseline")
    latest = max(published, key=lambda release: version_tuple(release["tag_name"]))
    previous = latest["tag_name"]
    git("merge-base", "--is-ancestor", previous, "HEAD")
    changed = git("diff", "--name-only", previous, "HEAD").splitlines()
    relevant = [name for name in changed if name.startswith(CODE_PREFIXES) or name in CODE_FILES]
    if not relevant and not options.force:
        return {"mode": "skip", "previous_tag": previous, "reason": "No application or build code changes"}
    latest_version = version_tuple(previous)
    if options.version:
        next_version = version_tuple(options.version)
    elif version_tuple(current_name) > latest_version:
        next_version = version_tuple(current_name)
    else:
        next_version = (*latest_version[:2], latest_version[2] + 1)
    if next_version <= latest_version or next_version < version_tuple(current_name):
        raise RuntimeError("Release version must advance without downgrading source versionName")
    name = ".".join(map(str, next_version))
    tag = f"v{name}"
    if any(release["tag_name"] == tag for release in releases) or git("tag", "--list", tag):
        raise RuntimeError(f"{tag} already exists; refusing to replace a tag or release")
    _, previous_code = app_version(git("show", f"{previous}:{CONFIG.as_posix()}"))
    code = max(current_code, previous_code) + 1
    return {"schema": 1, "mode": "new", "tag": tag, "version": name,
            "version_code": code, "previous_tag": previous,
            "source_commit": git("rev-parse", "HEAD"), "prerelease": options.prerelease,
            "complete": False, "changed_files": relevant}


def write_summary(message):
    print(message, flush=True)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write(message + "\n\n")


def release_notes(plan, options):
    tag = plan["tag"]
    changes = git("log", "--format=%h %s", f'{plan["previous_tag"]}..HEAD', "--",
                  *CODE_PREFIXES, *sorted(CODE_FILES)).splitlines()
    body = options.notes.strip() or "\n".join(f"- {line}" for line in changes)
    if not body:
        body = "阶段性发布，沿用上一版应用功能。"
    return (
        f"# 斩心魔 {tag}\n\n{body}\n\n"
        f"## 下载与升级\n\n"
        f"[APK](https://github.com/{REPOSITORY}/releases/download/{tag}/goule-{tag}.apk) · "
        f"[SHA-256](https://github.com/{REPOSITORY}/releases/download/{tag}/goule-{tag}.apk.sha256)\n\n"
        f'Android 11 及以上，版本 {plan["version"]}/versionCode {plan["version_code"]}。'
        "沿用原发布签名，可覆盖升级并保留数据。\n\n"
        "## 自动验证与边界\n\n"
        "发布前执行项目检查、Release 构建、版本元数据、原签名证书及 ZIP 对齐检查；"
        "两端发布后回下载核对 APK 和 SHA-256 文件。自动构建不代表完成新的真机行为回归。\n\n"
        f"[既有验证与已知边界](https://github.com/{REPOSITORY}/blob/{tag}/docs/verification.md)\n"
    )


def prepare_source(plan, options):
    if plan["mode"] == "resume":
        git("checkout", "--detach", plan["commit"])
        return plan["commit"]
    (ROOT / CONFIG).write_text(replace_app_version((ROOT / CONFIG).read_text(),
                                                   plan["version"], plan["version_code"]))
    state = {key: value for key, value in plan.items() if key != "mode"}
    (ROOT / STATE).write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
    notes = Path(f'docs/release-{plan["tag"]}.md')
    if not (ROOT / notes).exists():
        (ROOT / notes).write_text(release_notes(plan, options))
    run("./scripts/check", timeout=1200)
    git("add", CONFIG.as_posix(), STATE.as_posix(), notes.as_posix())
    git("diff", "--cached", "--check")
    git("commit", "-m", f'release: prepare {plan["tag"]}')
    commit = git("rev-parse", "HEAD")
    git("tag", "-a", plan["tag"], "-m", f'斩心魔 {plan["tag"]}')
    return commit


def android_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", ""))
    tool = sdk / "build-tools/35.0.0" / name
    if not tool.is_file():
        raise RuntimeError(f"Android build-tools 35.0.0 is required: {name}")
    return str(tool)


def certificate(apk):
    output = run(android_tool("apksigner"), "verify", "--print-certs", str(apk))
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
    if len(digests) != 1 or digests[0].lower() != CERTIFICATE:
        raise RuntimeError("APK certificate differs from the original release certificate")


def verify_apk(apk, plan, commit):
    certificate(apk)
    run(android_tool("zipalign"), "-c", "-P", "16", "4", str(apk))
    badging = run(android_tool("aapt"), "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.groups() != (APPLICATION_ID, str(plan["version_code"]), plan["version"]):
        raise RuntimeError("APK application ID or version metadata does not match its release")
    if "application-debuggable" in badging or "sdkVersion:'30'" not in badging:
        raise RuntimeError("APK must be a non-debuggable Android 11+ release")
    with zipfile.ZipFile(apk) as archive:
        info = archive.read("META-INF/version-control-info.textproto").decode()
        if commit not in info:
            raise RuntimeError("APK embedded source commit does not match its release tag")


def build_signed_apk(output, plan, commit):
    names = ("ANDROID_RELEASE_KEYSTORE_BASE64", "ANDROID_RELEASE_STORE_PASSWORD", "ANDROID_RELEASE_KEY_PASSWORD")
    if any(not os.environ.get(name) for name in names):
        raise RuntimeError("Configure all three ANDROID_RELEASE_* signing Secrets before publishing")
    run("./gradlew", "assembleRelease", timeout=1200)
    with tempfile.TemporaryDirectory(prefix="goule-signing-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
        directory = Path(temporary)
        key = directory / "release.p12"
        key.write_bytes(base64.b64decode(os.environ[names[0]], validate=True))
        key.chmod(0o600)
        aligned = directory / "aligned.apk"
        run(android_tool("zipalign"), "-P", "16", "-f", "4",
            str(ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"), str(aligned))
        run(android_tool("apksigner"), "sign", "--ks", str(key), "--ks-key-alias", "goule-release",
            "--ks-pass", "env:ANDROID_RELEASE_STORE_PASSWORD", "--key-pass", "env:ANDROID_RELEASE_KEY_PASSWORD",
            "--out", str(output), str(aligned))
    verify_apk(output, plan, commit)


def github_release(tag):
    releases = list_releases()
    return next((release for release in releases if release["tag_name"] == tag), None)


def ensure_github_release(directory, plan, commit):
    tag = plan["tag"]
    apk = directory / f"goule-{tag}.apk"
    checksum = directory / f"{apk.name}.sha256"
    release = github_release(tag)
    assets = {asset["name"]: asset for asset in release["assets"]} if release else {}
    if release and not release["draft"] and not {apk.name, checksum.name}.issubset(assets):
        raise RuntimeError("Published release has missing assets; manual inspection required")
    if apk.name in assets:
        run("gh", "release", "download", tag, "--repo", REPOSITORY, "--pattern", apk.name, "--dir", str(directory))
        verify_apk(apk, plan, commit)
    else:
        build_signed_apk(apk, plan, commit)
    expected = f"{hashlib.sha256(apk.read_bytes()).hexdigest()}  {apk.name}\n".encode()
    if checksum.name in assets:
        run("gh", "release", "download", tag, "--repo", REPOSITORY, "--pattern", checksum.name, "--dir", str(directory))
        if checksum.read_bytes() != expected:
            raise RuntimeError("Existing release checksum differs; refusing to overwrite assets")
    else:
        checksum.write_bytes(expected)
    if plan["mode"] == "new":
        # Publish refs only after all checks and original-signature verification.
        # An ordinary atomic push rejects concurrent main changes or tag reuse.
        git("push", "--atomic", "origin", "HEAD:refs/heads/main", f"refs/tags/{tag}:refs/tags/{tag}")
    if release is None:
        run("gh", "release", "create", tag, "--repo", REPOSITORY, "--verify-tag", "--draft",
            "--title", f"斩心魔 {tag}", "--notes-file", str(ROOT / f"docs/release-{tag}.md"),
            f'--prerelease={str(plan["prerelease"]).lower()}', str(apk), str(checksum))
    else:
        for file in (apk, checksum):
            if file.name not in assets:
                if not release["draft"]:
                    raise RuntimeError("Published release has missing assets; manual inspection required")
                run("gh", "release", "upload", tag, "--repo", REPOSITORY, str(file))
    downloaded = directory / "github-downloads"
    downloaded.mkdir()
    run("gh", "release", "download", tag, "--repo", REPOSITORY, "--pattern", apk.name,
        "--pattern", checksum.name, "--dir", str(downloaded))
    for file in (apk, checksum):
        if (downloaded / file.name).read_bytes() != file.read_bytes():
            raise RuntimeError(f"GitHub upload verification failed: {file.name}")
    run("gh", "release", "edit", tag, "--repo", REPOSITORY, "--draft=false",
        f'--prerelease={str(plan["prerelease"]).lower()}')
    actual = github_release(tag)
    if actual["draft"] or actual["prerelease"] != plan["prerelease"]:
        raise RuntimeError("GitHub release publication status verification failed")
    return apk, checksum


def finalize_release(plan, commit, apk):
    tag = plan["tag"]
    git("fetch", "origin", "refs/heads/main:refs/remotes/origin/main")
    git("checkout", "-B", "main", "refs/remotes/origin/main")
    state = json.loads((ROOT / STATE).read_text())
    if state["tag"] != tag:
        raise RuntimeError("Release state changed while publishing; refusing to overwrite it")
    if state["complete"]:
        return
    state["complete"] = True
    (ROOT / STATE).write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
    readme = ROOT / "README.md"
    text = readme.read_text()
    block = (
        f'<!-- release-downloads:start -->\n'
        f'**[国内下载斩心魔 {tag} 签名 APK（Gitee）](https://gitee.com/{REPOSITORY}/releases/download/{tag}/goule-{tag}.apk)** · '
        f'[GitHub 备用下载](https://github.com/{REPOSITORY}/releases/download/{tag}/goule-{tag}.apk)\n\n'
        f'Android 11 及以上 · [Gitee 发布说明](https://gitee.com/{REPOSITORY}/releases/tag/{tag}) · '
        f'[SHA-256](https://gitee.com/{REPOSITORY}/releases/download/{tag}/goule-{tag}.apk.sha256) · '
        f'[GitHub 原发布](https://github.com/{REPOSITORY}/releases/tag/{tag})\n'
        f'<!-- release-downloads:end -->'
    )
    pattern = r"<!-- release-downloads:start -->.*?<!-- release-downloads:end -->"
    if len(re.findall(pattern, text, re.DOTALL)) != 1:
        raise RuntimeError("README must contain exactly one managed release-downloads block")
    readme.write_text(re.sub(pattern, lambda match: block, text, flags=re.DOTALL))
    now = datetime.datetime.now(ZoneInfo("Asia/Shanghai")).isoformat(timespec="seconds")
    verification = {"verified_at": now, "tag": tag, "commit": commit,
                    "version_code": plan["version_code"], "certificate_sha256": CERTIFICATE,
                    "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "apk_size": apk.stat().st_size,
                    "github_and_gitee_downloads_match": True,
                    "workflow_url": f'https://github.com/{REPOSITORY}/actions/runs/{os.environ.get("GITHUB_RUN_ID", "")}',
                    "device_testing": "Not performed by CI; see existing verification records"}
    result = Path("docs/latest-release-verification.json")
    (ROOT / result).write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n")
    progress = ROOT / "PROGRESS.md"
    entry = (f'{now} Actions 已完成 {tag}/code {plan["version_code"]} 原签名自动发布及 Gitee 同步，'
             f'两端附件回下载一致，README 下载已更新。自动验收见 [发布记录]({result.as_posix()})；'
             '本次 CI 不代表新的真机行为回归。\n\n')
    progress_text = progress.read_text().replace("## 当前状态\n\n", "## 当前状态\n\n" + entry, 1)
    progress_text = re.sub(r"(?m)^更新于 \d{4}-\d{2}-\d{2}。$", f"更新于 {now[:10]}。", progress_text, count=1)
    progress.write_text(progress_text)
    run("./scripts/check", timeout=1200)
    git("add", STATE.as_posix(), "README.md", "PROGRESS.md", result.as_posix())
    git("diff", "--cached", "--check")
    git("commit", "-m", f'docs: record verified {tag} publication')
    git("push", "origin", "HEAD:refs/heads/main")


def main():
    if os.environ.get("GITHUB_REF", "refs/heads/main") != "refs/heads/main":
        raise RuntimeError("Releases must be started from main")
    if git("status", "--porcelain", "--untracked-files=all"):
        raise RuntimeError("Release requires a clean checkout")
    event = os.environ.get("GITHUB_EVENT_NAME", "workflow_dispatch")
    manual = event == "workflow_dispatch"
    options = Options(version=os.environ.get("RELEASE_VERSION", "") if manual else "",
                      force=manual and os.environ.get("RELEASE_FORCE") == "true",
                      prerelease=not manual or os.environ.get("RELEASE_PRERELEASE", "true") != "false",
                      notes=os.environ.get("RELEASE_NOTES", "") if manual else "",
                      dry_run=manual and os.environ.get("RELEASE_DRY_RUN") == "true")
    plan = plan_release(list_releases(), options)
    write_summary("Release plan: " + json.dumps(plan, ensure_ascii=False))
    if options.dry_run:
        return
    if plan["mode"] == "skip":
        # Heal a code-mirror failure after a successful publication without
        # rebuilding or minting another Android version.
        if os.environ.get("GITEE_TOKEN"):
            run(sys.executable, str(ROOT / "scripts/sync_gitee_release.py"), "--code-only", timeout=1500)
        return
    if not os.environ.get("GITEE_TOKEN"):
        raise RuntimeError("GITEE_TOKEN is required for automatic publication to both platforms")
    if plan["mode"] == "new" and any(not os.environ.get(name) for name in (
            "ANDROID_RELEASE_KEYSTORE_BASE64", "ANDROID_RELEASE_STORE_PASSWORD", "ANDROID_RELEASE_KEY_PASSWORD")):
        raise RuntimeError("Configure the original Android signing Secrets before preparing a release")
    git("config", "user.name", "github-actions[bot]")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    commit = prepare_source(plan, options)
    with tempfile.TemporaryDirectory(prefix="goule-publish-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
        apk, _ = ensure_github_release(Path(temporary), plan, commit)
        # GITHUB_TOKEN-created releases do not trigger the sibling release-event
        # workflow, so execute and await verification here instead.
        run(sys.executable, str(ROOT / "scripts/sync_gitee_release.py"), plan["tag"], timeout=2400)
        finalize_release(plan, commit, apk)
        run(sys.executable, str(ROOT / "scripts/sync_gitee_release.py"), "--code-only", timeout=1500)
    write_summary(f'Published and verified: https://github.com/{REPOSITORY}/releases/tag/{plan["tag"]}\n\n'
                  f'Gitee: https://gitee.com/{REPOSITORY}/releases/tag/{plan["tag"]}')


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        sys.exit(1)
