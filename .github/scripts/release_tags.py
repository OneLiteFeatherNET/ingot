#!/usr/bin/env python3
"""Pure rules for release tags across release lines.

A release line is `X.Y`, and a release branch `release/X.Y.x` carries the patch releases of that line. The functions here
decide which container tags a release moves, which tag is the newest across all lines and whether a version may be
released from a branch. They take the existing version tags as a list, so they need no network, no clock and no file.

Container tag rules (see openspec/changes/add-release-branches-and-backports/design.md, decision 5):

- `X.Y.Z` is always set.
- `X.Y` is always set, because the line is `X.Y`.
- `X` is set only when the release is the highest version with major `X`.
- `latest` is set only when the release is the highest version across all lines.

The standard library only. Imported by the workflows and by `tests/test_release_tags.py`.
"""

import re
from typing import Iterable, List, Optional, Tuple

VERSION_PATTERN = re.compile(r"^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
BRANCH_PATTERN = re.compile(r"^release/(0|[1-9]\d*)\.(0|[1-9]\d*)\.x$")

Version = Tuple[int, int, int]


class VersionOutsideLine(ValueError):
    """The version does not belong to the release line of the branch it is released from."""


def parse_version(text: str) -> Version:
    """Parses `X.Y.Z` or `vX.Y.Z` into a tuple of integers. Raises ValueError for anything else."""
    match = VERSION_PATTERN.match(text)
    if match is None:
        raise ValueError(f"not a release version: {text!r}")
    return int(match.group(1)), int(match.group(2)), int(match.group(3))


def _versions(tags: Iterable[str]) -> List[Tuple[Version, str]]:
    """Pairs every tag that is a release version with its parsed version. Other tags are ignored."""
    pairs = []
    for tag in tags:
        if VERSION_PATTERN.match(tag):
            pairs.append((parse_version(tag), tag))
    return pairs


def newest_tag(tags: Iterable[str]) -> Optional[str]:
    """Returns the tag of the highest release version across all lines, or None when there is none."""
    pairs = _versions(tags)
    if not pairs:
        return None
    return max(pairs)[1]


def highest_with_major(tags: Iterable[str], major: int) -> Optional[str]:
    """Returns the tag of the highest release version with the given major, or None when there is none."""
    pairs = [pair for pair in _versions(tags) if pair[0][0] == major]
    if not pairs:
        return None
    return max(pairs)[1]


def branch_line(branch: str) -> Optional[str]:
    """Returns the line `X.Y` of a release branch `release/X.Y.x`, or None for any other branch name."""
    match = BRANCH_PATTERN.match(branch)
    if match is None:
        return None
    return f"{match.group(1)}.{match.group(2)}"


def require_version_on_line(version: str, line: str) -> None:
    """Raises VersionOutsideLine unless `version` has the major and minor of `line`.

    The comparison uses the parsed numbers, so 1.10.0 is not on the line 1.1.
    """
    major, minor, _ = parse_version(version)
    line_major, line_minor = (int(part) for part in line.split("."))
    if (major, minor) != (line_major, line_minor):
        raise VersionOutsideLine(f"version {version} is not on the line {line}")


def container_tags(version: str, tags: Iterable[str]) -> List[str]:
    """Returns the container tags a release of `version` moves, most specific first.

    `tags` are the existing version tags. The released version counts even when its own tag is not in the list.
    """
    released = parse_version(version)
    known = [parsed for parsed, _ in _versions(list(tags) + [version])]
    major, minor, patch = released

    result = [f"{major}.{minor}.{patch}", f"{major}.{minor}"]
    if not any(parsed[0] == major and parsed > released for parsed in known):
        result.append(f"{major}")
    if not any(parsed > released for parsed in known):
        result.append("latest")
    return result
