#!/usr/bin/env python3
"""Require one source-bound, native, intentionally failing evidence control."""

import argparse
import json
import re
import sys
from pathlib import Path

from emit_junit_evidence import parse_reports


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("record", type=Path)
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    record = json.loads(args.record.read_text(encoding="utf-8"))
    execution = record.get("execution", {})
    counts = execution.get("counts", {})
    actual_digest = re.search(r"Native JUnit report bundle SHA-256: ([0-9a-f]{64})\.", " ".join(record.get("limitations", [])))
    native_counts, native_digest = parse_reports(args.reports)
    checks = {
        "source SHA": record.get("sourceSha") == args.source,
        "run ID": record.get("workflow", {}).get("runId") == args.run_id,
        "scope": record.get("scope", {}).get("id") == "aria.evidence-failure-control",
        "complete failed disposition": execution.get("disposition") == "failed" and execution.get("integrity") == "complete",
        "one selected and failed case": counts.get("selected") == 1 and counts.get("failed") == 1 and counts.get("passed") == 0 and counts.get("skipped") == 0,
        "exact native input digest": actual_digest is not None and actual_digest.group(1) == native_digest,
        "native counts reconcile": native_counts == {"selected": 1, "executed": 1, "passed": 0, "failed": 1, "skipped": 0, "retried": 0},
    }
    errors = [name for name, passed in checks.items() if not passed]
    if errors:
        print("Invalid failure control evidence: " + ", ".join(errors), file=sys.stderr)
        return 1
    print("Validated one intentional native JUnit failure bound to the exact source and run.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
