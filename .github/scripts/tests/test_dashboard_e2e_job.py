"""Tests for the `dashboard-e2e` job of .github/workflows/ci.yml.

The job runs the browser suite of the dashboard. These tests read the workflow file as text and check its shape: the
name the branch protection rule refers to, the time limit, the pinned actions, the failure-only artifact and the order
of the build and the suite. No network, no clock, no YAML library: the file is read from the repository and nothing
is written. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(TESTS_DIR)))
WORKFLOW = os.path.join(REPOSITORY_ROOT, ".github", "workflows", "ci.yml")

JOB_ID = "dashboard-e2e"
JOB_NAME = "Dashboard end-to-end"
FULL_SHA = re.compile(r"^[0-9a-f]{40}$")


def read_lines(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read().split("\n")


def job_lines(lines, job_id):
    """Returns the lines of one top-level job under `jobs:`, or an empty list when the job is missing.

    A job ends at the next line that is indented by at most two spaces and is not a comment: the next job key or the
    end of the jobs section.
    """
    start = None
    for index, line in enumerate(lines):
        if line == f"  {job_id}:":
            start = index
            break
    if start is None:
        return []
    block = [lines[start]]
    for line in lines[start + 1:]:
        if line.strip() and not line.startswith("   ") and not line.startswith("  #"):
            break
        block.append(line)
    return block


def steps_of(block):
    """Splits the lines of a job into its steps, each starting at a `- name:` line of the steps list."""
    steps = []
    for line in block:
        if line.startswith("      - name:"):
            steps.append([line])
        elif steps:
            steps[-1].append(line)
    return steps


def value_of(step, key):
    """Returns the value of a key of a step, or of its `with:` inputs, or None."""
    pattern = re.compile(rf"^\s+{re.escape(key)}:\s*(.*?)\s*$")
    for line in step:
        match = pattern.match(line)
        if match:
            return match.group(1).strip('"')
    return None


def job_value(block, key):
    """Returns the value of a key set at the level of the job's own keys, or None."""
    pattern = re.compile(rf"^    {re.escape(key)}:\s*(.*?)\s*$")
    for line in block:
        match = pattern.match(line)
        if match:
            return match.group(1).strip('"')
    return None


class DashboardE2eJobTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.lines = read_lines(WORKFLOW)
        cls.block = job_lines(cls.lines, JOB_ID)
        cls.steps = steps_of(cls.block)

    def test_job_with_the_dashboard_id_exists(self):
        self.assertTrue(self.block, f"the workflow has no job with the id {JOB_ID!r}")

    def test_job_is_named_dashboard_end_to_end(self):
        self.assertEqual(job_value(self.block, "name"), JOB_NAME,
                         "the name is the check name that branch protection shows")

    def test_job_has_no_needs(self):
        self.assertIsNone(job_value(self.block, "needs"),
                          "the job runs in parallel with the others and does not wait for them")

    def test_job_times_out_after_fifteen_minutes(self):
        self.assertEqual(job_value(self.block, "timeout-minutes"), "15",
                         "a hung server must fail the job at the limit")

    def test_job_has_read_only_permissions(self):
        permissions = [line for line in self.block if line.startswith("    permissions:")]
        self.assertEqual(len(permissions), 1, "the job declares its permissions")
        self.assertIn("contents: read", "\n".join(self.block),
                      "the job needs no write permission")

    def test_job_builds_the_shaded_server_jar(self):
        self.assertTrue(
            any("./gradlew :reposilite-backend:shadowJar" in "\n".join(step) for step in self.steps),
            "the suite starts the shaded jar, so the job has to build it",
        )

    def test_job_runs_the_suite_on_java_17(self):
        java = [step for step in self.steps if "actions/setup-java@" in "\n".join(step)]
        self.assertEqual(len(java), 1, "the job sets up Java once")
        self.assertEqual(value_of(java[0], "java-version"), "17",
                         "the jar is built with the JDK the project declares")

    def test_every_action_is_pinned_to_a_full_commit_sha(self):
        unpinned = []
        for line in self.block:
            match = re.search(r"uses:\s*(\S+)\s*(#.*)?$", line)
            if not match:
                continue
            reference = match.group(1)
            sha = reference.rsplit("@", 1)[-1]
            if not FULL_SHA.match(sha) or not match.group(2):
                unpinned.append(line.strip())
        self.assertEqual(unpinned, [], "every uses: line needs a 40-character SHA and a version comment")

    def test_job_has_exactly_one_upload_step(self):
        uploads = [step for step in self.steps if "actions/upload-artifact@" in "\n".join(step)]
        self.assertEqual(len(uploads), 1, "only the Playwright report is uploaded")

    def test_upload_step_runs_only_on_failure(self):
        uploads = [step for step in self.steps if "actions/upload-artifact@" in "\n".join(step)]
        self.assertEqual(value_of(uploads[0], "if"), "failure()",
                         "a green run must upload no artifact")

    def test_job_runs_the_e2e_script_in_the_frontend_with_two_workers(self):
        suite = [
            step for step in self.steps
            if value_of(step, "run") == "npm run e2e -- --workers=2"
            and value_of(step, "working-directory") == "reposilite-frontend"
        ]
        self.assertEqual(len(suite), 1,
                         "one step runs `npm run e2e -- --workers=2` in reposilite-frontend")

    def test_suite_runs_after_the_jar_is_built(self):
        names = ["./gradlew :reposilite-backend:shadowJar", "npm run e2e"]
        positions = {}
        for name in names:
            for index, step in enumerate(self.steps):
                if any(name in line for line in step):
                    positions[name] = index
                    break
        self.assertLess(positions[names[0]], positions[names[1]],
                        "the suite starts the jar, so the jar must exist before the suite runs")


if __name__ == "__main__":
    unittest.main()
