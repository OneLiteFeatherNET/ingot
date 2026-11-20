"""Shape tests for the cut-release-branch job of .github/workflows/release-please.yml.

When a new line ships on main (a release with patch 0), the job cuts `release/<previous line>.x` from the last tag of
that line, creates the `backport:<line>` label and a tracking issue, and opens the pull request that adds the new line to
support-lines.json (design decision 2). The decision itself is tested in test_release_branch.py; these tests guard the
wiring. Each test reads the workflow file fresh.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")
JOB = "cut-release-branch"


def load_job():
    with open(RELEASE_WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)["jobs"].get(JOB)


def run_text(job):
    return "\n".join(step.get("run") or "" for step in job.get("steps", []))


class CutReleaseBranchJobTest(unittest.TestCase):

    def test_job_exists(self):
        self.assertIsNotNone(load_job(), f"release-please.yml needs a {JOB} job")

    def test_job_runs_only_after_a_release_on_main(self):
        job = load_job()
        self.assertIn("release-please", job.get("needs"), "the cut follows the release that ships the new line")
        self.assertIn("needs.release-please.outputs.release_created == 'true'", job["if"], "no release, no cut")
        self.assertIn("github.ref == 'refs/heads/main'", job["if"], "only a new line on main cuts a branch")

    def test_decision_comes_from_the_tested_script(self):
        self.assertIn("release_branch.py cut", run_text(load_job()), "the YAML only wires the tested decision")

    def test_branch_is_created_only_on_a_create_decision(self):
        steps = [step for step in load_job()["steps"] if "git/refs" in (step.get("run") or "")]
        self.assertEqual(1, len(steps), "exactly one step creates the branch")
        self.assertIn("steps.cut.outputs.action == 'create'", steps[0].get("if", ""),
                      "an existing branch is never moved and a refusal never creates one")

    def test_job_creates_the_backport_label_and_the_tracking_issue(self):
        text = run_text(load_job())
        self.assertIn("gh label create", text, "the backport label of the line is created")
        self.assertIn("gh issue create", text, "a tracking issue names the end of the window")

    def test_support_lines_change_through_a_reviewed_pull_request(self):
        text = run_text(load_job())
        self.assertIn("release_branch.py add-line", text, "the new line is added by the tested script")
        self.assertIn("render_support_tables.py --mode write", text, "the tables follow the file")
        self.assertIn("gh pr create", text, "main changes only through review")
        self.assertNotIn("git push origin main", text, "the job never pushes to main")

    def test_job_token_is_limited(self):
        self.assertEqual({"contents": "read", "issues": "write"}, load_job().get("permissions"),
                         "pushes and pull requests use the release bot token, the job token only writes issues")


if __name__ == "__main__":
    unittest.main()
