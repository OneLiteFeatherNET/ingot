"""Tests for the `plugin-api` job of .github/workflows/ci.yml.

The job compares the plugin API with the last release and, since the slim plugin API artifact, also builds that jar,
runs its closure check and runs the consumer fixture against it. These tests read the workflow file as text and check
that the steps exist, that the closure check and the consumer test are blocking, and that they run before the job
writes its summary. No network, no clock, no YAML library: the file is only read.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(TESTS_DIR)))
WORKFLOW = os.path.join(REPOSITORY_ROOT, ".github", "workflows", "ci.yml")

JOB_ID = "plugin-api"
CLOSURE_COMMAND = "./gradlew --build-cache :reposilite-backend:pluginApiClosure"
CONSUMER_COMMAND = "./gradlew --build-cache :reposilite-test:plugin-api-consumer:test"
SUMMARY_MARKER = "GITHUB_STEP_SUMMARY"


def read_lines(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read().split("\n")


def job_lines(lines, job_id):
    """Returns the lines of one top-level job under `jobs:`, or an empty list when the job is missing."""
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


def step_running(steps, command):
    """Returns the index of the step whose run block contains the command, or None."""
    for index, step in enumerate(steps):
        if any(command in line for line in step):
            return index
    return None


class PluginApiJobTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.steps = steps_of(job_lines(read_lines(WORKFLOW), JOB_ID))

    def test_job_runs_the_closure_check_of_the_slim_jar(self):
        self.assertIsNotNone(
            step_running(self.steps, CLOSURE_COMMAND),
            f"the plugin-api job must run `{CLOSURE_COMMAND}` on every pull request",
        )

    def test_job_runs_the_consumer_test_against_the_slim_jar(self):
        self.assertIsNotNone(
            step_running(self.steps, CONSUMER_COMMAND),
            f"the plugin-api job must run `{CONSUMER_COMMAND}` on every pull request",
        )

    def test_closure_check_step_is_blocking(self):
        index = step_running(self.steps, CLOSURE_COMMAND)
        self.assertIsNotNone(index, "the closure check step is missing")
        self.assertFalse(
            any("continue-on-error" in line for line in self.steps[index]),
            "the closure check must fail the job, so it must not set continue-on-error",
        )

    def test_consumer_test_step_is_blocking(self):
        index = step_running(self.steps, CONSUMER_COMMAND)
        self.assertIsNotNone(index, "the consumer test step is missing")
        self.assertFalse(
            any("continue-on-error" in line for line in self.steps[index]),
            "the consumer test must fail the job, so it must not set continue-on-error",
        )

    def test_slim_checks_run_before_the_summary_is_written(self):
        summary = next(
            (i for i, step in enumerate(self.steps) if any(SUMMARY_MARKER in line for line in step)),
            None,
        )
        self.assertIsNotNone(summary, "the plugin-api job must still write its job summary")
        for command in (CLOSURE_COMMAND, CONSUMER_COMMAND):
            index = step_running(self.steps, command)
            self.assertIsNotNone(index, f"`{command}` is missing")
            self.assertLess(index, summary, f"`{command}` must run before the job summary step")


if __name__ == "__main__":
    unittest.main()
