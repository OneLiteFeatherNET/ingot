#!/usr/bin/env python3
"""Renders the release line tables of SUPPORT.md and .github/SECURITY.md from support-lines.json.

The file lists the release lines newest first. The first line is the latest one. Every other line has a status of
`previous` (its security window is open) or `archived` (its window has ended), and an end of life date. A regular line
ends 90 days after the first release of its successor, which is the line listed before it. An LTS line ends later than
that date. The check enforces these rules, so a window can only change in the one file.

Modes:

- `check` (the default) validates the file and fails when a table between the markers differs from the rendered one.
- `write` validates the file and replaces the text between the markers with the rendered tables.
- `labels` validates the file and prints `backport:<line> release/<line>.x` for every open previous line, one per line.

The script uses the standard library only, reads no clock and makes no network call. Run from the repository root:

    python3 .github/scripts/render_support_tables.py --mode check

Exit codes: 0 when the check passes or the write succeeds, 1 when the file or a table is invalid, 2 for a usage error.
"""

import argparse
import datetime
import json
import os
import re
import sys

WINDOW_DAYS = 90
KINDS = ("regular", "lts")
STATUSES = ("latest", "previous", "archived")
LINE_PATTERN = re.compile(r"^[0-9]+\.[0-9]+$")
MODES = ("check", "write", "labels")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LINES_FILE = "support-lines.json"

# Each target is a markdown file, the two markers around its table and the renderer that produces the table.
SUPPORT_TARGET = ("SUPPORT.md", "<!-- support-table:start -->", "<!-- support-table:end -->")
SECURITY_TARGET = (".github/SECURITY.md", "<!-- security-table:start -->", "<!-- security-table:end -->")

SUPPORT_TAIL = [("Older lines", "None", "Not supported")]
SECURITY_TAIL = [
    ("Ingot `main`", "Security fixes"),
    ("Older Ingot release lines", "Not supported"),
    ("Upstream Reposilite 3.x", "Not supported here, report to the upstream project"),
    ("Upstream Reposilite < 3.x", "Not supported"),
]


class MarkerError(Exception):
    """A markdown file does not contain exactly one pair of markers."""


def parse_date(value, context, errors):
    try:
        return datetime.date.fromisoformat(value)
    except (TypeError, ValueError):
        errors.append(f"{context}: '{value}' is not an ISO date")
        return None


def check_lines(lines):
    """Returns a list of error messages. An empty list means the lines are valid."""
    errors = []
    if not lines:
        return ["support-lines.json lists no lines"]

    seen = set()
    for entry in lines:
        name = entry.get("line", "")
        if not isinstance(name, str) or not LINE_PATTERN.match(name):
            errors.append(f"'{name}' is not a line of the form MAJOR.MINOR")
        if name in seen:
            errors.append(f"{name}: listed more than once")
        seen.add(name)
        if entry.get("kind") not in KINDS:
            errors.append(f"{name}: kind must be one of {', '.join(KINDS)}")
        if entry.get("status") not in STATUSES:
            errors.append(f"{name}: status must be one of {', '.join(STATUSES)}")
        parse_date(entry.get("released"), f"{name}: released", errors)

    if lines[0].get("status") != "latest":
        errors.append(f"{lines[0].get('line')}: the first listed line must have status latest")
    for entry in lines[1:]:
        if entry.get("status") == "latest":
            errors.append(f"{entry.get('line')}: only the first listed line may have status latest")

    for index, entry in enumerate(lines):
        name = entry.get("line")
        if entry.get("status") == "latest":
            if "end_of_life" in entry:
                errors.append(f"{name}: the latest line has no end of life date")
            continue
        if "end_of_life" not in entry:
            errors.append(f"{name}: end_of_life is required for a line that is not latest")
            continue
        if index == 0:
            continue
        successor = lines[index - 1]
        successor_release = parse_date(successor.get("released"), f"{successor.get('line')}: released", errors)
        end_of_life = parse_date(entry.get("end_of_life"), f"{name}: end_of_life", errors)
        if successor_release is None or end_of_life is None:
            continue
        regular_end = successor_release + datetime.timedelta(days=WINDOW_DAYS)
        if entry.get("kind") == "regular" and end_of_life != regular_end:
            errors.append(
                f"{name}: end_of_life must be {regular_end.isoformat()}, "
                f"{WINDOW_DAYS} days after {successor.get('line')}.0 was released"
            )
        if entry.get("kind") == "lts" and end_of_life <= regular_end:
            errors.append(f"{name}: an lts end_of_life must be later than {regular_end.isoformat()}")
    return errors


def successor_of(lines, index):
    return lines[index - 1]["line"]


def support_row(lines, index):
    entry = lines[index]
    name = entry["line"]
    if entry["status"] == "latest":
        return (f"`{name}.x` (latest)", "Every fix, bugs and security", "Supported")
    if entry["status"] == "previous":
        return (
            f"`{name}.x`",
            "Security fixes only",
            f"Security window ends on {entry['end_of_life']} (90 days after {successor_of(lines, index)}.0)",
        )
    return (
        f"`{name}.x`",
        "None",
        f"Window ended on {entry['end_of_life']} (90 days after {successor_of(lines, index)}.0)",
    )


def security_row(lines, index):
    entry = lines[index]
    name = entry["line"]
    if entry["status"] == "latest":
        return (f"Ingot `{name}.x` (latest)", "Every fix")
    if entry["status"] == "previous":
        return (f"Ingot `{name}.x`", f"Security fixes only, window ends on {entry['end_of_life']}")
    return (f"Ingot `{name}.x`", f"Window ended on {entry['end_of_life']}")


def markdown_table(headers, rows):
    widths = [max(len(cell) for cell in column) for column in zip(headers, *rows)]

    def format_row(cells):
        return "| " + " | ".join(cell.ljust(width) for cell, width in zip(cells, widths)) + " |"

    separator = "|" + "|".join(":" + "-" * (width + 1) for width in widths) + "|"
    return "\n".join([format_row(headers), separator, *(format_row(row) for row in rows)])


def render_support_table(lines):
    rows = [support_row(lines, index) for index in range(len(lines))]
    rows.extend(SUPPORT_TAIL)
    return markdown_table(("Release line", "Fixes received", "Status"), rows)


def render_security_table(lines):
    rows = [security_row(lines, index) for index in range(len(lines))]
    rows.extend(SECURITY_TAIL)
    return markdown_table(("Version", "Supported"), rows)


def open_branches(lines):
    """The backport label and the release branch of every open line that is not the latest one."""
    return [
        (f"backport:{entry['line']}", f"release/{entry['line']}.x")
        for entry in lines
        if entry["status"] == "previous"
    ]


def replace_block(text, begin, end, body):
    if text.count(begin) != 1 or text.count(end) != 1:
        raise MarkerError(f"expected exactly one '{begin}' and one '{end}'")
    start = text.index(begin) + len(begin)
    stop = text.index(end)
    if start > stop:
        raise MarkerError(f"'{end}' comes before '{begin}'")
    return text[:start] + "\n" + body + "\n" + text[stop:]


def load_lines(root):
    with open(os.path.join(root, LINES_FILE), encoding="utf-8") as handle:
        data = json.load(handle)
    return data["lines"]


def main(argv=None):
    parser = argparse.ArgumentParser(description="Render the support tables from support-lines.json.")
    parser.add_argument("--root", default=REPO_ROOT, help="repository root that holds support-lines.json")
    parser.add_argument("--mode", default="check", choices=MODES)
    args = parser.parse_args(argv)

    try:
        lines = load_lines(args.root)
    except (OSError, ValueError, KeyError) as error:
        print(f"error: cannot read {LINES_FILE}: {error}", file=sys.stderr)
        return 1

    errors = check_lines(lines)
    if errors:
        for error in errors:
            print(f"error: {LINES_FILE}: {error}", file=sys.stderr)
        return 1

    if args.mode == "labels":
        for label, branch in open_branches(lines):
            print(f"{label} {branch}")
        return 0

    tables = [
        (SUPPORT_TARGET, render_support_table(lines)),
        (SECURITY_TARGET, render_security_table(lines)),
    ]
    results = []
    for (path, begin, end), body in tables:
        full_path = os.path.join(args.root, path)
        try:
            with open(full_path, encoding="utf-8") as handle:
                text = handle.read()
            updated = replace_block(text, begin, end, body)
        except (OSError, MarkerError) as error:
            print(f"error: {path}: {error}", file=sys.stderr)
            return 1
        results.append((path, full_path, text, updated))

    if args.mode == "check":
        stale = [path for path, _, text, updated in results if text != updated]
        for path in stale:
            print(f"error: {path} differs from {LINES_FILE}, run render_support_tables.py --mode write", file=sys.stderr)
        return 1 if stale else 0

    for _, full_path, text, updated in results:
        if text != updated:
            with open(full_path, "w", encoding="utf-8") as handle:
                handle.write(updated)
    return 0


if __name__ == "__main__":
    sys.exit(main())
