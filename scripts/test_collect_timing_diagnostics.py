"""Offline coverage for safe evidence capture without relying on UTF-8 device output."""

from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from collect_timing_diagnostics import capture, collect


class TimingCaptureTests(unittest.TestCase):
    def test_non_utf8_logs_preserved_and_capture_is_read_only(self):
        calls = []

        def run(command, **kwargs):
            calls.append(command)
            return subprocess.CompletedProcess(command, 0, b"raw\xc0\xff\n")

        with tempfile.TemporaryDirectory() as directory, patch("subprocess.run", side_effect=run):
            output = Path(directory) / "evidence"
            self.assertEqual([], collect("adb", "phone", output))
            self.assertEqual(b"raw\xc0\xff\n", (output / "logcat.txt").read_bytes())
            self.assertEqual(0o700, output.stat().st_mode & 0o777)
            self.assertEqual(0o600, (output / "logcat.txt").stat().st_mode & 0o777)
            self.assertTrue(all(command[1:3] == ["-s", "phone"] for command in calls))
            self.assertFalse(any("-c" in command or "install" in command or "start" in command for command in calls))
            self.assertIn("--history", calls[1])

    def test_timeout_keeps_partial_evidence_and_other_captures_continue(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "partial.txt"
            with patch("subprocess.run", side_effect=subprocess.TimeoutExpired("adb", 20, output=b"partial")):
                self.assertEqual(124, capture("adb", "phone", ["logcat", "-d"], output))
            self.assertIn(b"partial", output.read_bytes())
            with patch("subprocess.run", return_value=subprocess.CompletedProcess([], 1, b"offline")):
                failures = collect("adb", "phone", Path(directory) / "evidence")
            self.assertIn("service-history", failures)
            self.assertIn("logcat", failures)
