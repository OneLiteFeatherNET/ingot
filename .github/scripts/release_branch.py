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
"""

from dataclasses import dataclass
from typing import Mapping, Optional, Tuple

from release_tags import VERSION_PATTERN, parse_version


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
