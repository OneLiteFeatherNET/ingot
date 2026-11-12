"""Policy tests for .github/workflows/openapi-diff.yml.

The workflow decides whether a pull request may break the REST API. These tests read the workflow file fresh in every
test and assert one behaviour each. The title rule is checked by running the real bash of the title step with the title
in an environment variable, so no network, no registry and no Docker daemon are needed. The workflow is read as text
rather than parsed as YAML, so the tests need no third-party package on the CI runner.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import subprocess
import tempfile
import textwrap
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
WORKFLOW = os.path.join(os.path.dirname(os.path.dirname(TESTS_DIR)), "workflows", "openapi-diff.yml")

COMMIT_PIN = re.compile(r"^[a-z0-9-]+/[a-zA-Z0-9._-]+@[0-9a-f]{40}\s+# v\d+\.\d+\.\d+\s*$")
DIGEST_PIN = re.compile(r"^docker://[a-z0-9./-]+@sha256:[0-9a-f]{64}\s+# v\d+\.\d+\.\d+\s*$")


def load_workflow():
    """Reads the workflow file as text, or fails with a message naming it."""
    if not os.path.isfile(WORKFLOW):
        raise AssertionError(f"the workflow {WORKFLOW} must exist")
    with open(WORKFLOW, encoding="utf-8") as handle:
        return handle.read()


def indent_of(line):
    return len(line) - len(line.lstrip())


def step_block(text, step_id):
    """Returns the text of the step with the given id, from its `- name` line to the next step."""
    lines = text.splitlines()
    id_lines = [i for i, line in enumerate(lines) if line.strip() == f"id: {step_id}"]
    if not id_lines:
        raise AssertionError(f"the workflow has no step with id '{step_id}'")
    start = id_lines[0]
    begin = max(i for i in range(start) if lines[i].lstrip().startswith("- "))
    step_indent = indent_of(lines[begin])
    end = len(lines)
    for i in range(start + 1, len(lines)):
        stripped = lines[i].lstrip()
        if stripped.startswith("- ") and indent_of(lines[i]) <= step_indent:
            end = i
            break
    return "\n".join(lines[begin:end])


def run_script(block):
    """Returns the body of the `run: |` block of a step, dedented."""
    lines = block.splitlines()
    run_index = next((i for i, line in enumerate(lines) if line.strip() == "run: |"), None)
    if run_index is None:
        raise AssertionError("the step has no `run: |` block")
    run_indent = indent_of(lines[run_index])
    body = []
    for line in lines[run_index + 1:]:
        if line.strip() and indent_of(line) <= run_indent:
            break
        body.append(line)
    return textwrap.dedent("\n".join(body))


def step_args(block):
    """Returns the `args` string of a docker:// step."""
    match = re.search(r"^\s*args:\s*(.+)$", block, re.MULTILINE)
    if not match:
        raise AssertionError("the step has no `args` under `with`")
    return match.group(1).strip()


class ActionPinningTest(unittest.TestCase):

    def test_every_uses_is_pinned_by_commit_or_digest(self):
        uses_lines = [line.strip() for line in load_workflow().splitlines() if line.strip().startswith("uses:")]
        self.assertTrue(uses_lines, "the workflow must use at least one action")
        for line in uses_lines:
            value = line[len("uses:"):].strip()
            pinned = COMMIT_PIN.match(value) or DIGEST_PIN.match(value)
            self.assertIsNotNone(
                pinned,
                f"'{line}' must pin an action to a 40 character commit SHA or an image to a sha256 digest, "
                "with the version in a comment",
            )


class PermissionsTest(unittest.TestCase):

    def test_workflow_grants_contents_read_only(self):
        text = load_workflow()
        self.assertIn("permissions:\n  contents: read", text, "the workflow must grant contents: read")
        self.assertIsNone(re.search(r":\s*write\b", text), "the workflow must not request any write permission")


class TriggerTest(unittest.TestCase):

    def test_pull_requests_to_main_run_again_when_the_title_is_edited(self):
        text = load_workflow()
        self.assertIn("pull_request:", text, "the workflow must run on pull requests")
        self.assertIn('"edited"', text, "the workflow must run on edited, so fixing the title needs no new push")
        self.assertIn('branches: [ "main" ]', text, "the workflow must run for pull requests to main")


class TitleInputTest(unittest.TestCase):

    def test_pull_request_title_reaches_the_scripts_only_through_an_environment_variable(self):
        lines = load_workflow().splitlines()
        title_lines = [line.strip() for line in lines if "github.event.pull_request.title" in line]
        self.assertTrue(title_lines, "the workflow must read the pull request title")
        for line in title_lines:
            self.assertTrue(
                line.startswith("PR_TITLE:"),
                f"'{line}' interpolates the title into the workflow text, read it through an environment variable instead",
            )


class TitleMarkerTest(unittest.TestCase):

    def evaluate(self, title):
        """Runs the title step's real bash with the title in PR_TITLE and returns its declared output."""
        script = run_script(step_block(load_workflow(), "title"))
        with tempfile.TemporaryDirectory() as directory:
            output = os.path.join(directory, "github_output")
            env = {"PATH": os.environ.get("PATH", ""), "PR_TITLE": title, "GITHUB_OUTPUT": output}
            result = subprocess.run(["bash", "-c", script], env=env, capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, f"the title step failed for {title!r}: {result.stderr}")
            with open(output, encoding="utf-8") as handle:
                lines = [line.strip() for line in handle if line.startswith("declared=")]
        self.assertEqual(len(lines), 1, f"the title step must write exactly one declared output for {title!r}")
        return lines[0] == "declared=true"

    def test_marker_matches_breaking_titles_with_and_without_scope(self):
        for title in ("fix(api)!: drop route", "feat!: drop the health route", "refactor(maven)!: rename the group"):
            self.assertTrue(self.evaluate(title), f"{title!r} declares a break and must be recognised")

    def test_marker_ignores_titles_without_the_bang(self):
        for title in ("fix(api): drop route", "ci(api): fail pull requests that break the rest api", "fix: a note"):
            self.assertFalse(self.evaluate(title), f"{title!r} declares no break and must not be recognised")

    def test_marker_rejects_malformed_titles(self):
        for title in ("Fix(api)!: drop route", "fix(api) !: drop route", "fix(api)!drop route", "chore: a!: in the text"):
            self.assertFalse(self.evaluate(title), f"{title!r} is not a Conventional Commit with a marker")

    def test_title_text_is_never_run_as_shell(self):
        with tempfile.TemporaryDirectory() as directory:
            marker_file = os.path.join(directory, "executed")
            title = f"fix(api)!: $(touch {marker_file})"
            self.assertTrue(self.evaluate(title), "the title is still a declared break")
            self.assertFalse(os.path.exists(marker_file), "the title must never be executed by the shell")


class ReportTest(unittest.TestCase):

    def test_unmarked_title_fails_on_error_level_changes(self):
        args = step_args(step_block(load_workflow(), "enforced"))
        self.assertIn("--fail-on ERR", args, "without a marker, error-level changes must fail the check")

    def test_marked_title_only_reports_the_changes(self):
        args = step_args(step_block(load_workflow(), "declared"))
        self.assertNotIn("--fail-on", args, "with a marker, the changes are reported and must not fail the check")

    def test_both_reports_compare_the_snapshot_of_the_base_with_the_pull_request(self):
        for step_id in ("enforced", "declared"):
            args = step_args(step_block(load_workflow(), step_id))
            self.assertIn("/github/workspace/openapi-diff/base.json", args, f"step '{step_id}' must read the base snapshot")
            self.assertIn("/github/workspace/openapi-diff/revision.json", args, f"step '{step_id}' must read the revision")


if __name__ == "__main__":
    unittest.main()
