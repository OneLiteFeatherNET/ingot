"""Tests for .github/scripts/coverage_changes.sh and the coverage jobs in ci.yml.

The script reads changed paths on stdin and prints three lines: backend=, frontend= and frontend_only=, each
true or false. The workflow appends those lines to GITHUB_OUTPUT. Each test names one behaviour and runs the
script as a subprocess. No git repository, no network, no clock, no files.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import subprocess
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT = os.path.join(SCRIPTS_DIR, "coverage_changes.sh")
CI_WORKFLOW = os.path.join(os.path.dirname(SCRIPTS_DIR), "workflows", "ci.yml")

NONE = {"backend": "false", "frontend": "false", "frontend_only": "false"}


def decide(paths):
    """Feeds the changed paths to the script and returns its three outputs as a dict."""
    result = subprocess.run(
        ["bash", SCRIPT],
        input="".join(path + "\n" for path in paths),
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(f"script exited with {result.returncode}: {result.stderr}")
    outputs = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition("=")
        outputs[key] = value
    return outputs


def job_block(text, job):
    """Returns the YAML text of one top-level job of the workflow, or None when it does not exist."""
    match = re.search(
        rf"^  {re.escape(job)}:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)",
        text,
        re.S | re.M,
    )
    return match.group(1) if match else None


def step_block(block, name):
    """Returns the YAML text of one step of a job block, identified by its name, or None."""
    match = re.search(
        rf"^      - name: \"{re.escape(name)}\"\n(.*?)(?=^      - name:|\Z)",
        block,
        re.S | re.M,
    )
    return match.group(1) if match else None


class CoverageChangesScriptTest(unittest.TestCase):

    def test_documentation_only_change_skips_every_coverage_job(self):
        self.assertEqual(decide(["docs/guide.md"]), NONE)

    def test_openspec_only_change_skips_every_coverage_job(self):
        self.assertEqual(decide(["openspec/changes/x/proposal.md", "openspec/changes/x/tasks.md"]), NONE)

    def test_markdown_file_in_subfolder_skips_every_coverage_job(self):
        self.assertEqual(decide(["reposilite-backend/README.md"]), NONE)

    def test_root_markdown_file_skips_every_coverage_job(self):
        self.assertEqual(decide(["CHANGELOG.md"]), NONE)

    def test_markdown_file_in_frontend_is_documentation(self):
        self.assertEqual(decide(["reposilite-frontend/README.md"]), NONE)

    def test_empty_change_list_skips_every_coverage_job(self):
        self.assertEqual(decide([]), NONE)

    def test_mixed_docs_and_backend_source_runs_only_the_backend_job(self):
        self.assertEqual(
            decide(["docs/guide.md", "reposilite-backend/x.kt"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_frontend_change_runs_the_backend_dashboard_subset_and_the_frontend_job(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue"]),
            {"backend": "true", "frontend": "true", "frontend_only": "true"},
        )

    def test_frontend_lockfile_is_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/package-lock.json"]),
            {"backend": "true", "frontend": "true", "frontend_only": "true"},
        )

    def test_frontend_and_backend_change_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "reposilite-backend/x.kt"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_frontend_and_docs_change_is_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "docs/guide.md"]),
            {"backend": "true", "frontend": "true", "frontend_only": "true"},
        )

    def test_frontend_and_workflow_change_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", ".github/workflows/ci.yml"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_frontend_and_unlisted_root_file_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "build.gradle.kts"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_workflow_change_runs_the_backend_job_only(self):
        self.assertEqual(
            decide([".github/workflows/ci.yml"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_scripts_change_runs_the_backend_job_only(self):
        self.assertEqual(
            decide([".github/scripts/check-image-annotations.sh"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_unlisted_root_file_runs_the_backend_job_only(self):
        self.assertEqual(
            decide(["build.gradle.kts"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_backend_source_runs_the_backend_job_only(self):
        self.assertEqual(
            decide(["reposilite-backend/src/main/kotlin/X.kt"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_directory_with_frontend_prefix_but_other_name_is_not_frontend(self):
        self.assertEqual(
            decide(["reposilite-frontend-tools/x.js"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_quoted_path_from_git_is_treated_as_backend(self):
        # git quotes non-ASCII names, so the frontend prefix does not match and the full run is kept.
        self.assertEqual(
            decide(['"reposilite-frontend/\\303\\244.vue"']),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )


class CoverageJobsInWorkflowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()

    def test_detection_job_exposes_all_three_outputs(self):
        block = job_block(self.workflow, "coverage-changes")
        self.assertIsNotNone(block, "ci.yml has no coverage-changes job")
        self.assertIn("name: \"Detect coverage changes\"", block)
        self.assertIn("backend: ${{ steps.filter.outputs.backend }}", block)
        self.assertIn("frontend: ${{ steps.filter.outputs.frontend }}", block)
        self.assertIn("frontend_only: ${{ steps.filter.outputs.frontend_only }}", block)

    def test_detection_job_diffs_against_the_merge_base(self):
        block = job_block(self.workflow, "coverage-changes")
        self.assertIn('git diff --name-only "$BASE...$HEAD"', block)

    def test_frontend_and_backend_jobs_run_in_full_when_detection_did_not_succeed(self):
        # A failed detection must never skip a job: each job's condition tests the detection result first.
        for job in ("coverage-backend", "coverage-frontend"):
            block = job_block(self.workflow, job)
            self.assertIn("needs.coverage-changes.result != 'success'", block, job)

    def test_backend_job_is_named_and_gated_on_the_backend_output(self):
        block = job_block(self.workflow, "coverage-backend")
        self.assertIsNotNone(block, "ci.yml has no coverage-backend job")
        self.assertIn("name: \"Coverage (backend)\"", block)
        self.assertIn("needs: coverage-changes", block)
        self.assertIn("needs.coverage-changes.outputs.backend == 'true'", block)
        self.assertIn("!cancelled()", block)

    def test_frontend_job_is_named_and_gated_on_the_frontend_output(self):
        block = job_block(self.workflow, "coverage-frontend")
        self.assertIsNotNone(block, "ci.yml has no coverage-frontend job")
        self.assertIn("name: \"Coverage (frontend)\"", block)
        self.assertIn("needs: coverage-changes", block)
        self.assertIn("needs.coverage-changes.outputs.frontend == 'true'", block)
        self.assertIn("!cancelled()", block)

    def test_frontend_job_runs_the_suite_once_with_coverage(self):
        block = job_block(self.workflow, "coverage-frontend")
        self.assertIn("npm run test:coverage", block)

    def test_frontend_job_uploads_its_coverage_report_as_an_artifact(self):
        block = job_block(self.workflow, "coverage-frontend")
        self.assertIn("actions/upload-artifact@", block)
        self.assertIn("path: reposilite-frontend/coverage", block)

    def test_frontend_lint_job_does_not_run_the_suite_again(self):
        # The vitest suite runs only in Coverage (frontend). Running it in both jobs would double it.
        block = job_block(self.workflow, "frontend")
        self.assertIsNotNone(block, "ci.yml has no frontend job")
        self.assertNotIn("npm test", block)
        self.assertIn("npm run eslint", block)

    def test_backend_job_runs_the_full_suite_unless_only_the_frontend_changed(self):
        block = job_block(self.workflow, "coverage-backend")
        step = step_block(block, "Run tests with coverage")
        self.assertIsNotNone(step, "backend job has no full coverage step")
        self.assertIn("./gradlew :reposilite-backend:testCoverage", step)
        self.assertIn("needs.coverage-changes.outputs.frontend_only != 'true'", step)

    def test_backend_job_runs_only_the_dashboard_tests_on_frontend_only_changes(self):
        block = job_block(self.workflow, "coverage-backend")
        step = step_block(block, "Run the dashboard tests of the backend")
        self.assertIsNotNone(step, "backend job has no dashboard-only step")
        self.assertIn("needs.coverage-changes.outputs.frontend_only == 'true'", step)
        self.assertIn(":reposilite-backend:test --tests 'com.reposilite.frontend.*'", step)
        self.assertIn(":reposilite-backend:integration --tests 'com.reposilite.frontend.*'", step)
        self.assertIn("--tests 'com.reposilite.web.DashboardRoutesIntegrationTest*'", step)
        self.assertIn("--tests 'com.reposilite.web.SecurityHeadersIntegrationTest*'", step)
        self.assertNotIn("testCoverage", step)

    def test_backend_job_skips_codecov_upload_on_frontend_only_changes(self):
        block = job_block(self.workflow, "coverage-backend")
        step = step_block(block, "Upload coverage to Codecov")
        self.assertIsNotNone(step, "backend job has no Codecov step")
        self.assertIn("needs.coverage-changes.outputs.frontend_only != 'true'", step)

    def test_coverage_aggregate_is_the_required_check_name(self):
        block = job_block(self.workflow, "coverage")
        self.assertIsNotNone(block, "ci.yml has no coverage aggregate job")
        self.assertIn("name: \"Coverage\"", block)

    def test_coverage_aggregate_runs_even_when_a_needed_job_was_skipped(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("if: ${{ always() }}", block)

    def test_coverage_aggregate_needs_both_coverage_jobs(self):
        block = job_block(self.workflow, "coverage")
        self.assertRegex(block, r"needs:\s*\[\s*coverage-backend\s*,\s*coverage-frontend\s*\]")

    def test_coverage_aggregate_fails_on_failed_or_cancelled_needed_jobs(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("needs.coverage-backend.result", block)
        self.assertIn("needs.coverage-frontend.result", block)
        self.assertIn("success|skipped)", block)


if __name__ == "__main__":
    unittest.main()
