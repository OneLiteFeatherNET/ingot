#!/usr/bin/env python3
"""Decides which heavy jobs a pull request asks for, and judges the result of the heavy run.

Two subcommands, both for `.github/workflows/heavy.yml`. The script uses the standard library only and makes no
network call.

`decide` reads the event and prints `key=value` lines (appended to the file given by `--output`, else to stdout):

- `jobs`: comma separated names of the heavy jobs to run, possibly empty;
- one `<job>=true|false` line per heavy job, for the `if:` of that job;
- `requested`: `true` when at least one job runs;
- `full`: `true` when the jobs run in full (the complete integration suite) and not as a selection;
- `release`: `true` for a pull request whose head branch starts with `release-please--`;
- `ok`: `false` when the aggregate must fail whatever the jobs report;
- `reason`: one line for the job summary.

Rules, in this order:

1. An unknown event or an unusable input is a scope failure: every job runs in full, `ok` is `false` and the exit
   status is 1.
2. `workflow_dispatch` on `refs/heads/main` runs every job in full. On any other ref nothing runs and `ok` is `false`.
3. A release pull request needs `ci:heavy`. With it every job runs in full, without it nothing runs and `ok` is `false`.
4. A fork pull request does not rerun on `synchronize`, because the label was given to code the maintainer reviewed.
   When a label is present `ok` is `false` until the maintainer removes and re-adds it.
5. `ci:heavy` selects every job. An area label selects its own jobs. No label selects nothing.
6. A `merge_group` event (the merge queue) has no labels. The scope flags of ci_scope.sh for the merge group diff,
   passed with `--scope-file`, select the jobs: `image` runs the images and the chart install that deploys them,
   `chart` runs the chart, `e2e` runs the end-to-end tests. Nothing else runs there. A missing or unreadable scope runs
   all three, so a failed detection never skips one.

`verdict` reads the decision and the result of every heavy job and prints the verdict for the aggregate `Heavy tests`.
It exits 1 when the aggregate must fail.

Run from the repository root, for example:

    python3 .github/scripts/heavy_gate.py decide --event-name pull_request --action labeled --labels '["ci:e2e"]' \\
        --head-ref feature --head-repo a/b --repo a/b
"""

import argparse
import json
import sys
from typing import Dict, List, NamedTuple, Optional, Tuple

HEAVY_JOBS = ("integration", "images", "ha", "e2e", "helm", "search", "codeql")
ALL_LABEL = "ci:heavy"
AREA_LABELS = {
    "ci:integration": ("integration",),
    "ci:images": ("images", "helm"),
    "ci:ha": ("ha",),
    "ci:e2e": ("e2e",),
    "ci:search": ("search",),
    "ci:codeql": ("codeql",),
}
# The heavy jobs a merge group can start, and the scope flags of ci_scope.sh that start each of them.
MERGE_GROUP_JOBS = {
    "images": ("image",),
    "helm": ("image", "chart"),
    "e2e": ("e2e",),
}
MAIN_REF = "refs/heads/main"
RELEASE_REF_PREFIX = "release-please--"
PULL_REQUEST_ACTIONS = ("opened", "reopened", "synchronize", "labeled", "unlabeled")
GREEN_RESULTS = ("success", "skipped")


class Decision(NamedTuple):
    jobs: Tuple[str, ...]
    full: bool
    release: bool
    ok: bool
    reason: str


def decide(event_name, action, labels, head_ref, head_repo, repo, ref="", scope=None) -> Decision:
    """Returns the heavy jobs a pull request, a merge group or a dispatch asks for.

    `scope` maps the flags of ci_scope.sh to "true" or "false" and is read for a merge group only. None means the
    scope is unknown."""
    everything = Decision(HEAVY_JOBS, True, False, True, "")
    if event_name == "merge_group":
        return _merge_group(scope)
    if event_name == "workflow_dispatch":
        if ref != MAIN_REF:
            return Decision((), False, False, False, "an on-demand heavy run starts from main only")
        return everything._replace(reason="on-demand run: every heavy job in full")
    if event_name != "pull_request" or action not in PULL_REQUEST_ACTIONS:
        return everything._replace(ok=False, reason=f"unknown event {event_name!r} action {action!r}: running everything")
    if not head_repo or not repo:
        return everything._replace(ok=False, reason="repository of the pull request unknown: running everything")

    label_set = set(labels)
    release = (head_ref or "").startswith(RELEASE_REF_PREFIX)
    if release:
        if ALL_LABEL in label_set:
            return everything._replace(release=True, reason="release pull request: every heavy job in full")
        return Decision((), False, True, False, f"run the heavy tests: add {ALL_LABEL}")

    selected = _selected_jobs(label_set)
    if head_repo != repo and action == "synchronize":
        if selected:
            return Decision((), False, False, False,
                            "push to a fork pull request: remove and re-add the label to run the heavy jobs again")
        return Decision((), False, False, True, "heavy tests not requested")
    if not selected:
        return Decision((), False, False, True, "heavy tests not requested")
    return Decision(selected, False, False, True, "heavy jobs requested by label: " + ", ".join(selected))


def _merge_group(scope) -> Decision:
    if scope is None:
        jobs = tuple(job for job in HEAVY_JOBS if job in MERGE_GROUP_JOBS)
        return Decision(jobs, False, False, True, "merge group scope unknown: running " + ", ".join(jobs))
    jobs = tuple(job for job in HEAVY_JOBS
                 if any(scope.get(flag) == "true" for flag in MERGE_GROUP_JOBS.get(job, ())))
    if not jobs:
        return Decision((), False, False, True, "merge group touches no heavy scope")
    return Decision(jobs, False, False, True, "merge group scope: " + ", ".join(jobs))


def _read_scope(path):
    """The key=value lines of ci_scope.sh as a dict, or None when the file is missing, unreadable or empty."""
    if not path:
        return None
    try:
        with open(path, encoding="utf-8") as handle:
            lines = handle.read().splitlines()
    except OSError:
        return None
    scope = dict(line.split("=", 1) for line in lines if "=" in line)
    return scope or None


def _selected_jobs(label_set) -> Tuple[str, ...]:
    if ALL_LABEL in label_set:
        return HEAVY_JOBS
    wanted = set()
    for label, jobs in AREA_LABELS.items():
        if label in label_set:
            wanted.update(jobs)
    return tuple(job for job in HEAVY_JOBS if job in wanted)


def render_decision(decision: Decision) -> List[str]:
    lines = [f"jobs={','.join(decision.jobs)}"]
    lines += [f"{job}={_flag(job in decision.jobs)}" for job in HEAVY_JOBS]
    lines += [
        f"requested={_flag(bool(decision.jobs))}",
        f"full={_flag(decision.full)}",
        f"release={_flag(decision.release)}",
        f"ok={_flag(decision.ok)}",
        f"reason={decision.reason}",
    ]
    return lines


def _flag(value: bool) -> str:
    return "true" if value else "false"


def verdict(decision: Decision, gate_result: str, results: Dict[str, object]):
    """Returns (passed, message) for the aggregate from the gate result and each heavy job result.

    `results` maps a job id to its result, or to an object with a `result` key, which is the shape of the `needs`
    context. A heavy job that is absent is not wired into the workflow and is ignored."""
    if gate_result != "success":
        return False, f"the heavy gate did not succeed ({gate_result or 'missing'}): no job counts as passed"
    if not decision.ok:
        return False, decision.reason
    problems = []
    for job in HEAVY_JOBS:
        if job not in results:
            continue  # not wired into the workflow (yet): the shape test of the workflow guards the list
        value = results[job]
        result = value.get("result", "") if isinstance(value, dict) else value
        if job in decision.jobs:
            if result != "success":
                problems.append(f"{job}: {result or 'missing'}")
        elif result not in GREEN_RESULTS:
            problems.append(f"{job}: {result or 'missing'}")
    if problems:
        return False, "heavy jobs did not pass: " + ", ".join(problems)
    if not decision.jobs:
        return True, decision.reason
    return True, "heavy jobs passed: " + ", ".join(decision.jobs)


def _parse_labels(raw: Optional[str]) -> List[str]:
    value = json.loads(raw or "[]")
    if value is None:  # a merge_group event has no pull request, so its label list renders as null
        value = []
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ValueError("labels must be a JSON list of strings")
    return value


def _run_decide(args) -> int:
    failed = False
    try:
        decision = decide(args.event_name, args.action, _parse_labels(args.labels),
                          args.head_ref, args.head_repo, args.repo, args.ref, _read_scope(args.scope_file))
    except ValueError as error:
        failed = True
        decision = Decision(HEAVY_JOBS, True, False, False, f"scope step failed ({error}): running everything")
    lines = render_decision(decision)
    if args.output:
        with open(args.output, "a", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
    else:
        print("\n".join(lines))
    return 1 if (failed or not decision.ok and decision.full) else 0


def _run_verdict(args) -> int:
    try:
        results = json.loads(args.results)
        if not isinstance(results, dict):
            raise ValueError("results must be a JSON object")
    except ValueError as error:
        print(f"unusable results ({error})")
        return 1
    decision = Decision(tuple(job for job in args.jobs.split(",") if job), args.full == "true",
                        args.release == "true", args.ok == "true", args.reason)
    passed, message = verdict(decision, args.gate_result, results)
    print(message)
    return 0 if passed else 1


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    decide_parser = sub.add_parser("decide")
    decide_parser.add_argument("--event-name", default="")
    decide_parser.add_argument("--action", default="")
    decide_parser.add_argument("--labels", default="[]", help="JSON list of label names")
    decide_parser.add_argument("--head-ref", default="")
    decide_parser.add_argument("--head-repo", default="")
    decide_parser.add_argument("--repo", default="")
    decide_parser.add_argument("--ref", default="", help="git ref the workflow runs on, for workflow_dispatch")
    decide_parser.add_argument("--scope-file", default="", help="output of ci_scope.sh, for a merge group")
    decide_parser.add_argument("--output", default="", help="file to append the key=value lines to")
    verdict_parser = sub.add_parser("verdict")
    verdict_parser.add_argument("--gate-result", default="")
    verdict_parser.add_argument("--results", default="{}", help="JSON object: job id to result, or the needs context")
    verdict_parser.add_argument("--jobs", default="")
    verdict_parser.add_argument("--full", default="false")
    verdict_parser.add_argument("--release", default="false")
    verdict_parser.add_argument("--ok", default="false")
    verdict_parser.add_argument("--reason", default="")
    args = parser.parse_args(argv)
    return _run_decide(args) if args.command == "decide" else _run_verdict(args)


if __name__ == "__main__":
    sys.exit(main())
