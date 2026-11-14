#!/usr/bin/env python3
"""Aggregates the light jobs of ci.yml into one check and reports their duration against a budget.

The check fails when a light job failed, was cancelled or ended in a state this script does not know. A skipped job
does not fail it, because a job skipped by its `if` is how the scope step keeps a pull request off work it cannot
affect. A job over its budget only prints a warning: a slow runner must not block a merge.

The budgets are the ceilings of the light checks. The light jobs run in parallel, so the slowest one is the critical
path of the pull request, and the table names it.

Inputs, both as JSON:

- the `needs` context of the aggregate job, in the environment variable NEEDS;
- the job list of the run, as returned by `gh api repos/<repo>/actions/runs/<run>/jobs`, in the file named by the
  argument or in the environment variable JOBS_FILE. A missing or unreadable list leaves the table empty and does not
  change the result.

The script uses the standard library only and makes no network call.
"""

import json
import os
import sys
from datetime import datetime

# Job name to budget in seconds, the wall clock of the job without queue time.
BUDGETS = {
    "Detect coverage changes": 30,
    "Coverage (backend)": 300,
    "Coverage (frontend)": 270,
    "Frontend": 90,
    "Licenses": 120,
    "Vulnerabilities": 180,
    "SBOM": 120,
    "Plugin API": 300,
    "Docs tests": 120,
}
# The Build job of the shared workflow is named per runner, for example "Build / Build (ubuntu-latest)".
BUILD_PREFIX = "Build / Build"
BUILD_BUDGET = 300

PASSING = ("success", "skipped")


def budget_of(name):
    if name in BUDGETS:
        return BUDGETS[name]
    if name.startswith(BUILD_PREFIX):
        return BUILD_BUDGET
    return None


def seconds_of(entry):
    """The wall clock of a finished job, or None when the API did not report both ends."""
    started, completed = entry.get("started_at"), entry.get("completed_at")
    if not started or not completed or entry.get("conclusion") == "skipped":
        return None
    try:
        begin = datetime.fromisoformat(started.replace("Z", "+00:00"))
        end = datetime.fromisoformat(completed.replace("Z", "+00:00"))
    except ValueError:
        return None
    return int((end - begin).total_seconds())


def main(needs_context, jobs):
    """Prints the summary and returns the exit code."""
    failed = False
    for job_id, value in sorted(needs_context.items()):
        result = value.get("result") if isinstance(value, dict) else None
        if result not in PASSING:
            print(f"::error::{job_id} ended with '{result}'.")
            failed = True

    rows = []
    for entry in jobs:
        name = entry.get("name", "")
        budget = budget_of(name)
        seconds = seconds_of(entry)
        if budget is None or seconds is None:
            continue
        rows.append((name, seconds, budget))

    print("## Light checks")
    print()
    if rows:
        print("| Job | Duration | Budget | State |")
        print("| --- | --- | --- | --- |")
        for name, seconds, budget in rows:
            over = seconds > budget
            print(f"| {name} | {seconds} s | {budget} s | {'over' if over else 'ok'} |")
            if over:
                print(f"::warning::{name} took {seconds} s, over its budget of {budget} s.")
        slowest = max(rows, key=lambda row: row[1])
        print()
        print(f"Critical path: {slowest[0]}, {slowest[1]} s. The light jobs run in parallel.")
    else:
        print("No job durations were available.")
    return 1 if failed else 0


def load_jobs(path):
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError, TypeError):
        return []
    jobs = data.get("jobs") if isinstance(data, dict) else None
    return jobs if isinstance(jobs, list) else []


if __name__ == "__main__":
    try:
        needs_context = json.loads(os.environ.get("NEEDS", "{}"))
    except ValueError:
        print("::error::The needs context is not valid JSON.")
        sys.exit(1)
    jobs_file = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("JOBS_FILE", "")
    sys.exit(main(needs_context, load_jobs(jobs_file) if jobs_file else []))
