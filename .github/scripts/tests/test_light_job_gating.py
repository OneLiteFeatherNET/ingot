"""Tests that the light jobs of ci.yml skip on the scope flags of Detect coverage changes.

A job skipped by its `if` reports success to a required check, so every gate has to open when the scope step did
not succeed: a failed detection must never skip a job. Each test names one behaviour and reads ci.yml as text.
No network, no clock.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

CI_WORKFLOW = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "workflows", "ci.yml"
)

# The job id, the check name it reports and the scope output that decides whether it runs.
GATED = {
    "licenses": ("Licenses", "unit"),
    "vulnerabilities": ("Vulnerabilities", "unit"),
    "sbom": ("SBOM", "unit"),
    "plugin-api": ("Plugin API", "unit"),
    "frontend": ("Frontend", "frontend"),
}
SCOPE = "needs.coverage-changes"


def job_block(text, job):
    match = re.search(rf"^  {re.escape(job)}:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)", text, re.S | re.M)
    return match.group(1) if match else None


def condition(block):
    match = re.search(r"^    if: (.*)$", block, re.M)
    return match.group(1) if match else ""


class LightJobGatingTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()

    def test_every_gated_job_keeps_its_check_name(self):
        for job, (name, _) in GATED.items():
            block = job_block(self.workflow, job)
            self.assertIsNotNone(block, f"ci.yml has no {job} job")
            self.assertIn(f'name: "{name}"', block, job)

    def test_every_gated_job_waits_for_the_scope_step(self):
        for job in GATED:
            self.assertIn("needs: coverage-changes", job_block(self.workflow, job), job)

    def test_every_gated_job_runs_when_the_scope_step_did_not_succeed(self):
        for job in GATED:
            self.assertIn(f"{SCOPE}.result != 'success'", condition(job_block(self.workflow, job)), job)

    def test_every_gated_job_is_not_started_by_a_cancelled_run(self):
        for job in GATED:
            self.assertIn("!cancelled()", condition(job_block(self.workflow, job)), job)

    def test_every_gated_job_still_stays_off_a_push_to_main(self):
        for job in GATED:
            self.assertIn("github.event_name != 'push'", condition(job_block(self.workflow, job)), job)

    def test_every_gated_job_reads_its_scope_flag(self):
        for job, (_, flag) in GATED.items():
            self.assertIn(f"{SCOPE}.outputs.{flag} == 'true'", condition(job_block(self.workflow, job)), job)

    def test_frontend_lint_also_runs_when_the_scope_could_not_place_a_path(self):
        # The frontend output stays false for a workflow edit, so the image and chart flags, which only a path
        # nothing knows turns on together, keep an edit of this job from skipping it.
        text = condition(job_block(self.workflow, "frontend"))
        self.assertIn(f"{SCOPE}.outputs.image == 'true'", text)
        self.assertIn(f"{SCOPE}.outputs.chart == 'true'", text)

    def test_script_tests_job_runs_the_workflow_script_tests(self):
        block = job_block(self.workflow, "script-tests")
        self.assertIsNotNone(block, "ci.yml has no script-tests job")
        self.assertIn('name: "Workflow script tests"', block)
        self.assertIn("python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests", block)
        self.assertIn("pyyaml", block)
        self.assertIn("jsonschema", block)

    def test_script_tests_job_runs_when_the_pull_request_touches_github_files(self):
        # The scope step turns image and chart on together only for a path it cannot place, and a workflow or
        # script edit is one of those.
        block = job_block(self.workflow, "script-tests")
        text = condition(block)
        self.assertIn("needs: coverage-changes", block)
        self.assertIn(f"{SCOPE}.result != 'success'", text)
        self.assertIn(f"{SCOPE}.outputs.image == 'true' && {SCOPE}.outputs.chart == 'true'", text)
        self.assertIn("github.event_name != 'push'", text)

    def test_jobs_that_gate_nothing_stay_free_of_the_scope_step(self):
        # Container images, the Helm chart and the HA smoke test are heavy jobs and are not touched here.
        for job in ("images", "chart", "ha-smoke", "dashboard-e2e"):
            block = job_block(self.workflow, job)
            self.assertIsNotNone(block, job)
            self.assertNotIn("needs: coverage-changes", block, job)


if __name__ == "__main__":
    unittest.main()
