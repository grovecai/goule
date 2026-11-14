#!/usr/bin/env python3
"""Copy a published GitHub release to Gitee without rebuilding its APK."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid


REPOSITORY = "grovecai/goule"
API = f"https://gitee.com/api/v5/repos/{REPOSITORY}"


def run(*args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


class Gitee:
    def __init__(self, token):
        self.token = token

    def request(self, method, path, payload=None, file=None):
        headers = {"Authorization": f"Bearer {self.token}"}
        data = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload).encode()
        if file is not None:
            boundary = uuid.uuid4().hex
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
            data = (
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                f'filename="{file.name}"\r\nContent-Type: application/octet-stream\r\n\r\n'
            ).encode() + file.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        request = urllib.request.Request(API + path, data, headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if method == "GET" and error.code == 404:
                return None
            # Avoid printing response bodies or URLs that may contain credentials.
            raise RuntimeError(f"Gitee {method} {path}: HTTP {error.code}") from None

    def download(self, url):
        # Public downloads must work for domestic users without an access token.
        if urllib.parse.urlsplit(url).scheme != "https":
            raise RuntimeError("Gitee returned a download URL without HTTPS")
        with urllib.request.urlopen(url, timeout=120) as response:
            return response.read()


def validate_artifacts(directory, tag):
    apk = directory / f"goule-{tag}.apk"
    checksum = directory / f"{apk.name}.sha256"
    match = re.fullmatch(r"([0-9a-fA-F]{64})[ \t]+\*?([^\r\n]+)\r?\n?", checksum.read_text())
    if not match or match[2] != apk.name:
        raise RuntimeError("SHA-256 file must name the release APK exactly")
    if hashlib.sha256(apk.read_bytes()).hexdigest() != match[1].lower():
        raise RuntimeError("GitHub APK does not match its SHA-256 file")
    return [apk, checksum]


def sync_release(client, source, files, commit):
    tag = source["tag_name"]
    release_path = f"/releases/tags/{urllib.parse.quote(tag, safe='')}"
    fields = {key: source[key] for key in ("tag_name", "name", "body", "prerelease")}
    release = client.request("GET", release_path)
    if release is None:
        release = client.request("POST", "/releases", {**fields, "target_commitish": commit})
    attachments_path = f'/releases/{release["id"]}/attach_files'
    attachments = []
    page = 1
    while True:
        batch = client.request("GET", f"{attachments_path}?page={page}&per_page=100")
        attachments.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    downloads = {}
    for file in files:
        matches = [asset for asset in attachments if asset["name"] == file.name]
        if len(matches) > 1:
            raise RuntimeError(f"Duplicate Gitee attachments: {file.name}")
        asset = matches[0] if matches else client.request("POST", attachments_path, file=file)
        downloaded = client.download(asset["browser_download_url"])
        if len(downloaded) != file.stat().st_size or downloaded != file.read_bytes():
            raise RuntimeError(f"Gitee attachment differs: {file.name}; no file was overwritten")
        downloads[file.name] = asset["browser_download_url"]

    # Keep the release notes but point their asset links at the domestic copies.
    body = fields["body"]
    for asset in source["assets"]:
        if asset["name"] in downloads:
            body = body.replace(asset["browser_download_url"], downloads[asset["name"]])
    fields["body"] = body
    client.request("PATCH", f'/releases/{release["id"]}', fields)
    verified = client.request("GET", release_path)
    if any(verified[key] != value for key, value in fields.items()):
        raise RuntimeError("Gitee release metadata verification failed")
    print(f"Verified Gitee release: https://gitee.com/{REPOSITORY}/releases/tag/{tag}")
    for name, url in downloads.items():
        print(f"{name}: {url}")


def push_refs(tag, token, directory):
    # Use an ephemeral askpass script; never put tokens in Git URLs or arguments.
    askpass = directory / "askpass"
    askpass.write_text(
        '#!/bin/sh\ncase "$1" in\n'
        '  *Username*) printf "%s\\n" "grovecai" ;;\n'
        '  *) printf "%s\\n" "$GITEE_TOKEN" ;;\nesac\n'
    )
    askpass.chmod(0o700)
    env = {**os.environ, "GITEE_TOKEN": token, "GIT_ASKPASS": str(askpass), "GIT_TERMINAL_PROMPT": "0"}
    remote = f"https://gitee.com/{REPOSITORY}.git"
    refs = ["refs/remotes/origin/main:refs/heads/main", f"refs/tags/{tag}:refs/tags/{tag}"]
    # Disable unrelated credential helpers for this command only.
    run("git", "-c", "credential.helper=", "push", remote, *refs, env=env)
    actual = dict(
        line.split()[::-1]
        for line in run("git", "-c", "credential.helper=", "ls-remote", remote,
                        "refs/heads/main", f"refs/tags/{tag}", env=env).splitlines()
    )
    for local, target in (ref.split(":") for ref in refs):
        if actual.get(target) != run("git", "rev-parse", local):
            raise RuntimeError(f"Gitee ref verification failed: {target}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag", help="Published GitHub tag, for example v0.3.13")
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
        parser.error("tag must have the form v0.3.13")
    token = os.environ.get("GITEE_TOKEN")
    if not token:
        raise RuntimeError("Configure the GITEE_TOKEN Actions secret or environment variable first")
    source = json.loads(run("gh", "api", f"repos/{REPOSITORY}/releases/tags/{args.tag}"))
    if source["draft"] or source["tag_name"] != args.tag:
        raise RuntimeError("Only published GitHub releases with the requested tag can be synced")
    expected = {f"goule-{args.tag}.apk", f"goule-{args.tag}.apk.sha256"}
    if not expected.issubset({asset["name"] for asset in source["assets"]}):
        raise RuntimeError("GitHub release must contain both APK and SHA-256 attachments")
    run("git", "fetch", "origin", "refs/heads/main:refs/remotes/origin/main",
        f"refs/tags/{args.tag}:refs/tags/{args.tag}")
    commit = run("git", "rev-parse", f"refs/tags/{args.tag}^{{commit}}")
    run("git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main")
    with tempfile.TemporaryDirectory(prefix="goule-gitee-") as temporary:
        directory = Path(temporary)
        run("gh", "release", "download", args.tag, "--repo", REPOSITORY,
            "--pattern", f"goule-{args.tag}.apk", "--pattern", f"goule-{args.tag}.apk.sha256",
            "--dir", str(directory))
        files = validate_artifacts(directory, args.tag)
        push_refs(args.tag, token, directory)
        sync_release(Gitee(token), source, files, commit)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Gitee sync failed: {error}", file=sys.stderr)
        sys.exit(1)
