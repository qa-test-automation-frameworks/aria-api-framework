#!/usr/bin/env python3
"""Validate that an intentional provider regression is detected and replayed exactly."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def read_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise SystemExit(f"Expected a JSON object in {path}")
    return value


def validate_junit(directory: Path, command_index: int) -> int:
    reports = sorted(directory.rglob("TEST-*.xml"))
    if not reports:
        raise SystemExit(f"No native JUnit reports were retained under {directory}")
    matching_failures = 0
    for report in reports:
        root = ET.parse(report).getroot()
        for case in root.iter("testcase"):
            for failure in case:
                if failure.tag not in {"failure", "error"}:
                    continue
                message = failure.attrib.get("message", "") + "\n" + (failure.text or "")
                if f"F2 divergence at command {command_index}" in message:
                    matching_failures += 1
    if matching_failures != 1:
        raise SystemExit(
            f"Expected one native JUnit failure at command {command_index}, found {matching_failures}"
        )
    return matching_failures


def validate(args: argparse.Namespace) -> dict:
    if args.initial_exit == 0 or args.replay_exit == 0:
        raise SystemExit("Both the injected regression and its replay must fail the model assertion")
    initial = read_json(args.receipt)
    replay = read_json(args.replay_receipt)
    for name, receipt in (("initial", initial), ("replay", replay)):
        if receipt.get("schemaVersion") != 1:
            raise SystemExit(f"The {name} receipt has an unsupported schema")
        if receipt.get("firstFailingCommandIndex") != args.expected_index:
            raise SystemExit(f"The {name} receipt failed at a different command")
        if receipt.get("frameworkRevision") != args.framework_revision:
            raise SystemExit(f"The {name} receipt names the wrong framework revision")
        if receipt.get("targetRevision") != args.target_revision:
            raise SystemExit(f"The {name} receipt names the wrong target revision")
        if receipt.get("targetDigest") != args.target_digest:
            raise SystemExit(f"The {name} receipt names the wrong image digest")
        commands = receipt.get("commands")
        if not isinstance(commands, list) or len(commands) != args.expected_index + 1:
            raise SystemExit(f"The {name} receipt does not stop at its first divergent command")
        if [item.get("commandIndex") for item in commands] != list(range(args.expected_index + 1)):
            raise SystemExit(f"The {name} receipt command prefix is incomplete or out of order")
        coverage = receipt.get("coverage")
        if not isinstance(coverage, dict) or sum(coverage.get("commandTypes", {}).values()) != len(commands):
            raise SystemExit(f"The {name} receipt does not retain command coverage")
        if not receipt.get("divergence"):
            raise SystemExit(f"The {name} receipt omits the expected/observed difference")

    if initial["seed"] != replay["seed"] or initial["apiVersion"] != replay["apiVersion"]:
        raise SystemExit("The replay changed the original seed or API version")
    if initial["baselineAliases"] != replay["baselineAliases"]:
        raise SystemExit("The replay did not start from the same seeded baseline aliases")
    if initial["divergence"] != replay["divergence"]:
        raise SystemExit("The replay did not reproduce the same sanitized divergence")
    if [item["command"] for item in initial["commands"]] != [item["command"] for item in replay["commands"]]:
        raise SystemExit("The replay did not preserve the original command prefix")

    result = initial["commands"][args.expected_index]
    command = result["command"]
    expected = result["expected"]
    observed = result["observed"]
    if args.name == "allow-terminal-reopen":
        if not (
            command.get("type") == "TRANSITION"
            and command.get("actorAlias") == "reviewer"
            and command.get("claimAlias") == "a-seed"
            and command.get("targetStatus") == "under_review"
            and expected.get("status") == 409
            and expected.get("errorCode") == "INVALID_TRANSITION"
            and observed.get("status") == 200
            and observed.get("claim", {}).get("status") == "under_review"
        ):
            raise SystemExit("The terminal-transition candidate did not fail at its named model command")
    elif args.name == "allow-foreign-member-read":
        if not (
            command.get("type") == "READ"
            and command.get("actorAlias") == "member-b"
            and command.get("claimAlias") == "a-seed"
            and expected.get("status") == 404
            and expected.get("errorCode") == "RESOURCE_NOT_FOUND"
            and observed.get("status") == 200
            and observed.get("claim", {}).get("status") == "submitted"
        ):
            raise SystemExit("The ownership candidate did not fail at its named model command")
    else:
        raise SystemExit(f"Unknown claim model mutation control {args.name!r}")

    initial_failures = validate_junit(args.initial_junit, args.expected_index)
    replay_failures = validate_junit(args.replay_junit, args.expected_index)
    summary = {
        "schemaVersion": 1,
        "control": args.name,
        "frameworkRevision": args.framework_revision,
        "targetRevision": args.target_revision,
        "targetDigest": args.target_digest,
        "seed": initial["seed"],
        "apiVersion": initial["apiVersion"],
        "firstFailingCommandIndex": args.expected_index,
        "commandType": command["type"],
        "actorAlias": command["actorAlias"],
        "claimAlias": command.get("claimAlias"),
        "targetStatus": command.get("targetStatus"),
        "expectedStatus": expected["status"],
        "expectedErrorCode": expected.get("errorCode"),
        "observedStatus": observed["status"],
        "observedErrorCode": observed.get("errorCode"),
        "replayedSameDivergence": True,
        "nativeJUnitFailureCount": initial_failures,
        "replayJUnitFailureCount": replay_failures,
    }
    args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--replay-receipt", type=Path, required=True)
    parser.add_argument("--initial-junit", type=Path, required=True)
    parser.add_argument("--replay-junit", type=Path, required=True)
    parser.add_argument("--initial-exit", type=int, required=True)
    parser.add_argument("--replay-exit", type=int, required=True)
    parser.add_argument("--expected-index", type=int, required=True)
    parser.add_argument("--framework-revision", required=True)
    parser.add_argument("--target-revision", required=True)
    parser.add_argument("--target-digest", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    summary = validate(args)
    print(
        f"PASS: {summary['control']} failed and replayed command "
        f"{summary['firstFailingCommandIndex']} with native JUnit evidence"
    )


if __name__ == "__main__":
    main()
