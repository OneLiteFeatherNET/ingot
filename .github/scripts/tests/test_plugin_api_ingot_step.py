"""Tests that the job "Plugin API" of ci.yml runs the Ingot plugin API report, report-only, and summarises it.

The step reads the releases credentials from the repository secrets, so a fork without them skips the report instead of
failing it. Each test names one behaviour and reads the workflow as text. No network, no clock, no shared state.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CI_WORKFLOW = os.path.join(REPO_DIR, "workflows", "ci.yml")
JOB = "plugin-api"
BINARY_STEP = '- name: "Compare the plugin API with Reposilite 3.5.28"'
INGOT_STEP = '- name: "Compare the Ingot plugin API with the last Ingot release"'
SUMMARY_STEP = '- name: "Write the Ingot plugin API to the job summary"'
USERNAME = "ONELITEFEATHER_MAVEN_USERNAME"
PASSWORD = "ONELITEFEATHER_MAVEN_PASSWORD"
REPORT_DIR = "reposilite-backend/build/reports/plugin-api/"


def job_blocks(text):
    """Maps each job id to the text of its block."""
    blocks = {}
    matches = list(re.finditer(r"^  ([a-z][a-z-]*):\n", text, re.M))
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        blocks[match.group(1)] = text[match.start():end]
    return blocks


def step_block(job_text, name):
    """Returns the text of the step that starts with the given name line, up to the next step."""
    start = job_text.find(f"      {name}")
    if start == -1:
        return None
    end = job_text.find("\n      - name:", start + 1)
    return job_text[start:end if end != -1 else len(job_text)]


class PluginApiIngotStepTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()
        cls.job = job_blocks(cls.workflow)[JOB]

    def test_plugin_api_job_has_an_ingot_report_step(self):
        step = step_block(self.job, INGOT_STEP)
        self.assertIsNotNone(step, "the plugin-api job has no step that compares the Ingot plugin API")
        self.assertIn("run: ./gradlew --build-cache :reposilite-backend:ingotPluginApiReport", step)

    def test_ingot_report_runs_after_the_three_five_twenty_eight_report(self):
        self.assertGreater(
            self.job.index(INGOT_STEP),
            self.job.index(BINARY_STEP),
            "the Ingot report must follow the 3.5.28 report",
        )

    def test_ingot_report_step_is_report_only(self):
        step = step_block(self.job, INGOT_STEP)
        self.assertIn("continue-on-error: true", step, "a failing Ingot report must not fail the job")

    def test_ingot_report_step_reads_the_releases_credentials_from_secrets(self):
        step = step_block(self.job, INGOT_STEP)
        self.assertIn(f"{USERNAME}: ${{{{ secrets.{USERNAME} }}}}", step)
        self.assertIn(f"{PASSWORD}: ${{{{ secrets.{PASSWORD} }}}}", step)

    def test_credentials_are_read_by_the_ingot_report_step_only(self):
        step = step_block(self.job, INGOT_STEP)
        for name in (USERNAME, PASSWORD):
            self.assertEqual(
                self.workflow.count(f"secrets.{name}"),
                step.count(f"secrets.{name}"),
                f"{name} is read outside the Ingot report step",
            )

    def test_plugin_api_job_keeps_read_only_permissions(self):
        self.assertIn("permissions:\n      contents: read\n", self.job)

    def test_job_summary_has_an_ingot_plugin_api_section(self):
        step = step_block(self.job, SUMMARY_STEP)
        self.assertIsNotNone(step, "the plugin-api job writes no Ingot section to the job summary")
        self.assertIn('echo "## Ingot plugin API"', step)

    def test_job_summary_counts_the_groups_with_the_summary_script(self):
        step = step_block(self.job, SUMMARY_STEP)
        self.assertIn(f"report={REPORT_DIR}ingot-report.md", step)
        self.assertIn('python3 .github/scripts/plugin_api_summary.py "$report"', step)

    def test_job_summary_step_runs_after_the_ingot_report_step(self):
        self.assertGreater(self.job.index(SUMMARY_STEP), self.job.index(INGOT_STEP))

    def test_job_summary_step_runs_even_when_the_report_fails(self):
        step = step_block(self.job, SUMMARY_STEP)
        self.assertIn("if: always()", step)

    def test_report_artifact_uploads_both_plugin_api_reports(self):
        self.assertIn(f"path: {REPORT_DIR}\n", self.job, "the artifact must carry the Ingot reports next to the 3.5.28 report")


if __name__ == "__main__":
    unittest.main()
