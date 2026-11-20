#!/usr/bin/env python3
"""Pure cut decision for release branches.

A release branch `release/X.Y.x` is created from the last tag of the previous line when the next line ships on `main`
(openspec/changes/add-release-branches-and-backports/design.md, decision 2). This module decides, from the existing version
tags and the commit of the branch, what the cut job has to do. It never creates a branch and never moves one.

Decisions:

- `skip`: the release is not the first release of a line (patch part not 0), or there is no older line to keep.
- `create`: the branch does not exist. The job creates it at `base_sha`, the commit of the last tag of the previous line.
- `exists`: the branch already points at `base_sha`. A rerun does nothing.
- `refuse`: the release is older than an existing release, or the branch points elsewhere. The job fails and never moves the
  branch.

The tags are given as a mapping from tag name to the commit it points at. The standard library and `release_tags` only, so
the decision needs no network, no clock and no file.

The command line wires the decision into the `cut-release-branch` job of .github/workflows/release-please.yml:

- `cut VERSION` reads `git ls-remote --tags --heads` output on stdin and prints the decision as `key=value` lines for
  `$GITHUB_OUTPUT`. It exits 1 on a refusal.
- `add-line LINE DATE --file support-lines.json` lists the line that shipped on DATE as the latest line and opens the
  window of the line before it. The date is a parameter, so the script never reads the clock.
"""

import argparse
import copy
import datetime
import json
import sys
from dataclasses import dataclass
from typing import IO, Dict, List, Mapping, Optional, Tuple

from release_tags import VERSION_PATTERN, parse_version
from render_support_tables import WINDOW_DAYS


@dataclass(frozen=True)
class CutDecision:
    action: str
    branch: Optional[str] = None
    base_tag: Optional[str] = None
    base_sha: Optional[str] = None
    reason: str = ""


def _line_of(version: Tuple[int, int, int]) -> str:
    return f"{version[0]}.{version[1]}"


def decide_cut(released: str, tags: Mapping[str, str], branch_sha: Optional[str]) -> CutDecision:
    """Decides the cut for a release of `released` on `main`.

    `tags` maps every existing tag name to its commit. `branch_sha` is the commit of `release/<previous line>.x`, or None when
    that branch does not exist. Raises ValueError when `released` is not a release version.
    """
    version = parse_version(released)
    if version[2] != 0:
        return CutDecision(action="skip", reason=f"{released} is a patch release, a line starts at patch 0")

    versions = [(parse_version(tag), tag) for tag in tags if VERSION_PATTERN.match(tag)]
    if any(parsed > version for parsed, _ in versions):
        return CutDecision(action="refuse", reason=f"a release newer than {released} exists, only the newest release cuts a branch")

    older = [(parsed, tag) for parsed, tag in versions if parsed < version]
    if not older:
        return CutDecision(action="skip", reason=f"{released} has no previous line to keep receiving fixes")

    previous_line = _line_of(max(older)[0])
    base_tag = max(
        (parsed, tag) for parsed, tag in older if _line_of(parsed) == previous_line
    )[1]
    branch = f"release/{previous_line}.x"
    base_sha = tags[base_tag]

    if branch_sha is None:
        return CutDecision(action="create", branch=branch, base_tag=base_tag, base_sha=base_sha, reason=f"cut {branch} from {base_tag}")
    if branch_sha == base_sha:
        return CutDecision(action="exists", branch=branch, base_tag=base_tag, base_sha=base_sha, reason=f"{branch} already points at {base_tag}")
    return CutDecision(
        action="refuse",
        branch=branch,
        base_tag=base_tag,
        base_sha=base_sha,
        reason=f"{branch} exists and does not point at {base_tag}, the maintainer decides how to continue",
    )


def parse_ls_remote(listing: str) -> Tuple[Dict[str, str], Dict[str, str]]:
    """Reads `git ls-remote` output into tag name to commit and branch name to commit.

    An annotated tag appears twice; its peeled `^{}` entry names the commit and wins over the tag object.
    """
    tags: Dict[str, str] = {}
    heads: Dict[str, str] = {}
    peeled = set()
    for row in listing.splitlines():
        if "\t" not in row:
            continue
        sha, ref = row.strip().split("\t", 1)
        if ref.startswith("refs/heads/"):
            heads[ref[len("refs/heads/"):]] = sha
        elif ref.startswith("refs/tags/"):
            name = ref[len("refs/tags/"):]
            if name.endswith("^{}"):
                name = name[:-3]
                tags[name] = sha
                peeled.add(name)
            elif name not in peeled:
                tags[name] = sha
    return tags, heads


def add_line(lines: List[dict], line: str, released: str) -> List[dict]:
    """Returns the lines with `line` listed first as latest, released on `released`.

    The line that was latest becomes `previous`, and its window ends WINDOW_DAYS after `released`. A line that is already
    listed changes nothing, so a rerun never moves a window. The input is not modified.
    """
    if any(entry.get("line") == line for entry in lines):
        return copy.deepcopy(lines)
    end_of_life = datetime.date.fromisoformat(released) + datetime.timedelta(days=WINDOW_DAYS)
    updated = [{"line": line, "kind": "regular", "status": "latest", "released": released}]
    for entry in copy.deepcopy(lines):
        if entry.get("status") == "latest":
            entry["status"] = "previous"
            entry["end_of_life"] = end_of_life.isoformat()
        updated.append(entry)
    return updated


def format_lines(lines: List[dict]) -> str:
    """Writes the file with one entry per line, the layout support-lines.json is kept in."""
    entries = ["    { " + json.dumps(entry)[1:-1] + " }" for entry in lines]
    return '{\n  "lines": [\n' + ",\n".join(entries) + "\n  ]\n}\n"


def main(argv: List[str], stdin: Optional[IO[str]] = None, stdout: Optional[IO[str]] = None) -> int:
    """Runs one command for the cut job. See the module documentation."""
    parser = argparse.ArgumentParser(prog="release_branch.py", description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    cut = commands.add_parser("cut", help="decide the cut for a release, reading ls-remote output on stdin")
    cut.add_argument("version")
    add = commands.add_parser("add-line", help="list a new latest line in support-lines.json")
    add.add_argument("line")
    add.add_argument("released")
    add.add_argument("--file", required=True)
    args = parser.parse_args(argv)

    stdin = stdin if stdin is not None else sys.stdin
    stdout = stdout if stdout is not None else sys.stdout

    if args.command == "add-line":
        with open(args.file, encoding="utf-8") as handle:
            data = json.load(handle)
        data["lines"] = add_line(data["lines"], args.line, args.released)
        with open(args.file, "w", encoding="utf-8") as handle:
            handle.write(format_lines(data["lines"]))
        return 0

    tags, heads = parse_ls_remote(stdin.read())
    branch = decide_cut(args.version, tags, None).branch
    decision = decide_cut(args.version, tags, heads.get(branch) if branch else None)
    outputs = {
        "action": decision.action,
        "branch": decision.branch or "",
        "base_tag": decision.base_tag or "",
        "base_sha": decision.base_sha or "",
        "line": decision.branch[len("release/"):-len(".x")] if decision.branch else "",
        "new_line": _line_of(parse_version(args.version)),
        "reason": decision.reason,
    }
    for key, value in outputs.items():
        stdout.write(f"{key}={value}\n")
    return 1 if decision.action == "refuse" else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
