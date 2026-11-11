#!/usr/bin/env python3
"""Checks the Developer Certificate of Origin sign-off of the commits in a pull request.

A commit passes when one of these holds:

- it carries a `Signed-off-by` line whose address equals the author address of the commit (case-insensitive);
- it carries a `(cherry picked from commit <sha>)` trailer and <sha> is an ancestor of the upstream branch, which the
  fork syncs from with `git cherry-pick -x`.

A commit with a cherry-pick trailer that cannot be verified is `unverified`. Merge commits are not checked, and a pull
request opened by a GitHub App account is accepted as a whole by the caller with `--bot`.

The script uses the standard library only and makes no network call. The upstream history must already be present as a
local ref, which the workflow fetches before it runs this script.

`--mode report` (also used when the mode is empty) prints a warning per failing commit and exits 0. `--mode enforce`
prints an error per failing commit and exits 1. Any other mode exits 2.

Run from the repository root, for example:

    python3 .github/scripts/check_dco.py --base <sha> --head <sha> --upstream-ref refs/dco/upstream-main --mode report
"""

import argparse
import os
import re
import subprocess
import sys
from typing import NamedTuple

MODES = ("report", "enforce")
DEFAULT_UPSTREAM_REF = "refs/dco/upstream-main"

SIGNED_OFF_BY = re.compile(r"^Signed-off-by:[ \t]*.*?<([^<>\s]+)>[ \t]*$", re.MULTILINE | re.IGNORECASE)
CHERRY_PICKED_FROM = re.compile(r"^\(cherry picked from commit ([0-9a-fA-F]{7,40})\)[ \t]*$", re.MULTILINE)


class Verdict(NamedTuple):
    sha: str
    status: str  # "pass", "fail" or "unverified"
    reason: str


def git(repo, *args):
    result = subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True, check=True)
    return result.stdout


def git_succeeds(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True).returncode == 0


def parse_mode(value):
    """Returns the mode for a DCO_MODE value. An empty value means report."""
    if value == "":
        return "report"
    if value in MODES:
        return value
    raise ValueError(f"DCO_MODE must be unset, 'report' or 'enforce', not {value!r}")


def signed_off_addresses(message):
    return {address.lower() for address in SIGNED_OFF_BY.findall(message)}


def cherry_picked_shas(message):
    return CHERRY_PICKED_FROM.findall(message)


def list_commits(repo, base, head):
    """Non-merge commits of base..head, oldest first."""
    output = git(repo, "rev-list", "--no-merges", "--reverse", f"{base}..{head}")
    return [line for line in output.splitlines() if line]


def upstream_available(repo, upstream_ref):
    return git_succeeds(repo, "rev-parse", "--verify", "--quiet", f"{upstream_ref}^{{commit}}")


def is_upstream_ancestor(repo, sha, upstream_ref):
    # A sha that is not in the local history makes git fail, which counts as not an ancestor.
    return git_succeeds(repo, "merge-base", "--is-ancestor", sha, upstream_ref)


def evaluate_commit(repo, sha, upstream_ref, upstream_ok):
    message = git(repo, "log", "-1", "--format=%B", sha)
    author = git(repo, "log", "-1", "--format=%ae", sha).strip().lower()

    if author in signed_off_addresses(message):
        return Verdict(sha, "pass", "signed off with the author address")

    cherry_picks = cherry_picked_shas(message)
    if cherry_picks:
        if not upstream_ok:
            return Verdict(sha, "unverified", f"cherry-pick trailer, but upstream {upstream_ref} is not available")
        if any(is_upstream_ancestor(repo, picked, upstream_ref) for picked in cherry_picks):
            return Verdict(sha, "pass", "cherry-pick of a commit in upstream main")
        return Verdict(sha, "unverified", f"cherry-pick trailer names a commit that is not in upstream {upstream_ref}")

    return Verdict(sha, "fail", "no Signed-off-by line matches the author address")


def evaluate_range(repo, base, head, upstream_ref, is_bot=False):
    shas = list_commits(repo, base, head)
    if is_bot:
        return [Verdict(sha, "pass", "pull request opened by a bot account") for sha in shas]
    upstream_ok = upstream_available(repo, upstream_ref)
    return [evaluate_commit(repo, sha, upstream_ref, upstream_ok) for sha in shas]


def write_summary(verdicts, mode):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    lines = [f"### DCO sign-off (mode: {mode})", "", "| Commit | Result | Reason |", "| --- | --- | --- |"]
    lines += [f"| `{verdict.sha[:12]}` | {verdict.status} | {verdict.reason} |" for verdict in verdicts]
    with open(path, "a", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Check the DCO sign-off of pull request commits.")
    parser.add_argument("--repo", default=".", help="path of the git repository")
    parser.add_argument("--base", required=True, help="sha of the base of the pull request")
    parser.add_argument("--head", required=True, help="sha of the head of the pull request")
    parser.add_argument("--upstream-ref", default=DEFAULT_UPSTREAM_REF, help="local ref of upstream main")
    parser.add_argument("--mode", default="", help="report, enforce, or empty for report")
    parser.add_argument("--bot", action="store_true", help="the pull request was opened by a GitHub App account")
    args = parser.parse_args(argv)

    try:
        mode = parse_mode(args.mode)
    except ValueError as error:
        print(f"::error title=DCO::{error}")
        return 2

    verdicts = evaluate_range(args.repo, args.base, args.head, args.upstream_ref, is_bot=args.bot)
    failing = [verdict for verdict in verdicts if verdict.status != "pass"]
    level = "error" if mode == "enforce" else "warning"
    for verdict in failing:
        print(f"::{level} title=DCO::{verdict.sha}: {verdict.reason}")
    write_summary(verdicts, mode)
    print(f"Checked {len(verdicts)} commit(s), {len(failing)} without a valid sign-off or upstream trailer.")

    return 1 if mode == "enforce" and failing else 0


if __name__ == "__main__":
    sys.exit(main())
