#!/usr/bin/env python3
"""Tests actual Java/Gradle version capture without requiring either runtime."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("capture-build-versions.sh")


class CaptureBuildVersionsTests(unittest.TestCase):
    def test_captures_versions_reported_by_runtime_commands(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            binary = root / "bin"
            binary.mkdir()
            java = binary / "java"
            java.write_text(
                '#!/bin/sh\necho \'openjdk version "21.0.8" 2025-07-15\' >&2\n'
            )
            java.chmod(0o755)
            wrapper = root / "gradlew"
            wrapper.write_text('#!/bin/sh\nprintf "Gradle 8.7\\n"\n')
            wrapper.chmod(0o755)
            output = root / "github-env"
            environment = {
                **os.environ,
                "PATH": f"{binary}:{os.environ['PATH']}",
                "GITHUB_ENV": str(output),
            }
            result = subprocess.run(
                ["bash", str(SCRIPT)],
                cwd=root,
                env=environment,
                capture_output=True,
                text=True,
                check=True,
            )
            self.assertIn("Captured Java 21.0.8 and Gradle 8.7", result.stdout)
            self.assertEqual(output.read_text(), "JAVA_VERSION=21.0.8\nGRADLE_VERSION=8.7\n")

    def test_fails_when_runtime_version_output_cannot_be_parsed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            binary = root / "bin"
            binary.mkdir()
            java = binary / "java"
            java.write_text('#!/bin/sh\necho "java unavailable" >&2\n')
            java.chmod(0o755)
            wrapper = root / "gradlew"
            wrapper.write_text('#!/bin/sh\necho "version unavailable"\n')
            wrapper.chmod(0o755)
            environment = {
                **os.environ,
                "PATH": f"{binary}:{os.environ['PATH']}",
                "GITHUB_ENV": str(root / "github-env"),
            }
            result = subprocess.run(
                ["bash", str(SCRIPT)], cwd=root, env=environment, capture_output=True, text=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Could not determine actual Java and Gradle versions", result.stderr)


if __name__ == "__main__":
    unittest.main()
