"""Tests for the shape of the backend coverage job in .github/workflows/ci.yml.

The job asks select_backend_tests.py for a decision and runs either the full backend suite with the Codecov upload,
or the selected tests without a report and without an upload. The tests read the workflow fresh in every test and
check only its shape: job names, needs, the step that asks the script, and which step runs in which mode. No network,
no clock, no GitHub API. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
WORKFLOW = os.path.join(REPO_DIR, ".github", "workflows", "ci.yml")


def load_jobs():
    with open(WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)["jobs"]


def steps_of(job_name):
    return load_jobs()[job_name]["steps"]


def find_steps(job_name, predicate):
    return [step for step in steps_of(job_name) if predicate(step)]


def run_text(step):
    return step.get("run", "") or ""


def condition(step):
    return str(step.get("if", ""))


class AggregateCoverageCheckTest(unittest.TestCase):
    def test_aggregate_coverage_job_keeps_its_name_and_needs(self):
        job = load_jobs()["coverage"]

        self.assertEqual("Coverage", job["name"], "the required status check must keep the name Coverage")
        self.assertEqual(["coverage-backend", "coverage-frontend"], job["needs"])

    def test_backend_job_is_still_gated_on_coverage_changes(self):
        job = load_jobs()["coverage-backend"]

        self.assertEqual("coverage-changes", job["needs"])


class SelectionStepTest(unittest.TestCase):
    def test_backend_job_asks_the_selection_script_for_a_decision(self):
        steps = find_steps(
            "coverage-backend",
            lambda step: step.get("id") == "select" and "select_backend_tests.py" in run_text(step),
        )

        self.assertEqual(1, len(steps), "exactly one step with id 'select' must run select_backend_tests.py")


class FullRunTest(unittest.TestCase):
    def test_codecov_upload_runs_only_for_a_full_run(self):
        uploads = find_steps(
            "coverage-backend",
            lambda step: "codecov" in str(step.get("uses", "")),
        )

        self.assertEqual(1, len(uploads), "the backend job must keep exactly one Codecov upload")
        self.assertIn(
            "steps.select.outputs.mode == 'full'",
            condition(uploads[0]),
            "a partial report must never reach Codecov",
        )

    def test_full_run_keeps_the_coverage_report_task(self):
        steps = find_steps(
            "coverage-backend",
            lambda step: "steps.select.outputs.mode == 'full'" in condition(step)
            and ":reposilite-backend:testCoverage" in run_text(step),
        )

        self.assertEqual(1, len(steps), "a full run must keep ./gradlew :reposilite-backend:testCoverage")


class PartialRunTest(unittest.TestCase):
    def partial_steps(self):
        return find_steps(
            "coverage-backend",
            lambda step: "steps.select.outputs.mode == 'partial'" in condition(step),
        )

    def test_partial_run_passes_filters_to_the_test_and_integration_tasks(self):
        steps = self.partial_steps()

        self.assertEqual(1, len(steps), "exactly one step must run when the decision is partial")
        run = run_text(steps[0])
        self.assertIn(":reposilite-backend:test", run, "the test task must run")
        self.assertIn(":reposilite-backend:integration", run, "the integration task must run")
        self.assertIn("--tests", run, "the selected filters must reach the tasks as --tests options")

    def test_partial_run_produces_no_coverage_report(self):
        for step in self.partial_steps():
            with self.subTest(step=step.get("name")):
                self.assertNotIn("testCoverage", run_text(step), "a partial run must not build the coverage report")
                self.assertNotIn("jacoco", run_text(step).lower(), "a partial run must not build the JaCoCo report")


if __name__ == "__main__":
    unittest.main()
