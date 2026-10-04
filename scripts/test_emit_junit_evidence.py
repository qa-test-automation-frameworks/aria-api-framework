#!/usr/bin/env python3
"""Adversarial unit checks for the JUnit evidence adapter."""

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("emit_junit_evidence.py")
SPEC = importlib.util.spec_from_file_location("emit_junit_evidence", MODULE)
EMITTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EMITTER)


class JunitEvidenceTests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
