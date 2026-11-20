"""Shape tests for .github/workflows/backport.yml.

The backport workflow runs on pull_request_target, so it holds a write token while a pull request from a fork may have
triggered it. These tests guard the rules that keep that safe and the behaviour of the design (decision 7): only merged
pull requests into main, never the head of the pull request, a cherry-pick with -x and -s of the merge commit, and an
abort that leaves no branch behind on a conflict. Each test reads the workflow file fresh.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import json
import os
import re
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "backport.yml")
SHA_PIN = re.compile(r"@[0-9a-f]{40}$")


def load():
    with open(WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def triggers(workflow):
    # PyYAML reads the bare key `on` as the boolean True (YAML 1.1).
    return workflow.get("on", workflow.get(True))


def job(workflow):
    jobs = workflow["jobs"]
    return jobs["backport"]


def run_text(workflow):
    return "\n".join(step.get("run") or "" for step in job(workflow).get("steps", []))


class BackportTriggerTest(unittest.TestCase):

    def test_runs_on_closed_and_labeled_pull_requests_into_main(self):
        trigger = triggers(load())["pull_request_target"]
        self.assertEqual(["closed", "labeled"], sorted(trigger["types"]), "a merge and a later label both start a backport")
        self.assertEqual(["main"], trigger["branches"], "only pull requests into main are backported")

    def test_job_runs_only_for_merged_pull_requests(self):
        condition = job(load())["if"]
        self.assertIn("github.event.pull_request.merged", condition, "an unmerged pull request is never backported")

    def test_workflow_token_is_read_only_by_default(self):
        self.assertEqual({"contents": "read"}, load().get("permissions"), "write access is granted per job only")


class BackportSafetyTest(unittest.TestCase):

    def test_checkout_never_reads_the_pull_request_head(self):
        for step in job(load())["steps"]:
            if (step.get("uses") or "").startswith("actions/checkout@"):
                ref = str((step.get("with") or {}).get("ref", ""))
                self.assertEqual("main", ref, "the workflow checks out main, never the head of a fork")

    def test_no_run_step_interpolates_pull_request_text(self):
        for step in job(load())["steps"]:
            run = step.get("run") or ""
            self.assertNotIn("github.event.pull_request.title", run, "the title reaches the shell through env only")
            self.assertNotIn("github.event.pull_request.body", run, "the body reaches the shell through env only")

    def test_every_action_is_pinned_by_sha(self):
        for step in job(load())["steps"]:
            uses = step.get("uses")
            if uses:
                self.assertRegex(uses, SHA_PIN, f"{uses} must be pinned by commit")


class BackportBehaviourTest(unittest.TestCase):

    def test_decision_comes_from_the_tested_script(self):
        self.assertIn("backport_decision.py", run_text(load()), "the YAML only wires the tested decision")

    def test_cherry_pick_keeps_the_trailer_and_signs_off(self):
        self.assertIn("git cherry-pick -x -s", run_text(load()), "-x names the source commit, -s certifies the DCO")

    def test_cherry_pick_uses_the_merge_commit(self):
        steps = json.dumps(job(load())["steps"])
        self.assertIn("github.event.pull_request.merge_commit_sha", steps, "the squashed commit on main is picked")

    def test_branch_comes_from_the_release_branch(self):
        self.assertIn('git switch --quiet -c "$work" "origin/${branch}"', run_text(load()),
                      "a backport branch starts at the release branch the decision names")

    def test_conflict_aborts_and_comments(self):
        text = run_text(load())
        self.assertIn("git cherry-pick --abort", text, "a conflict leaves no half applied pick")
        self.assertIn("gh pr comment", text, "the source pull request gets the manual steps")

    def test_pull_request_is_opened_against_the_release_branch(self):
        self.assertIn("gh pr create", run_text(load()), "a clean pick opens a pull request")
        self.assertIn('--base "$branch"', run_text(load()), "the base is the release branch of the line")


if __name__ == "__main__":
    unittest.main()
