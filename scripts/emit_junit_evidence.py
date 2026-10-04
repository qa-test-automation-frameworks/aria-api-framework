#!/usr/bin/env python3
"""Emit a sanitized schema-v3 record from one Gradle JUnit XML task directory."""

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAX_REPORT_BYTES = 20_000_000
SHA = re.compile(r"^[0-9a-f]{40}$")


class EvidenceError(Exception):
    pass


def parse_reports(directory: Path):
    paths = sorted(directory.glob("TEST-*.xml"))
    if not paths:
        raise EvidenceError("No native JUnit XML reports were found")
    selected = passed = failed = skipped = retried = 0
    digest = hashlib.sha256()
    for path in paths:
        raw = path.read_bytes()
        if len(raw) > MAX_REPORT_BYTES or b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
            raise EvidenceError("Native JUnit XML is oversized or contains a forbidden declaration")
        try:
            root = ET.fromstring(raw)
        except ET.ParseError as exc:
            raise EvidenceError("Native JUnit XML is malformed") from exc
        suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
        if not suites or root.tag not in {"testsuite", "testsuites"}:
            raise EvidenceError("Unexpected JUnit XML root or no test suites")
        for suite in suites:
            cases = suite.findall("testcase")
            try:
                expected = {key: int(suite.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
            except (KeyError, ValueError) as exc:
                raise EvidenceError("JUnit suite is missing valid aggregate counters") from exc
            local_selected = len(cases)
            local_failed = sum(case.find("failure") is not None for case in cases)
            local_errors = sum(case.find("error") is not None for case in cases)
            local_skipped = sum(case.find("skipped") is not None for case in cases)
            if (local_selected, local_failed, local_errors, local_skipped) != (
                expected["tests"], expected["failures"], expected["errors"], expected["skipped"]
            ):
                raise EvidenceError("JUnit aggregate counters do not reconcile to testcase elements")
            if any(case.find("failure") is not None and case.find("error") is not None for case in cases):
                raise EvidenceError("A testcase cannot be both failure and error")
            selected += local_selected
            failed += local_failed + local_errors
            skipped += local_skipped
            retried += sum(any(child.tag in {"flakyFailure", "flakyError"} for child in case) for case in cases)
        digest.update(path.name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(raw)
    if selected == 0:
        raise EvidenceError("Native JUnit reports contain no selected testcases")
    passed = selected - failed - skipped
    if passed < 0:
        raise EvidenceError("Native outcomes exceed selected testcases")
    return {"selected": selected, "executed": selected - skipped, "passed": passed,
            "failed": failed, "skipped": skipped, "retried": retried}, digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--scope", required=True)
    parser.add_argument("--reason", default=None, help="Emit a no-execution skip record")
    args = parser.parse_args()
    env = os.environ
    source = env.get("GITHUB_SHA", "")
    run_id = env.get("GITHUB_RUN_ID", "")
    attempt = env.get("GITHUB_RUN_ATTEMPT", "")
    repo = env.get("GITHUB_REPOSITORY", "")
    started = env.get("EVIDENCE_STARTED_AT")
    if not SHA.fullmatch(source) or not run_id.isdigit() or int(run_id) < 1 or not attempt.isdigit() or int(attempt) < 1:
        raise EvidenceError("GitHub run/source identity is missing or invalid")
    if repo != "qa-test-automation-frameworks/aria-api-framework":
        raise EvidenceError("Unexpected repository identity")
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    record = {
        "schemaVersion": 3, "repository": repo, "sourceSha": source,
        "limitations": ["JUnit testcase names, parameters, failure text and environment values are omitted.",
                        "Retries are counted only when native flakyFailure/flakyError elements are present."],
        "kind": "execution",
        "workflow": {"id": f"{repo}/.github/workflows/ci.yml@refs/heads/{env.get('GITHUB_REF_NAME', 'unknown')}",
                     "name": "ARIA API Framework CI", "runId": run_id, "attempt": int(attempt),
                     "url": f"https://github.com/{repo}/actions/runs/{run_id}",
                     "trigger": env.get("GITHUB_EVENT_NAME", "unknown"), "branch": env.get("GITHUB_REF_NAME", "unknown")},
        "scope": {"id": args.scope, "evidenceClass": "controlled" if args.scope != "aria.live-smoke" else "credentialed",
                  "required": args.scope != "aria.live-smoke", "freshnessPolicy": "daily_gate_v1" if args.scope != "aria.live-smoke" else "quarterly_experiment_v1"},
        "target": {"identity": "aria-owned-provider", "revision": None, "fixtureVersion": None,
                   "environment": env.get("ENV", "dev"), "profile": None,
                   "tools": {"java": env.get("JAVA_VERSION", "unknown"), "gradle": env.get("GRADLE_VERSION", "unknown")}},
        "execution": {"disposition": "skipped" if args.reason else "unavailable", "reason": args.reason or "Evidence collection failed",
                      "startedAt": None, "completedAt": None, "integrity": "unavailable",
                      "counts": {"unit": "parameter_case", "selected": None, "executed": None, "passed": None, "failed": None, "skipped": None, "retried": None,
                                 "semantics": "JUnit XML testcase invocations; selected includes skips; errors are combined with failures."},
                      "measurements": [], "shards": {"expected": [args.scope], "received": []}},
        "publication": {"disposition": "pending", "reason": None, "publishedAt": None, "reportUrl": None, "artifacts": []},
        "generator": {"name": "aria-gradle-junit-evidence", "version": "1.0.0"}}
    if args.reason is None:
        try:
            counts, xml_digest = parse_reports(args.reports)
            if not started:
                raise EvidenceError("Attempted execution has no recorded start timestamp")
            record["execution"].update({"disposition": "failed" if counts["failed"] else "passed",
                                        "reason": "Native tests reported failing outcomes" if counts["failed"] else None,
                                        "startedAt": started, "completedAt": now, "integrity": "complete",
                                        "counts": {**counts, "unit": "parameter_case", "semantics": record["execution"]["counts"]["semantics"]},
                                        "measurements": [], "shards": {"expected": [args.scope], "received": [args.scope]}})
            record["limitations"].append(f"Native JUnit report bundle SHA-256: {xml_digest}.")
        except EvidenceError as exc:
            record["execution"]["reason"] = str(exc)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    return 0 if record["execution"]["integrity"] == "complete" or args.reason else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (EvidenceError, OSError) as exc:
        print(f"evidence emission failed: {exc}", file=sys.stderr)
        sys.exit(1)
