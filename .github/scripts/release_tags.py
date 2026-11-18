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

The standard library only. Imported by the workflows and by `tests/test_release_tags.py`. The command line runs the
same rules for the workflows: `container-tags VERSION` and `previous BRANCH`, both reading the version tags on stdin.
"""

import argparse
import re
import sys
from typing import IO, Iterable, List, Optional, Tuple

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


def previous_release(tags: Iterable[str], line: Optional[str]) -> Optional[str]:
    """Returns the tag of the previous release that an upgrade rehearsal starts from, or None when there is none.

    On a release line `X.Y` it is the highest tag of that line, so a `1.1.x` build is never rehearsed from a `2.0.x`
    image. Without a line (main) it is the highest tag across all lines.
    """
    if line is None:
        return newest_tag(tags)
    line_major, line_minor = (int(part) for part in line.split("."))
    pairs = [pair for pair in _versions(tags) if (pair[0][0], pair[0][1]) == (line_major, line_minor)]
    if not pairs:
        return None
    return max(pairs)[1]


def _read_tags(stream: IO[str]) -> List[str]:
    return [line.strip() for line in stream if line.strip()]


def main(argv: List[str], stdin: Optional[IO[str]] = None, stdout: Optional[IO[str]] = None) -> int:
    """Runs one rule for a workflow step. The version tags are read from stdin, one per line.

    `container-tags VERSION` prints the container tags of a release. `previous BRANCH` prints the previous release of
    the branch's line, or of all lines for any other branch, and exits 1 when there is none.
    """
    parser = argparse.ArgumentParser(prog="release_tags.py", description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    container = commands.add_parser("container-tags", help="print the container tags of a release")
    container.add_argument("version")
    previous = commands.add_parser("previous", help="print the previous release of a branch line")
    previous.add_argument("branch")
    args = parser.parse_args(argv)

    stdin = stdin if stdin is not None else sys.stdin
    stdout = stdout if stdout is not None else sys.stdout
    tags = _read_tags(stdin)

    if args.command == "container-tags":
        for tag in container_tags(args.version, tags):
            stdout.write(tag + "\n")
        return 0

    branch = args.branch[len("refs/heads/"):] if args.branch.startswith("refs/heads/") else args.branch
    tag = previous_release(tags, branch_line(branch))
    if tag is None:
        return 1
    stdout.write(tag + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
