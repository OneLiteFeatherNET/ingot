"""Tests for .github/scripts/release_branch.py.

The cut decision is pure: it takes the version tags with their commits and the commit of the release branch, and it never
reads the network, the clock or the file system. Each test names one behaviour and builds its own inputs.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import sys
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)

if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

import release_branch  # noqa: E402

OLD_TIP = "1111111111111111111111111111111111111111"
OLD_LAST_TAG_SHA = "2222222222222222222222222222222222222222"
MAIN_TIP = "3333333333333333333333333333333333333333"


class CutDecisionTest(unittest.TestCase):

    def test_cut_starts_the_branch_at_the_last_tag_of_the_previous_line(self):
        tags = {"v1.1.0": "aaaa", "v1.1.1": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, None)

        self.assertEqual("create", decision.action, "the branch does not exist yet, so the job creates it")
        self.assertEqual("release/1.1.x", decision.branch, "the previous line of 2.0.0 is 1.1")
        self.assertEqual("v1.1.1", decision.base_tag, "the base is the last release of the old line")
        self.assertEqual(OLD_LAST_TAG_SHA, decision.base_sha, "the branch points at the commit of v1.1.1, not at main")

    def test_cut_on_a_minor_release_uses_the_line_before_it(self):
        tags = {"v1.1.2": "bbbb", "v1.1.1": "aaaa", "v1.0.0": "cccc"}

        decision = release_branch.decide_cut("1.2.0", tags, None)

        self.assertEqual("release/1.1.x", decision.branch, "1.2.0 supersedes the 1.1 line, not the 1.0 line")
        self.assertEqual("v1.1.2", decision.base_tag, "the last release of 1.1 is 1.1.2")

    def test_cut_base_is_the_highest_tag_of_the_previous_line_only(self):
        tags = {"v1.0.5": "cccc", "v1.1.1": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, None)

        self.assertEqual("v1.1.1", decision.base_tag, "1.0.5 belongs to an older line and is not the base")

    def test_cut_compares_version_numbers_not_text(self):
        tags = {"v1.9.0": "aaaa", "v1.10.0": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, None)

        self.assertEqual("v1.10.0", decision.base_tag, "1.10.0 is newer than 1.9.0")

    def test_cut_accepts_tags_without_a_v_prefix(self):
        tags = {"1.1.1": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, None)

        self.assertEqual("1.1.1", decision.base_tag, "the tag spelling does not change the base")

    def test_cut_ignores_tags_that_are_not_versions(self):
        tags = {"nightly": "dddd", "latest": "eeee", "v1.1.1": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, None)

        self.assertEqual("v1.1.1", decision.base_tag, "only X.Y.Z tags count as releases")

    def test_cut_is_not_confused_by_the_tag_of_the_released_version(self):
        tags = {"v1.1.1": OLD_LAST_TAG_SHA, "v2.0.0": MAIN_TIP}

        decision = release_branch.decide_cut("2.0.0", tags, None)

        self.assertEqual("create", decision.action, "a rerun after the tag exists still cuts from the old line")
        self.assertEqual("v1.1.1", decision.base_tag, "2.0.0 is the released line, not its own previous line")

    def test_cut_is_skipped_for_a_patch_release(self):
        decision = release_branch.decide_cut("1.1.2", {"v1.1.1": OLD_LAST_TAG_SHA}, None)

        self.assertEqual("skip", decision.action, "a patch release never starts a line, so no branch is cut")
        self.assertIsNone(decision.branch, "no branch name is reported for a skipped cut")

    def test_cut_is_skipped_for_the_first_release_without_a_previous_line(self):
        decision = release_branch.decide_cut("1.0.0", {}, None)

        self.assertEqual("skip", decision.action, "there is no older line to keep receiving fixes")

    def test_cut_is_refused_for_a_release_older_than_an_existing_one(self):
        decision = release_branch.decide_cut("1.2.0", {"v2.0.0": MAIN_TIP}, None)

        self.assertEqual("refuse", decision.action, "only the newest release on main cuts a branch")
        self.assertIn("newer", decision.reason, "the reason says a newer release exists")

    def test_cut_exists_when_the_branch_already_points_at_the_last_tag(self):
        tags = {"v1.1.1": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, OLD_LAST_TAG_SHA)

        self.assertEqual("exists", decision.action, "a rerun finds the branch in place and does nothing")
        self.assertEqual(OLD_LAST_TAG_SHA, decision.base_sha, "the expected commit is still reported")

    def test_cut_refuses_when_the_branch_points_elsewhere(self):
        tags = {"v1.1.1": OLD_LAST_TAG_SHA}

        decision = release_branch.decide_cut("2.0.0", tags, OLD_TIP)

        self.assertEqual("refuse", decision.action, "an existing branch that differs is never moved")
        self.assertEqual("release/1.1.x", decision.branch, "the refusal names the branch for the maintainer")
        self.assertIn("release/1.1.x", decision.reason, "the reason names the branch")

    def test_cut_rejects_a_released_version_that_is_not_a_version(self):
        with self.assertRaises(ValueError, msg="2.0 is not a release version"):
            release_branch.decide_cut("2.0", {"v1.1.1": OLD_LAST_TAG_SHA}, None)


class LsRemoteTest(unittest.TestCase):

    def test_annotated_tag_resolves_to_its_commit(self):
        listing = f"9999\trefs/tags/v1.1.1\n{OLD_LAST_TAG_SHA}\trefs/tags/v1.1.1^{{}}\n"

        tags, _ = release_branch.parse_ls_remote(listing)

        self.assertEqual({"v1.1.1": OLD_LAST_TAG_SHA}, tags, "the peeled entry names the commit, not the tag object")

    def test_heads_are_read_by_branch_name(self):
        listing = f"{OLD_TIP}\trefs/heads/release/1.1.x\n"

        _, heads = release_branch.parse_ls_remote(listing)

        self.assertEqual({"release/1.1.x": OLD_TIP}, heads, "a head is keyed by the branch name without refs/heads/")


class AddLineTest(unittest.TestCase):

    def lines(self):
        return [{"line": "1.1", "kind": "regular", "status": "latest", "released": "2026-08-09"}]

    def test_new_line_becomes_the_latest_line_on_top(self):
        updated = release_branch.add_line(self.lines(), "2.0", "2026-12-01")

        self.assertEqual({"line": "2.0", "kind": "regular", "status": "latest", "released": "2026-12-01"}, updated[0],
                         "the line that just shipped is listed first as latest")

    def test_old_latest_line_opens_its_window_of_ninety_days(self):
        updated = release_branch.add_line(self.lines(), "2.0", "2026-12-01")

        self.assertEqual("previous", updated[1]["status"], "the superseded line receives security fixes")
        self.assertEqual("2027-03-01", updated[1]["end_of_life"], "the window ends 90 days after the ship date")

    def test_adding_a_listed_line_changes_nothing(self):
        once = release_branch.add_line(self.lines(), "2.0", "2026-12-01")

        self.assertEqual(once, release_branch.add_line(once, "2.0", "2026-12-02"), "a rerun never shifts the window")

    def test_input_is_not_modified(self):
        lines = self.lines()
        release_branch.add_line(lines, "2.0", "2026-12-01")

        self.assertEqual(self.lines(), lines, "the caller keeps its own list")


class CommandLineTest(unittest.TestCase):

    def run_cli(self, argv, stdin_text=""):
        import io

        stdout = io.StringIO()
        code = release_branch.main(argv, stdin=io.StringIO(stdin_text), stdout=stdout)
        return code, stdout.getvalue()

    def test_cut_command_prints_the_outputs_of_a_create(self):
        listing = f"{OLD_LAST_TAG_SHA}\trefs/tags/v1.1.1\n{MAIN_TIP}\trefs/tags/v2.0.0\n"

        code, out = self.run_cli(["cut", "2.0.0"], listing)

        self.assertEqual(0, code, "a cut is not a failure")
        self.assertIn("action=create\n", out, "the job reads the action")
        self.assertIn("branch=release/1.1.x\n", out, "the job reads the branch")
        self.assertIn(f"base_sha={OLD_LAST_TAG_SHA}\n", out, "the job creates the branch at the old tag")
        self.assertIn("line=1.1\n", out, "the job names the label and the issue after the old line")
        self.assertIn("new_line=2.0\n", out, "the job adds the new line to support-lines.json")

    def test_cut_command_reads_the_existing_branch(self):
        listing = f"{OLD_LAST_TAG_SHA}\trefs/tags/v1.1.1\n{OLD_LAST_TAG_SHA}\trefs/heads/release/1.1.x\n"

        code, out = self.run_cli(["cut", "2.0.0"], listing)

        self.assertEqual(0, code, "a rerun is not a failure")
        self.assertIn("action=exists\n", out, "a branch at the old tag is left alone")

    def test_cut_command_fails_on_a_refusal(self):
        listing = f"{OLD_LAST_TAG_SHA}\trefs/tags/v1.1.1\n{OLD_TIP}\trefs/heads/release/1.1.x\n"

        code, out = self.run_cli(["cut", "2.0.0"], listing)

        self.assertEqual(1, code, "a branch that points elsewhere stops the job")
        self.assertIn("action=refuse\n", out, "the refusal is still reported")

    def test_add_line_command_rewrites_the_file_in_its_own_layout(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            path = os.path.join(root, "support-lines.json")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write('{\n  "lines": [\n'
                             '    { "line": "1.1", "kind": "regular", "status": "latest", "released": "2026-08-09" }\n'
                             '  ]\n}\n')

            code, _ = self.run_cli(["add-line", "2.0", "2026-12-01", "--file", path])

            with open(path, encoding="utf-8") as handle:
                written = handle.read()

        self.assertEqual(0, code, "adding a line succeeds")
        self.assertEqual(
            '{\n  "lines": [\n'
            '    { "line": "2.0", "kind": "regular", "status": "latest", "released": "2026-12-01" },\n'
            '    { "line": "1.1", "kind": "regular", "status": "previous", "released": "2026-08-09", "end_of_life": "2027-03-01" }\n'
            '  ]\n}\n',
            written,
            "one line per entry, as the file is written by hand",
        )


if __name__ == "__main__":
    unittest.main()
