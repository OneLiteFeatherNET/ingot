#!/usr/bin/env python3
"""Pure end of window decision for release lines.

A line with status `previous` receives security fixes until its `end_of_life` date in support-lines.json
(openspec/changes/add-release-branches-and-backports/design.md, decision 8). On that date the `end-of-life` job of
.github/workflows/release-please.yml opens the pull request that archives the line, and once the archived line is on main
it renames `release/<line>.x` to `archive/<line>.x`.

The date is always a parameter, so nothing here reads the clock, the network or any file other than the one named.

Commands:

- `due --today DATE --file support-lines.json` prints every open line whose window has ended on or before DATE.
- `archive LINE --file support-lines.json` sets the line to `archived` and keeps the file layout.
- `archived --file support-lines.json` prints every archived line.
"""

import argparse
import copy
import datetime
import json
import sys
from typing import IO, List, Optional


def due_lines(lines: List[dict], today: str) -> List[str]:
    """Returns the open lines whose end of life date is `today` or earlier. Raises ValueError for a malformed date."""
    day = datetime.date.fromisoformat(today)
    return [
        entry["line"]
        for entry in lines
        if entry.get("status") == "previous" and datetime.date.fromisoformat(entry["end_of_life"]) <= day
    ]


def archive(lines: List[dict], line: str) -> List[dict]:
    """Returns the lines with `line` set to `archived`. The end of life date stays. The input is not modified."""
    updated = copy.deepcopy(lines)
    for entry in updated:
        if entry.get("line") == line:
            entry["status"] = "archived"
    return updated


def archived_lines(lines: List[dict]) -> List[str]:
    """Returns the lines whose window has ended."""
    return [entry["line"] for entry in lines if entry.get("status") == "archived"]


def format_lines(lines: List[dict]) -> str:
    """Writes the file with one entry per line, the layout support-lines.json is kept in."""
    entries = ["    { " + json.dumps(entry)[1:-1] + " }" for entry in lines]
    return '{\n  "lines": [\n' + ",\n".join(entries) + "\n  ]\n}\n"


def main(argv: List[str], stdout: Optional[IO[str]] = None) -> int:
    """Runs one command for the end-of-life job. See the module documentation."""
    parser = argparse.ArgumentParser(prog="support_window.py", description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    due = commands.add_parser("due", help="print the open lines whose window has ended")
    due.add_argument("--today", required=True)
    due.add_argument("--file", required=True)
    close = commands.add_parser("archive", help="set a line to archived")
    close.add_argument("line")
    close.add_argument("--file", required=True)
    closed = commands.add_parser("archived", help="print the archived lines")
    closed.add_argument("--file", required=True)
    args = parser.parse_args(argv)

    stdout = stdout if stdout is not None else sys.stdout
    with open(args.file, encoding="utf-8") as handle:
        data = json.load(handle)

    if args.command == "archive":
        data["lines"] = archive(data["lines"], args.line)
        with open(args.file, "w", encoding="utf-8") as handle:
            handle.write(format_lines(data["lines"]))
        return 0

    names = due_lines(data["lines"], args.today) if args.command == "due" else archived_lines(data["lines"])
    for name in names:
        stdout.write(name + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
