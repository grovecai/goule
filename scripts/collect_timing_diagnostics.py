#!/usr/bin/env python3
"""Read-only ADB evidence capture. Raw device data stays in a private local directory."""

import argparse
from datetime import datetime
import os
from pathlib import Path
import shutil
import subprocess
import time


def capture(adb, serial, arguments, destination, timeout=20):
    try:
        result = subprocess.run(
            [str(adb), "-s", serial, *arguments], stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, timeout=timeout, check=False,
        )
        data, code = result.stdout, result.returncode
    except subprocess.TimeoutExpired as error:
        data, code = (error.stdout or b"") + b"\nCAPTURE TIMEOUT\n", 124
    destination.write_bytes(data)
    destination.chmod(0o600)
    return code


def collect(adb, serial, output, watch_seconds=0, interval=1):
    output.mkdir(parents=True, exist_ok=False, mode=0o700)
    commands = {
        "clock": ["shell", "date", "+%Y-%m-%dT%H:%M:%S%z"],
        "service-history": ["shell", "dumpsys", "activity", "service", "com.goule.prototype/.FreezeService", "--history"],
        "activity-history": ["shell", "dumpsys", "activity", "com.goule.prototype/.MainActivity", "--history"],
        "accessibility": ["shell", "dumpsys", "accessibility"],
        "exits": ["shell", "dumpsys", "activity", "exit-info", "com.goule.prototype"],
        "package": ["shell", "dumpsys", "package", "com.goule.prototype"],
        "window": ["shell", "dumpsys", "window", "displays"],
        "power": ["shell", "dumpsys", "power"],
        "process": ["shell", "dumpsys", "activity", "processes", "com.goule.prototype"],
        "logcat": ["logcat", "-d", "-b", "all", "-v", "threadtime"],
        "usage": ["shell", "dumpsys", "usagestats"],
    }
    failures = []
    # Save short-lived observations first, before the larger system dumps.
    for name, command in commands.items():
        if capture(adb, serial, command, output / f"{name}.txt", timeout=30) != 0:
            failures.append(name)
    end = time.monotonic() + watch_seconds
    index = 0
    while time.monotonic() < end:
        started = time.monotonic()
        prefix = f"watch-{index:05d}"
        capture(adb, serial, commands["service-history"][:-1], output / f"{prefix}-service.txt")
        capture(adb, serial, commands["window"], output / f"{prefix}-window.txt")
        index += 1
        time.sleep(max(0, min(end - time.monotonic(), interval - (time.monotonic() - started))))
    if watch_seconds:
        capture(adb, serial, commands["service-history"], output / "service-history-after.txt")
        capture(adb, serial, commands["logcat"], output / "logcat-after.txt", timeout=30)
    note = output / "README.txt"
    note.write_text(
        f"Captured {datetime.now().astimezone().isoformat()}\n"
        f"Read-only; did not launch apps, toggle permissions, clear logs or change rules.\n"
        f"Initial captures with errors: {', '.join(failures) or 'none'}\n"
        f"Watch samples: {index}\n"
        "Raw system dumps may contain other apps and private device information. Do not commit or publish this directory.\n"
        "An older app version may ignore --history and show only its 32 in-memory observations.\n"
        "If the service is disconnected, open the app yourself, then repeat capture to export its previous journal via Activity.\n"
    )
    note.chmod(0o600)
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="An authorized adb device serial")
    parser.add_argument("--adb", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--watch-seconds", type=float, default=0)
    parser.add_argument("--interval", type=float, default=1)
    args = parser.parse_args()
    if not 0 <= args.watch_seconds <= 3600 or args.interval < 1:
        parser.error("watch-seconds must be 0..3600 and interval must be at least 1 second")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    adb = args.adb or shutil.which("adb") or sdk / "platform-tools/adb"
    device = subprocess.run([str(adb), "-s", args.serial, "get-state"], capture_output=True, check=False)
    if device.returncode != 0 or device.stdout.strip() != b"device":
        parser.error("Selected device is not connected and authorized; no evidence directory created")
    output = args.output or Path.home() / ".cache/goule-android" / datetime.now().strftime("timing-%Y%m%d-%H%M%S")
    failures = collect(adb, args.serial, output, args.watch_seconds, args.interval)
    print(f"Evidence saved locally: {output.resolve()}")
    if failures:
        print(f"Some captures failed; see their output files: {', '.join(failures)}")
    return int(bool(failures))


if __name__ == "__main__":
    raise SystemExit(main())
