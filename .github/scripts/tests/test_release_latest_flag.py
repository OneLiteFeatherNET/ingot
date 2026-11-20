"""Shape tests for the GitHub Latest flag step of .github/workflows/release-please.yml.

GitHub marks the most recently created release as Latest, and Release Please has no input for that flag. A patch release
on an older line would therefore take the badge from the newest line. A job after every release puts the flag back on
the highest version tag, which `release_tags.py newest` picks. These tests read the workflow file fresh in every test.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")
JOB = "latest-flag"


def load_job():
    with open(RELEASE_WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)["jobs"].get(JOB)


def run_text(job):
    return "\n".join(step.get("run") or "" for step in job.get("steps", []))


class LatestFlagJobTest(unittest.TestCase):

    def test_job_exists(self):
        self.assertIsNotNone(load_job(), f"release-please.yml needs a {JOB} job")

    def test_job_runs_only_after_a_release(self):
        job = load_job()
        self.assertIn("release-please", job.get("needs"), "the flag depends on the release that was just created")
        self.assertIn("needs.release-please.outputs.release_created == 'true'", job.get("if", ""),
                      "without a release there is nothing to mark")

    def test_job_picks_the_newest_tag_with_the_release_tag_rules(self):
        self.assertIn("release_tags.py newest", run_text(load_job()),
                      "the newest tag across lines comes from the tested rule, not from shell sorting")

    def test_job_marks_that_tag_as_latest(self):
        text = run_text(load_job())
        self.assertIn("gh release edit", text, "the flag is set on an existing release")
        self.assertIn("--latest", text, "the release is marked explicitly as Latest")

    def test_job_may_only_write_contents(self):
        self.assertEqual({"contents": "write"}, load_job().get("permissions"),
                         "editing a release needs contents: write and nothing else")


if __name__ == "__main__":
    unittest.main()
