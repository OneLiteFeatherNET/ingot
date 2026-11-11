"""Tests for .github/scripts/coverage_changes.sh and the coverage gate in ci.yml.

The script reads changed paths on stdin and prints one line, backend=true or backend=false. The workflow
appends that line to GITHUB_OUTPUT. Each test names one behaviour and runs the script as a subprocess. No git
repository, no network, no clock, no files.

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


def decide(paths):
    """Feeds the changed paths to the script and returns its stdout, stripped."""
    result = subprocess.run(
        ["bash", SCRIPT],
        input="".join(path + "\n" for path in paths),
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(f"script exited with {result.returncode}: {result.stderr}")
    return result.stdout.strip()


def job_block(text, job):
    """Returns the YAML text of one top-level job of the workflow, or None when it does not exist."""
    match = re.search(
        rf"^  {re.escape(job)}:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)",
        text,
        re.S | re.M,
    )
    return match.group(1) if match else None


class CoverageChangesScriptTest(unittest.TestCase):

    def test_documentation_only_change_skips_coverage(self):
        self.assertEqual(decide(["docs/guide.md"]), "backend=false")

    def test_openspec_only_change_skips_coverage(self):
        self.assertEqual(decide(["openspec/changes/x/proposal.md", "openspec/changes/x/tasks.md"]), "backend=false")

    def test_markdown_file_in_subfolder_skips_coverage(self):
        self.assertEqual(decide(["reposilite-backend/README.md"]), "backend=false")

    def test_root_markdown_file_skips_coverage(self):
        self.assertEqual(decide(["CHANGELOG.md"]), "backend=false")

    def test_mixed_docs_and_backend_source_runs_coverage(self):
        self.assertEqual(decide(["docs/guide.md", "reposilite-backend/x.kt"]), "backend=true")

    def test_frontend_change_runs_coverage(self):
        self.assertEqual(decide(["reposilite-frontend/x.vue"]), "backend=true")

    def test_workflow_change_runs_coverage(self):
        self.assertEqual(decide([".github/workflows/ci.yml"]), "backend=true")

    def test_scripts_change_runs_coverage(self):
        self.assertEqual(decide([".github/scripts/check-image-annotations.sh"]), "backend=true")

    def test_unlisted_root_file_runs_coverage(self):
        self.assertEqual(decide(["build.gradle.kts"]), "backend=true")

    def test_empty_change_list_skips_coverage(self):
        self.assertEqual(decide([]), "backend=false")


class CoverageGateInWorkflowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()

    def test_detection_job_exists_and_exposes_backend_output(self):
        block = job_block(self.workflow, "coverage-changes")
        self.assertIsNotNone(block, "ci.yml has no coverage-changes job")
        self.assertIn("name: \"Detect coverage changes\"", block)
        self.assertIn("backend: ${{ steps.filter.outputs.backend }}", block)

    def test_coverage_job_waits_for_detection_job(self):
        block = job_block(self.workflow, "coverage")
        self.assertIsNotNone(block, "ci.yml has no coverage job")
        self.assertIn("needs: coverage-changes", block)

    def test_coverage_job_runs_only_when_backend_changed(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("needs.coverage-changes.outputs.backend == 'true'", block)

    def test_coverage_job_condition_has_cancelled_guard(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("!cancelled()", block)

    def test_coverage_job_runs_when_detection_job_did_not_succeed(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("needs.coverage-changes.result != 'success'", block)

    def test_detection_job_diffs_against_the_merge_base(self):
        block = job_block(self.workflow, "coverage-changes")
        self.assertIn('git diff --name-only "$BASE...$HEAD"', block)

    def test_coverage_job_keeps_its_required_check_name(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("name: \"Coverage\"", block)


if __name__ == "__main__":
    unittest.main()
