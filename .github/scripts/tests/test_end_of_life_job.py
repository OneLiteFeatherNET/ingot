"""Shape tests for the end-of-life job of .github/workflows/release-please.yml.

A daily schedule ends the window of a release line on its end of life date (design decision 8): it opens the pull
request that archives the line in support-lines.json, removes the backport label from open pull requests, and renames
`release/<line>.x` to `archive/<line>.x` once the archived line is on main. The schedule must never start a release.
Each test reads the workflow file fresh.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")
JOB = "end-of-life"


def load():
    with open(RELEASE_WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def triggers(workflow):
    # PyYAML reads the bare key `on` as the boolean True (YAML 1.1).
    return workflow.get("on", workflow.get(True))


def run_text(job):
    return "\n".join(step.get("run") or "" for step in job.get("steps", []))


class EndOfLifeJobTest(unittest.TestCase):

    def test_workflow_runs_daily(self):
        self.assertIn("schedule", triggers(load()), "the window end is checked every day")

    def test_job_runs_only_on_the_schedule(self):
        job = load()["jobs"].get(JOB)
        self.assertIsNotNone(job, f"release-please.yml needs an {JOB} job")
        self.assertEqual("github.event_name == 'schedule'", job["if"], "a push never archives a line")

    def test_schedule_never_starts_a_release(self):
        self.assertEqual("github.event_name == 'push'", load()["jobs"]["release-please"]["if"],
                         "Release Please runs on pushes only, so the schedule cannot release")

    def test_date_is_passed_to_the_tested_rule(self):
        text = run_text(load()["jobs"][JOB])
        self.assertIn("support_window.py due --today", text, "the date decision comes from the script, as a parameter")

    def test_line_is_archived_through_a_reviewed_pull_request(self):
        text = run_text(load()["jobs"][JOB])
        self.assertIn("support_window.py archive", text, "the line is closed by the tested script")
        self.assertIn("render_support_tables.py --mode write", text, "the tables follow the file")
        self.assertIn("gh pr create", text, "main changes only through review")
        self.assertNotIn("git push origin main", text, "the job never pushes to main")

    def test_backport_label_is_removed_from_open_pull_requests(self):
        self.assertIn("--remove-label", run_text(load()["jobs"][JOB]), "a closed line takes no more backports")

    def test_branch_is_renamed_not_deleted(self):
        text = run_text(load()["jobs"][JOB])
        self.assertIn("support_window.py archived", text, "only lines archived on main lose their branch")
        self.assertIn("archive/", text, "the branch is renamed to archive/<line>.x")
        self.assertNotIn("-X DELETE", text, "history stays visible, nothing is deleted")


if __name__ == "__main__":
    unittest.main()
