"""Tests for .github/scripts/check_dco.py and .github/workflows/dco.yml.

Each test creates its own git repository in a temporary directory with fixed identities and dates. The upstream history
is a local ref that the test creates, so no test reads the network or the clock. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import importlib.util
import io
import os
import re
import subprocess
import tempfile
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
REPO_DIR = os.path.dirname(os.path.dirname(SCRIPTS_DIR))
WORKFLOW = os.path.join(REPO_DIR, ".github", "workflows", "dco.yml")

_SPEC = importlib.util.spec_from_file_location("check_dco", os.path.join(SCRIPTS_DIR, "check_dco.py"))
check_dco = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(check_dco)

UPSTREAM_REF = "refs/dco/upstream-main"
MAINTAINER_NAME = "dzikoysk"
MAINTAINER_EMAIL = "dzikoysk@dzikoysk.net"
CONTRIBUTOR_NAME = "Jane Contributor"
CONTRIBUTOR_EMAIL = "jane@example.org"

GIT_ENV = {
    "GIT_CONFIG_NOSYSTEM": "1",
    "GIT_CONFIG_GLOBAL": os.devnull,
    "GIT_AUTHOR_DATE": "2026-01-01T00:00:00+0000",
    "GIT_COMMITTER_DATE": "2026-01-01T00:00:00+0000",
}


class Repo:
    """A throwaway repository. Branches are chosen explicitly with start(), so no commit moves a branch by accident."""

    def __init__(self, path):
        self.path = path
        self.git("init", "-q", "-b", "main")

    def git(self, *args, author=None, input_text=None):
        env = dict(os.environ, **GIT_ENV)
        name, email = author or (CONTRIBUTOR_NAME, CONTRIBUTOR_EMAIL)
        env.update({
            "GIT_AUTHOR_NAME": name,
            "GIT_AUTHOR_EMAIL": email,
            "GIT_COMMITTER_NAME": name,
            "GIT_COMMITTER_EMAIL": email,
        })
        result = subprocess.run(
            ["git", "-c", "commit.gpgsign=false", "-c", "core.hooksPath=" + os.devnull, *args],
            cwd=self.path, env=env, input=input_text, capture_output=True, text=True, check=True,
        )
        return result.stdout.strip()

    def start(self, branch, sha):
        self.git("checkout", "-q", "-B", branch, sha)
        return sha

    def commit(self, message, author=None):
        """Commits on the current branch and returns the new sha."""
        self.git("commit", "-q", "--allow-empty", "--no-verify", "-F", "-", author=author, input_text=message)
        return self.git("rev-parse", "HEAD")

    def merge(self, sha, message):
        self.git("merge", "-q", "--no-ff", "-m", message, sha)
        return self.git("rev-parse", "HEAD")

    def set_upstream(self, sha):
        self.git("update-ref", UPSTREAM_REF, sha)


def new_repo(test_case):
    directory = tempfile.TemporaryDirectory()
    test_case.addCleanup(directory.cleanup)
    return Repo(directory.name)


def base_commit(repo):
    return repo.commit("Initial commit of the base branch\n")


def signed(message, name=CONTRIBUTOR_NAME, email=CONTRIBUTOR_EMAIL):
    return f"{message}\n\nSigned-off-by: {name} <{email}>\n"


def cherry_pick(message, upstream_sha):
    return f"{message}\n\n(cherry picked from commit {upstream_sha})\n"


def verdicts_for(repo, base, head, is_bot=False):
    return check_dco.evaluate_range(repo.path, base, head, UPSTREAM_REF, is_bot=is_bot)


def run_main(repo, base, head, mode, is_bot=False):
    args = ["--repo", repo.path, "--base", base, "--head", head, "--upstream-ref", UPSTREAM_REF, "--mode", mode]
    if is_bot:
        args.append("--bot")
    output = io.StringIO()
    with contextlib.redirect_stdout(output):
        code = check_dco.main(args)
    return code, output.getvalue()


class CommitSignOffTests(unittest.TestCase):

    def test_signed_commit_with_matching_author_address_passes(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit(signed("Add a feature"))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("pass", verdict.status, verdict.reason)

    def test_sign_off_for_another_address_fails(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit(signed("Add a feature", name="Someone Else", email="else@example.org"))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("fail", verdict.status)

    def test_sign_off_address_comparison_ignores_case(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit(signed("Add a feature", email="Jane@Example.ORG"))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("pass", verdict.status, verdict.reason)

    def test_unsigned_commit_fails(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit("Add a feature without a sign-off\n")
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("fail", verdict.status)
        self.assertEqual(head, verdict.sha)


class ModeTests(unittest.TestCase):

    def test_unsigned_commit_only_warns_in_report_mode(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit("Add a feature without a sign-off\n")
        code, output = run_main(repo, base, head, "report")
        self.assertEqual(0, code)
        self.assertIn(f"::warning title=DCO::{head}", output)

    def test_unsigned_commit_fails_the_step_in_enforce_mode(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit("Add a feature without a sign-off\n")
        code, output = run_main(repo, base, head, "enforce")
        self.assertEqual(1, code)
        self.assertIn(f"::error title=DCO::{head}", output)

    def test_unset_mode_behaves_as_report(self):
        self.assertEqual("report", check_dco.parse_mode(""))

    def test_unknown_mode_is_rejected_with_the_value_in_the_message(self):
        with self.assertRaises(ValueError) as raised:
            check_dco.parse_mode("strict")
        self.assertIn("strict", str(raised.exception))

    def test_unknown_mode_fails_the_step(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit(signed("Add a feature"))
        code, output = run_main(repo, base, head, "strict")
        self.assertNotEqual(0, code)
        self.assertIn("strict", output)


class CherryPickTests(unittest.TestCase):

    def test_cherry_pick_of_an_upstream_ancestor_passes_without_sign_off(self):
        repo = new_repo(self)
        base = base_commit(repo)
        upstream = repo.start("upstream", base)
        upstream = repo.commit("Upstream fix\n")
        repo.set_upstream(upstream)
        repo.start("feature", base)
        head = repo.commit(cherry_pick("Sync upstream fix", upstream))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("pass", verdict.status, verdict.reason)

    def test_cherry_pick_naming_a_non_ancestor_is_unverified(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("upstream", base)
        upstream = repo.commit("Upstream fix\n")
        repo.set_upstream(upstream)
        repo.start("fork-only", base)
        forged = repo.commit("Commit only in this fork\n")
        repo.start("feature", base)
        head = repo.commit(cherry_pick("Sync something", forged))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("unverified", verdict.status)

    def test_cherry_pick_without_an_upstream_ref_is_unverified(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("upstream", base)
        upstream = repo.commit("Upstream fix\n")
        repo.start("feature", base)
        head = repo.commit(cherry_pick("Sync upstream fix", upstream))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("unverified", verdict.status)
        self.assertIn("upstream", verdict.reason)


class ExemptionTests(unittest.TestCase):

    def test_bot_pull_request_passes_unsigned_commits(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit("Bump a dependency\n")
        code, output = run_main(repo, base, head, "enforce", is_bot=True)
        self.assertEqual(0, code)
        self.assertNotIn("::error", output)

    def test_upstream_maintainer_author_name_without_trailer_fails(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("feature", base)
        head = repo.commit("Change made under the upstream name\n",
                           author=(MAINTAINER_NAME, MAINTAINER_EMAIL))
        [verdict] = verdicts_for(repo, base, head)
        self.assertEqual("fail", verdict.status)

    def test_merge_commit_is_not_checked(self):
        repo = new_repo(self)
        base = base_commit(repo)
        repo.start("side", base)
        side = repo.commit(signed("Side change"))
        repo.start("feature", base)
        feature = repo.commit(signed("Feature change"))
        merge = repo.merge(side, "Merge side into feature")
        checked = {verdict.sha for verdict in verdicts_for(repo, base, merge)}
        self.assertNotIn(merge, checked)
        self.assertIn(side, checked)
        self.assertIn(feature, checked)


class WorkflowShapeTests(unittest.TestCase):

    def load_workflow(self):
        with open(WORKFLOW, encoding="utf-8") as handle:
            return yaml.safe_load(handle)

    def test_workflow_triggers_on_pull_requests_into_main_only(self):
        workflow = self.load_workflow()
        # PyYAML reads the bare key `on` as the boolean True, so accept both spellings.
        triggers = workflow.get("on", workflow.get(True))
        self.assertEqual({"pull_request"}, set(triggers))
        self.assertEqual(["main"], triggers["pull_request"]["branches"])

    def test_workflow_permissions_are_contents_read_only(self):
        self.assertEqual({"contents": "read"}, self.load_workflow()["permissions"])

    def test_every_uses_line_names_a_full_sha_with_a_version_comment(self):
        pattern = re.compile(r"^\s*(?:- )?uses:\s*\S+@[0-9a-f]{40}\s+# v\d+\.\d+\.\d+\s*$")
        with open(WORKFLOW, encoding="utf-8") as handle:
            uses_lines = [line for line in handle if re.match(r"^\s*(?:- )?uses:", line)]
        self.assertTrue(uses_lines, "the workflow must reference at least one action")
        for line in uses_lines:
            self.assertRegex(line, pattern)


class WorkflowScriptSourceTests(unittest.TestCase):
    """The checker must come from the base branch, never from the pull request under test."""

    HEAD_SHA = "${{ github.event.pull_request.head.sha }}"
    BASE_SHA = "${{ github.event.pull_request.base.sha }}"

    def load_steps(self):
        with open(WORKFLOW, encoding="utf-8") as handle:
            workflow = yaml.safe_load(handle)
        return workflow["jobs"]["signoff"]["steps"]

    def checkout_steps(self):
        return [step for step in self.load_steps()
                if str(step.get("uses", "")).startswith("actions/checkout@")]

    def test_checkout_uses_the_base_sha(self):
        [checkout] = self.checkout_steps()
        self.assertEqual(self.BASE_SHA, checkout["with"]["ref"])

    def test_checkout_keeps_the_full_history(self):
        [checkout] = self.checkout_steps()
        self.assertEqual(0, checkout["with"]["fetch-depth"])

    def test_no_checkout_takes_the_head_sha_as_its_ref(self):
        for checkout in self.checkout_steps():
            self.assertNotEqual(self.HEAD_SHA, checkout.get("with", {}).get("ref"))

    def test_the_pull_request_head_is_fetched_as_objects_not_checked_out(self):
        fetches = [step["run"] for step in self.load_steps()
                   if "git fetch" in step.get("run", "") and "refs/pull/" in step.get("run", "")]
        self.assertEqual(1, len(fetches), "exactly one step fetches the pull request head")

    def test_the_checker_runs_from_the_workspace_without_a_working_directory_override(self):
        steps = [step for step in self.load_steps() if "check_dco.py" in step.get("run", "")]
        self.assertEqual(1, len(steps))
        self.assertIn("python3 .github/scripts/check_dco.py", steps[0]["run"])
        self.assertNotIn("working-directory", steps[0])


if __name__ == "__main__":
    unittest.main()
