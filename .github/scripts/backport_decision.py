#!/usr/bin/env python3
"""Pure backport decision for merged pull requests.

A merged pull request into `main` that carries a label `backport:<major>.<minor>` may reach that line's release branch
(openspec/changes/add-release-branches-and-backports/specs/backports/spec.md). Only a security fix is eligible: its title is
`fix(security): ...` without a breaking marker. Every other change stays on `main` and is refused with the reason the source
pull request should carry as a comment.

The module takes the title, body, labels and base of the pull request and the lines that have a release branch. It never
reads the network, the clock or the file system. The command line reads the same inputs as one JSON object on stdin and
prints one tab separated line per decision (action, line, branch, reason) for .github/workflows/backport.yml.
"""

import json
import re
import sys
from dataclasses import dataclass
from typing import IO, Iterable, List, Optional, Sequence

BACKPORT_LABEL_PREFIX = "backport:"
LINE_PATTERN = re.compile(r"^backport:((?:0|[1-9]\d*)\.(?:0|[1-9]\d*))$")
TITLE_PATTERN = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[^()\s]+)\))?(?P<bang>!)?: (?P<summary>\S.*)$")
BREAKING_FOOTER_PATTERN = re.compile(r"^BREAKING[ -]CHANGE:", re.MULTILINE)

REASON_NOT_CONVENTIONAL = "the title is not a Conventional Commit, so the change cannot be classified"
REASON_BREAKING = "a breaking change never goes to a previous release line"
REASON_PLAIN_FIX = "a fix that is not a security fix ships with the next release of main"
REASON_PERFORMANCE = "a performance change ships with the next release of main"
REASON_FEATURE = "a feature ships with the next release of main"
REASON_OTHER_TYPE = "only a security fix is backported to a previous release line"
REASON_MALFORMED = "the label does not name a release line"


@dataclass(frozen=True)
class Decision:
    action: str
    line: Optional[str]
    branch: Optional[str]
    reason: str


def parse_backport_label(label: str) -> Optional[str]:
    """Returns the line `X.Y` named by `backport:X.Y`, or None for any label that is not of that exact form."""
    match = LINE_PATTERN.match(label)
    if match is None:
        return None
    return match.group(1)


def _title_refusal(title: str, body: str) -> Optional[str]:
    """Returns the reason the title or body makes the change ineligible, or None when it is a security fix."""
    match = TITLE_PATTERN.match(title)
    if match is None:
        return REASON_NOT_CONVENTIONAL
    if match.group("bang") or BREAKING_FOOTER_PATTERN.search(body or ""):
        return REASON_BREAKING
    kind = match.group("type")
    if kind == "fix":
        if match.group("scope") == "security":
            return None
        return REASON_PLAIN_FIX
    if kind == "perf":
        return REASON_PERFORMANCE
    if kind == "feat":
        return REASON_FEATURE
    return REASON_OTHER_TYPE


def decide(
    title: str,
    body: str,
    labels: Sequence[str],
    base_ref: str,
    merged: bool,
    lines_with_branch: Iterable[str],
) -> List[Decision]:
    """Returns one decision per backport label of a pull request, in the order of the labels.

    A pull request that is not merged into `main` gets no decision at all. A title that is not a security fix refuses every
    backport label with the reason for the title. A label for a line without a release branch is refused by name.
    """
    if not merged or base_ref != "main":
        return []

    branches = set(lines_with_branch)
    title_refusal = _title_refusal(title, body)
    decisions: List[Decision] = []
    for label in labels:
        if not label.startswith(BACKPORT_LABEL_PREFIX):
            continue
        line = parse_backport_label(label)
        if line is None:
            decisions.append(Decision(action="refuse", line=None, branch=None, reason=REASON_MALFORMED))
            continue
        if title_refusal is not None:
            decisions.append(Decision(action="refuse", line=line, branch=None, reason=title_refusal))
            continue
        if line not in branches:
            decisions.append(Decision(action="refuse", line=line, branch=None, reason=f"no release branch exists for line {line}"))
            continue
        decisions.append(Decision(action="backport", line=line, branch=f"release/{line}.x", reason=f"security fix for line {line}"))
    return decisions


def main(argv: List[str], stdin: Optional[IO[str]] = None, stdout: Optional[IO[str]] = None) -> int:
    """Reads the pull request as JSON on stdin and prints `action<TAB>line<TAB>branch<TAB>reason` per decision.

    The object carries `title`, `body`, `labels`, `base_ref`, `merged` and `lines_with_branch`. A missing line or branch
    is printed as an empty field. Every reason is one of the fixed texts above, so no input text reaches the output.
    """
    stdin = stdin if stdin is not None else sys.stdin
    stdout = stdout if stdout is not None else sys.stdout
    event = json.load(stdin)
    decisions = decide(
        title=event.get("title") or "",
        body=event.get("body") or "",
        labels=event.get("labels") or [],
        base_ref=event.get("base_ref") or "",
        merged=bool(event.get("merged")),
        lines_with_branch=event.get("lines_with_branch") or [],
    )
    for decision in decisions:
        stdout.write("\t".join([decision.action, decision.line or "", decision.branch or "", decision.reason]) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
