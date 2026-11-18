"""Tests that ci.yml lints the Helm chart on every pull request that changes charts/, before any label is needed.

The chart job of heavy.yml stays label-gated and still installs the chart on a kind cluster. The pre-cluster part of
that job (the lint, the alert rule check, the unit tests and the drift check) moves into ci.yml behind a path check.
Each test names one behaviour and reads the workflow as data. No network, no clock.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CI_WORKFLOW = os.path.join(REPO_DIR, "workflows", "ci.yml")
HEAVY_WORKFLOW = os.path.join(REPO_DIR, "workflows", "heavy.yml")

CHANGES = "chart-changes"
LINT = "chart-lint"


def load(path):
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def run_text(job):
    return "\n".join(step.get("run", "") for step in job["steps"])


class ChartChangesDetectionTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.jobs = load(CI_WORKFLOW)["jobs"]

    def test_detection_job_exists(self):
        self.assertIn(CHANGES, self.jobs)

    def test_detection_runs_only_on_pull_requests(self):
        self.assertIn("github.event_name == 'pull_request'", self.jobs[CHANGES]["if"])

    def test_detection_matches_the_charts_directory_only(self):
        self.assertIn("grep -q '^charts/'", run_text(self.jobs[CHANGES]))

    def test_detection_compares_against_the_merge_base(self):
        self.assertIn('"$BASE...$HEAD"', run_text(self.jobs[CHANGES]))

    def test_detection_fails_open_when_the_diff_cannot_be_read(self):
        # A failed diff must count as a chart change, so the lint can never be skipped by accident.
        script = run_text(self.jobs[CHANGES])
        self.assertIn('if ! git diff --name-only "$BASE...$HEAD"', script)
        self.assertIn("chart=true", script.split("elif", 1)[0])

    def test_detection_exposes_the_chart_output(self):
        self.assertIn("chart", self.jobs[CHANGES]["outputs"])


class ChartLintJobTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.jobs = load(CI_WORKFLOW)["jobs"]

    def test_lint_job_exists_with_a_readable_name(self):
        self.assertEqual(self.jobs[LINT]["name"], "Chart lint")

    def test_lint_job_waits_for_the_detection(self):
        self.assertIn(CHANGES, self.jobs[LINT]["needs"])

    def test_lint_job_runs_on_pull_requests_that_change_the_chart(self):
        condition = self.jobs[LINT]["if"]
        self.assertIn("github.event_name == 'pull_request'", condition)
        self.assertIn(f"needs.{CHANGES}.outputs.chart == 'true'", condition)

    def test_lint_job_also_runs_when_the_detection_did_not_succeed(self):
        self.assertIn(f"needs.{CHANGES}.result != 'success'", self.jobs[LINT]["if"])

    def test_lint_job_lints_the_chart_in_both_deploy_modes_and_the_scalable_profile(self):
        script = run_text(self.jobs[LINT])
        for values in ("charts/ingot/ci/allinone-values.yaml", "charts/ingot/ci/split-values.yaml",
                       "charts/ingot/tests/scalable/values.yaml"):
            self.assertIn(f"helm lint charts/ingot --strict -f {values}", script)

    def test_lint_job_runs_the_chart_unit_tests(self):
        self.assertIn("python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests", run_text(self.jobs[LINT]))

    def test_lint_job_checks_the_dashboard_segment_copy(self):
        self.assertIn("cmp charts/ingot/files/dashboard-segments.json", run_text(self.jobs[LINT]))

    def test_lint_job_checks_the_alert_rules_with_promtool(self):
        self.assertIn("check rules", run_text(self.jobs[LINT]))

    def test_lint_job_runs_chart_testing_lint_without_a_cluster(self):
        steps = self.jobs[LINT]["steps"]
        self.assertIn("ct lint --config ct.yaml --all", run_text(self.jobs[LINT]))
        self.assertFalse(any("kind" in str(step.get("uses", "")) for step in steps))

    def test_lint_job_does_not_install_the_chart_or_run_the_image_smoke_test(self):
        text = yaml.safe_dump(self.jobs[LINT])
        self.assertNotIn("ct install", text)
        self.assertNotIn("helm install", text)
        self.assertNotIn("download-artifact", text)

    def test_lint_job_is_not_a_required_light_check(self):
        self.assertNotIn(LINT, self.jobs["light-checks"]["needs"])

    def test_heavy_helm_job_keeps_its_label_gate(self):
        heavy = load(HEAVY_WORKFLOW)["jobs"]["helm"]
        self.assertIn("needs.gate.outputs.helm == 'true'", heavy["if"])


if __name__ == "__main__":
    unittest.main()
