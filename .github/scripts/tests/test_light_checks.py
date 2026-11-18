"""Tests for .github/scripts/light_checks.py and the Light checks job in ci.yml.

The script takes the results of the light jobs and the job list of the run, prints a table of each job's duration
against its budget, and exits 1 only when a light job failed or was cancelled. Skipped jobs and jobs over budget do not
fail it. Each test builds its own input in memory. No network, no clock, no files.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import importlib.util
import io
import os
import re
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
CI_WORKFLOW = os.path.join(os.path.dirname(SCRIPTS_DIR), "workflows", "ci.yml")

spec = importlib.util.spec_from_file_location("light_checks", os.path.join(SCRIPTS_DIR, "light_checks.py"))
light_checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(light_checks)


def job(name, seconds, conclusion="success"):
    """A job of the GitHub API listing, started at a fixed instant."""
    return {
        "name": name,
        "conclusion": conclusion,
        "started_at": "2026-11-14T10:00:00Z",
        "completed_at": f"2026-11-14T{10 + seconds // 3600:02d}:{seconds % 3600 // 60:02d}:{seconds % 60:02d}Z",
    }


def needs(**results):
    """The needs context of the aggregate: job id to a result."""
    return {job_id: {"result": result} for job_id, result in results.items()}


def run(needs_context, jobs):
    """Runs the script on the inputs and returns the exit code, the table and the warnings."""
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        code = light_checks.main(needs_context, jobs)
    text = out.getvalue()
    warnings = [line for line in text.splitlines() if line.startswith("::warning")]
    return code, text, warnings


class ResultTest(unittest.TestCase):

    def test_all_jobs_successful_passes(self):
        code, _, _ = run(needs(a="success", b="success"), [])
        self.assertEqual(code, 0)

    def test_skipped_job_does_not_fail_the_aggregate(self):
        code, _, _ = run(needs(a="success", b="skipped"), [])
        self.assertEqual(code, 0)

    def test_failed_job_fails_the_aggregate(self):
        code, _, _ = run(needs(a="success", b="failure"), [])
        self.assertEqual(code, 1)

    def test_cancelled_job_fails_the_aggregate(self):
        code, _, _ = run(needs(a="cancelled"), [])
        self.assertEqual(code, 1)

    def test_failure_names_the_failing_job(self):
        _, text, _ = run(needs(licenses="failure"), [])
        self.assertIn("::error::licenses ended with 'failure'", text)

    def test_unknown_result_fails_the_aggregate(self):
        code, _, _ = run(needs(a="something-new"), [])
        self.assertEqual(code, 1)


class BudgetTest(unittest.TestCase):

    def test_job_within_budget_is_listed_without_a_warning(self):
        code, text, warnings = run(needs(a="success"), [job("Licenses", 59)])
        self.assertEqual(code, 0)
        self.assertEqual(warnings, [])
        self.assertIn("| Licenses | 59 s | 120 s | ok |", text)

    def test_job_over_budget_warns_and_still_passes(self):
        code, text, warnings = run(needs(a="success"), [job("Licenses", 121)])
        self.assertEqual(code, 0)
        self.assertEqual(len(warnings), 1)
        self.assertIn("Licenses", warnings[0])
        self.assertIn("| Licenses | 121 s | 120 s | over |", text)

    def test_job_exactly_on_budget_is_not_over(self):
        _, _, warnings = run(needs(a="success"), [job("Licenses", 120)])
        self.assertEqual(warnings, [])

    def test_build_matrix_job_uses_the_build_budget(self):
        _, text, _ = run(needs(a="success"), [job("Build / Build (ubuntu-latest)", 250)])
        self.assertIn("| Build / Build (ubuntu-latest) | 250 s | 300 s | ok |", text)

    def test_docs_tests_job_has_a_two_minute_budget(self):
        _, text, _ = run(needs(a="success"), [job("Docs tests", 120)])
        self.assertIn("| Docs tests | 120 s | 120 s | ok |", text)

    def test_job_without_a_budget_is_not_listed(self):
        _, text, warnings = run(needs(a="success"), [job("Container images", 1500)])
        self.assertNotIn("Container images", text)
        self.assertEqual(warnings, [])

    def test_skipped_job_has_no_duration_row(self):
        _, text, _ = run(needs(a="success"), [job("Licenses", 0, conclusion="skipped")])
        self.assertNotIn("| Licenses |", text)

    def test_job_without_a_completion_time_is_ignored(self):
        broken = {"name": "Licenses", "conclusion": None, "started_at": "2026-11-14T10:00:00Z", "completed_at": None}
        code, text, _ = run(needs(a="success"), [broken])
        self.assertEqual(code, 0)
        self.assertNotIn("| Licenses |", text)

    def test_slowest_light_job_is_named_as_the_critical_path(self):
        _, text, _ = run(needs(a="success"), [job("Licenses", 59), job("Plugin API", 240)])
        self.assertIn("Critical path: Plugin API, 240 s", text)

    def test_empty_job_list_still_prints_a_summary(self):
        _, text, _ = run(needs(a="success"), [])
        self.assertIn("## Light checks", text)


class WorkflowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            text = handle.read()
        match = re.search(r"^  light-checks:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)", text, re.S | re.M)
        cls.block = match.group(1) if match else ""

    def test_job_exists_with_the_required_check_name(self):
        self.assertIn('name: "Light checks"', self.block)

    def test_job_runs_even_when_a_needed_job_was_skipped_or_failed(self):
        self.assertIn("if: ${{ always() }}", self.block)

    def test_job_needs_every_light_job(self):
        for job_id in (
            "build", "coverage-changes", "coverage-backend", "coverage-frontend",
            "frontend", "licenses", "vulnerabilities", "sbom", "plugin-api", "script-tests", "docs-tests", "support-lines",
        ):
            self.assertRegex(self.block, rf"needs:[^\n]*\b{re.escape(job_id)}\b", job_id)

    def test_job_does_not_need_a_heavy_job(self):
        for job_id in ("images", "chart", "ha-smoke", "dashboard-e2e"):
            self.assertNotRegex(self.block, rf"needs:[^\n]*\b{re.escape(job_id)}\b", job_id)

    def test_job_may_read_the_job_list_of_its_run_and_nothing_else(self):
        self.assertIn("actions: read", self.block)
        self.assertIn("contents: read", self.block)
        self.assertNotIn("write", self.block)

    def test_a_failing_script_fails_the_step_despite_the_pipe_to_the_summary(self):
        self.assertIn("set -o pipefail", self.block)

    def test_job_runs_the_script_with_the_needs_context(self):
        self.assertIn("light_checks.py", self.block)
        self.assertIn("toJSON(needs)", self.block)


if __name__ == "__main__":
    unittest.main()
