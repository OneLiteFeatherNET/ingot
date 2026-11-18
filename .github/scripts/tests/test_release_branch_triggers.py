"""Trigger tests for the workflows that run checks on release branches.

A release branch is named release/<major>.<minor>.x. Its pull requests and pushes must run the same checks as main,
so each workflow has to name release/** next to main. Each test reads one workflow file fresh and asserts one behaviour.
"""

import os
import unittest

import yaml


REPO_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
WORKFLOWS = os.path.join(REPO_DIR, ".github", "workflows")
RELEASE_BRANCHES = "release/**"


def load_triggers(name):
    with open(os.path.join(WORKFLOWS, name), encoding="utf-8") as handle:
        workflow = yaml.safe_load(handle)
    # PyYAML reads the bare key `on` as the boolean True (YAML 1.1), so accept both spellings.
    return workflow.get("on", workflow.get(True))


class ReleaseBranchTriggerTest(unittest.TestCase):

    def test_ci_runs_pushes_to_release_branches(self):
        self.assertIn(RELEASE_BRANCHES, load_triggers("ci.yml")["push"]["branches"])

    def test_ci_runs_pull_requests_into_release_branches(self):
        self.assertIn(RELEASE_BRANCHES, load_triggers("ci.yml")["pull_request"]["branches"])

    def test_heavy_runs_pull_requests_into_release_branches(self):
        self.assertIn(RELEASE_BRANCHES, load_triggers("heavy.yml")["pull_request"]["branches"])

    def test_dco_runs_pull_requests_into_release_branches(self):
        self.assertIn(RELEASE_BRANCHES, load_triggers("dco.yml")["pull_request"]["branches"])

    def test_openapi_diff_runs_pull_requests_into_release_branches(self):
        self.assertIn(RELEASE_BRANCHES, load_triggers("openapi-diff.yml")["pull_request"]["branches"])

    def test_codeql_runs_pushes_to_release_branches(self):
        self.assertIn(RELEASE_BRANCHES, load_triggers("codeql.yml")["push"]["branches"])

    def test_main_stays_a_trigger_branch_of_every_workflow(self):
        for name, key in (("ci.yml", "push"), ("ci.yml", "pull_request"), ("heavy.yml", "pull_request"),
                          ("dco.yml", "pull_request"), ("openapi-diff.yml", "pull_request"), ("codeql.yml", "push")):
            with self.subTest(workflow=name, event=key):
                self.assertIn("main", load_triggers(name)[key]["branches"])


if __name__ == "__main__":
    unittest.main()
