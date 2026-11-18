#!/usr/bin/env python3
"""Counts the findings of the Ingot plugin API report by group and prints a table for the job summary.

The report is the Markdown file written by ingotPluginApiReport. It has a "## Promised" section and a "## Experimental"
section. Each finding is a top-level bullet ("- ") in its section; nested bullets are details and are not counted. A
section that says it has no findings counts zero. A report with neither section is rejected, so that a missing or
unreadable report is never read as "no findings".

Usage:

    python3 .github/scripts/plugin_api_summary.py <path-to-ingot-report.md> >> "$GITHUB_STEP_SUMMARY"

Experimental findings are reported for information and never block; only the promised group can block later.
"""

import sys

PROMISED = "Promised"
EXPERIMENTAL = "Experimental"


def count_groups(markdown):
    """Returns {"promised": n, "experimental": n} for a report. Raises ValueError when neither section is present."""
    counts = {}
    current = None
    for line in markdown.splitlines():
        if line.startswith("## "):
            heading = line[3:].strip()
            current = heading if heading in (PROMISED, EXPERIMENTAL) else None
            if current is not None:
                counts[current] = 0
            continue
        if current is not None and line.startswith("- "):
            counts[current] += 1
    if PROMISED not in counts and EXPERIMENTAL not in counts:
        raise ValueError(f"the report has no '## {PROMISED}' or '## {EXPERIMENTAL}' section")
    return {
        "promised": counts.get(PROMISED, 0),
        "experimental": counts.get(EXPERIMENTAL, 0),
    }


def summary_table(counts):
    return "\n".join([
        "| Group | Findings |",
        "| --- | --- |",
        f"| Promised | {counts['promised']} |",
        f"| Experimental | {counts['experimental']} |",
        "",
        "Experimental findings are reported for information and never block.",
        "",
    ])


def main(argv):
    if len(argv) != 1:
        print("usage: plugin_api_summary.py <ingot-report.md>", file=sys.stderr)
        return 2
    with open(argv[0], encoding="utf-8") as handle:
        markdown = handle.read()
    try:
        counts = count_groups(markdown)
    except ValueError as error:
        print(f"cannot summarise {argv[0]}: {error}", file=sys.stderr)
        return 1
    print(summary_table(counts), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
