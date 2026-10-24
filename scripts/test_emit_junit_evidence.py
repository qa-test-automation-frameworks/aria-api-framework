#!/usr/bin/env python3
"""Adversarial unit checks for the JUnit evidence adapter."""

import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).with_name("emit_junit_evidence.py")
SPEC = importlib.util.spec_from_file_location("emit_junit_evidence", MODULE)
EMITTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EMITTER)


class JunitEvidenceTests(unittest.TestCase):
    def test_attempted_records_require_actual_java_and_gradle_versions(self):
        self.assertEqual(
            EMITTER.tool_versions({"JAVA_VERSION": "21.0.8", "GRADLE_VERSION": "8.7"}, True),
            {"java": "21.0.8", "gradle": "8.7"},
        )
        with self.assertRaisesRegex(EMITTER.EvidenceError, "actual Java/Gradle"):
            EMITTER.tool_versions({"JAVA_VERSION": "21.0.8", "GRADLE_VERSION": "unknown"}, True)
        self.assertEqual(
            EMITTER.tool_versions({}, False), {"java": "unknown", "gradle": "unknown"}
        )

    def write(self, root, contents):
        root.mkdir(parents=True, exist_ok=True)
        (root / "TEST-suite.xml").write_text(contents, encoding="utf-8")

    def test_reconciles_native_pass_skip_and_redacts_case_data(self):
        with tempfile.TemporaryDirectory() as temp:
            reports = Path(temp)
            self.write(reports, '<testsuite tests="2" failures="0" errors="0" skipped="1"><testcase name="secret-case"/><testcase name="skipped"><skipped/></testcase></testsuite>')
            counts, digest = EMITTER.parse_reports(reports)
            self.assertEqual(counts, {"selected": 2, "executed": 1, "passed": 1, "failed": 0, "skipped": 1, "retried": 0})
            self.assertEqual(len(digest), 64)

    def test_failure_is_a_complete_native_outcome(self):
        with tempfile.TemporaryDirectory() as temp:
            reports = Path(temp)
            self.write(reports, '<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase><failure>credential-redaction-sentinel</failure></testcase></testsuite>')
            counts, _ = EMITTER.parse_reports(reports)
            self.assertEqual(counts["failed"], 1)
            self.assertEqual(counts["passed"], 0)

    def test_rejects_missing_malformed_and_inconsistent_reports(self):
        with tempfile.TemporaryDirectory() as temp:
            reports = Path(temp)
            with self.assertRaisesRegex(EMITTER.EvidenceError, "No native"):
                EMITTER.parse_reports(reports)
            self.write(reports, '<testsuite>')
            with self.assertRaisesRegex(EMITTER.EvidenceError, "malformed"):
                EMITTER.parse_reports(reports)
            self.write(reports, '<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase/></testsuite>')
            with self.assertRaisesRegex(EMITTER.EvidenceError, "do not reconcile"):
                EMITTER.parse_reports(reports)

    def test_rejects_doctype_and_zero_case_xml(self):
        with tempfile.TemporaryDirectory() as temp:
            reports = Path(temp)
            self.write(reports, '<!DOCTYPE x><testsuite tests="1" failures="0" errors="0" skipped="0"><testcase/></testsuite>')
            with self.assertRaisesRegex(EMITTER.EvidenceError, "forbidden"):
                EMITTER.parse_reports(reports)
            self.write(reports, '<testsuite tests="0" failures="0" errors="0" skipped="0"/>')
            with self.assertRaisesRegex(EMITTER.EvidenceError, "no selected"):
                EMITTER.parse_reports(reports)

    def test_expected_failure_control_is_visible_but_not_a_readiness_gate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            reports = root / "reports"
            self.write(reports, '<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase><failure>intentional</failure></testcase></testsuite>')
            output = root / "record.json"
            env = {
                "GITHUB_SHA": "a" * 40,
                "GITHUB_RUN_ID": "42",
                "GITHUB_RUN_ATTEMPT": "1",
                "GITHUB_REPOSITORY": "qa-test-automation-frameworks/aria-api-framework",
                "GITHUB_REF_NAME": "main",
                "GITHUB_EVENT_NAME": "push",
                "EVIDENCE_STARTED_AT": "2026-10-17T00:00:00Z",
                "JAVA_VERSION": "21.0.8",
                "GRADLE_VERSION": "8.7",
            }
            with patch.dict(os.environ, env, clear=True), patch.object(
                sys, "argv", ["emit_junit_evidence.py", "--reports", str(reports), "--output", str(output), "--scope", "aria.evidence-failure-control"]
            ):
                self.assertEqual(EMITTER.main(), 0)
            record = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(record["execution"]["disposition"], "failed")
            self.assertEqual(record["execution"]["counts"]["failed"], 1)
            self.assertFalse(record["scope"]["required"])


    def test_preserves_explicit_shared_target_identity_and_versions(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            reports = root / "reports"
            output = root / "shared-app.json"
            self.write(reports, '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase/></testsuite>')
            environment = {
                "GITHUB_SHA": "a" * 40,
                "GITHUB_RUN_ID": "12345",
                "GITHUB_RUN_ATTEMPT": "1",
                "GITHUB_REPOSITORY": "qa-test-automation-frameworks/aria-api-framework",
                "GITHUB_REF_NAME": "main",
                "GITHUB_EVENT_NAME": "push",
                "EVIDENCE_STARTED_AT": "2026-10-17T00:00:00Z",
                "JAVA_VERSION": "21.0.8",
                "GRADLE_VERSION": "8.14.3",
                "EVIDENCE_TARGET_IDENTITY": "quality-reference-application",
                "EVIDENCE_TARGET_REVISION": "sha256:" + "b" * 64,
                "EVIDENCE_FIXTURE_VERSION": "v0.1.0",
                "EVIDENCE_TARGET_ENVIRONMENT": "isolated-github-actions",
            }
            with patch.dict("os.environ", environment, clear=True), patch("sys.argv", [
                "emit_junit_evidence.py", "--reports", str(reports), "--output", str(output),
                "--scope", "aria.shared-app",
            ]):
                self.assertEqual(EMITTER.main(), 0)

            record = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(record["scope"]["id"], "aria.shared-app")
            self.assertEqual(record["target"]["identity"], "quality-reference-application")
            self.assertEqual(record["target"]["revision"], "sha256:" + "b" * 64)
            self.assertEqual(record["target"]["fixtureVersion"], "v0.1.0")
            self.assertEqual(record["execution"]["disposition"], "passed")

    def test_workflow_identity_comes_from_the_running_workflow(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            reports = root / "reports"
            output = root / "live.json"
            self.write(reports, '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase/></testsuite>')
            environment = {
                "GITHUB_SHA": "a" * 40,
                "GITHUB_RUN_ID": "7",
                "GITHUB_RUN_ATTEMPT": "1",
                "GITHUB_REPOSITORY": "qa-test-automation-frameworks/aria-api-framework",
                "GITHUB_REF_NAME": "main",
                "GITHUB_EVENT_NAME": "schedule",
                "GITHUB_WORKFLOW": "ARIA live smoke (external dependency)",
                "GITHUB_WORKFLOW_REF": "qa-test-automation-frameworks/aria-api-framework/.github/workflows/live-smoke.yml@refs/heads/main",
                "EVIDENCE_STARTED_AT": "2026-10-17T00:00:00Z",
                "JAVA_VERSION": "21.0.8",
                "GRADLE_VERSION": "8.7",
            }
            with patch.dict(os.environ, environment, clear=True), patch.object(
                sys, "argv", ["emit_junit_evidence.py", "--reports", str(reports), "--output", str(output), "--scope", "aria.live-smoke"]
            ):
                self.assertEqual(EMITTER.main(), 0)
            workflow = json.loads(output.read_text(encoding="utf-8"))["workflow"]
            self.assertTrue(workflow["id"].endswith("/.github/workflows/live-smoke.yml@refs/heads/main"))
            self.assertEqual(workflow["name"], "ARIA live smoke (external dependency)")


if __name__ == "__main__":
    unittest.main()
