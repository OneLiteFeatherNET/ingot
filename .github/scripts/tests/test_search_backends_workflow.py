"""Tests for the shape of .github/workflows/search-backends.yml.

The workflow runs the search backend container tests. It is called by the heavy workflow on a label and keeps its
nightly schedule. A workflow-level path filter would leave its check pending when skipped, so none may return. The tests
read the file fresh in every test and need no network, no clock and no GitHub API. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
WORKFLOW = os.path.join(REPO_DIR, ".github", "workflows", "search-backends.yml")


def load():
    with open(WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def triggers():
    workflow = load()
    return workflow.get("on", workflow.get(True))


class SearchBackendsWorkflowTest(unittest.TestCase):
    def test_workflow_can_be_called_by_the_heavy_workflow(self):
        self.assertIn("workflow_call", triggers())

    def test_workflow_does_not_start_on_pull_requests_by_itself(self):
        self.assertNotIn("pull_request", triggers())

    def test_the_nightly_schedule_stays(self):
        schedule = triggers()["schedule"]
        self.assertEqual(1, len(schedule))

    def test_there_is_no_path_filter(self):
        self.assertNotIn("paths", str(triggers()))

    def test_the_job_keeps_its_check_name(self):
        self.assertEqual("Search backends", load()["jobs"]["search-backends"]["name"])

    def test_permissions_stay_read_only(self):
        self.assertEqual({"contents": "read"}, load()["permissions"])


if __name__ == "__main__":
    unittest.main()
