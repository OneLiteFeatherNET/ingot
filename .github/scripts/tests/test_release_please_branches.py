"""Shape tests for Release Please on release branches in .github/workflows/release-please.yml.

A release branch release/X.Y.x releases the patches of its line. Release Please runs on pushes to it with the branch as
its target, and the job refuses a manifest version outside the branch's line before the action can tag it. The
publish jobs then sign with the identity of the ref that built the release. Each test reads the workflow file fresh.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")


def load_workflow():
    with open(RELEASE_WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def triggers(workflow):
    # PyYAML reads the bare key `on` as the boolean True (YAML 1.1), so accept both spellings.
    return workflow.get("on", workflow.get(True))


def release_job():
    return load_workflow()["jobs"]["release-please"]


def step_index(job, predicate):
    for index, step in enumerate(job["steps"]):
        if predicate(step):
            return index
    return -1


def is_action_step(step):
    return "googleapis/release-please-action" in (step.get("uses") or "")


class ReleasePleaseOnReleaseBranchesTest(unittest.TestCase):

    def test_pushes_to_release_branches_run_release_please(self):
        self.assertIn("release/**", triggers(load_workflow())["push"]["branches"],
                      "a merged backport must reach Release Please on its branch")

    def test_main_stays_a_push_branch(self):
        self.assertIn("main", triggers(load_workflow())["push"]["branches"], "releases from main are unchanged")

    def test_action_targets_the_pushed_branch(self):
        job = release_job()
        action = job["steps"][step_index(job, is_action_step)]
        self.assertEqual("${{ github.ref_name }}", action["with"].get("target-branch"),
                         "the release pull request and the manifest belong to the pushed branch, not to main")

    def test_concurrency_stays_per_ref(self):
        self.assertIn("${{ github.ref }}", release_job()["concurrency"]["group"],
                      "a branch release must not queue behind a run on main")

    def test_line_check_runs_before_the_action(self):
        job = release_job()
        check = step_index(job, lambda step: "release_tags.py check-line" in (step.get("run") or ""))
        self.assertNotEqual(-1, check, "the version on the branch is checked with the tested rule")
        self.assertLess(check, step_index(job, is_action_step),
                        "a version outside the line is refused before the action tags it")

    def test_line_check_reads_the_manifest_of_the_pushed_branch(self):
        job = release_job()
        check = job["steps"][step_index(job, lambda step: "release_tags.py check-line" in (step.get("run") or ""))]
        self.assertIn(".release-please-manifest.json", check["run"], "the manifest holds the version to release")
        self.assertIn("github.ref", " ".join(str(value) for value in (check.get("env") or {}).values()),
                      "the line comes from the pushed branch")

    def test_chart_signature_identity_is_the_ref_that_built_the_release(self):
        identity = load_workflow()["jobs"]["chart"]["env"]["SIGNER_IDENTITY"]
        self.assertTrue(identity.endswith("release-please.yml@${{ github.ref }}"),
                        "a chart released from a release branch is signed by that branch, not by main")


if __name__ == "__main__":
    unittest.main()
