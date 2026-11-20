"""Tests for .github/scripts/backport_decision.py.

The decision is pure: it takes the title, body, labels and base of a merged pull request and the lines that have a release
branch. It never reads the network, the clock or the file system. Each test names one behaviour and builds its own inputs.

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

import backport_decision  # noqa: E402


def decide(title, labels, body="", lines=("1.1",), base="main", merged=True):
    return backport_decision.decide(
        title=title,
        body=body,
        labels=labels,
        base_ref=base,
        merged=merged,
        lines_with_branch=set(lines),
    )


class BackportLabelTest(unittest.TestCase):

    def test_backport_label_names_its_line(self):
        self.assertEqual("1.1", backport_decision.parse_backport_label("backport:1.1"), "the label carries the line")

    def test_backport_label_accepts_a_two_digit_minor(self):
        self.assertEqual("1.10", backport_decision.parse_backport_label("backport:1.10"), "1.10 is a line of its own")

    def test_label_that_is_not_a_backport_label_is_none(self):
        self.assertIsNone(backport_decision.parse_backport_label("bug"), "an unrelated label names no line")

    def test_backport_label_with_a_patch_part_is_none(self):
        self.assertIsNone(backport_decision.parse_backport_label("backport:1.1.2"), "a line is major.minor, not a version")


class SecurityFixDecisionTest(unittest.TestCase):

    def test_security_fix_is_backported_to_a_line_with_a_branch(self):
        decisions = decide("fix(security): reject traversal in paths", ["backport:1.1"])

        self.assertEqual(1, len(decisions), "one backport label gives one decision")
        self.assertEqual("backport", decisions[0].action, "a security fix goes to the labelled line")
        self.assertEqual("1.1", decisions[0].line, "the decision names the line")
        self.assertEqual("release/1.1.x", decisions[0].branch, "the target is the release branch of the line")

    def test_label_for_a_line_without_a_branch_is_refused(self):
        decisions = decide("fix(security): reject traversal", ["backport:1.1"], lines=("1.0",))

        self.assertEqual("refuse", decisions[0].action, "no branch exists for 1.1")
        self.assertIn("1.1", decisions[0].reason, "the reason names the missing line")

    def test_security_fix_with_a_breaking_bang_is_refused(self):
        decisions = decide("fix(security)!: drop the legacy token format", ["backport:1.1"])

        self.assertEqual("refuse", decisions[0].action, "a breaking change never reaches a release line")

    def test_security_fix_with_a_breaking_footer_is_refused(self):
        decisions = decide(
            "fix(security): tighten the token check",
            ["backport:1.1"],
            body="Some text.\n\nBREAKING CHANGE: tokens older than a day are rejected",
        )

        self.assertEqual("refuse", decisions[0].action, "a breaking footer refuses the backport even on a security fix")

    def test_security_fix_with_a_hyphenated_breaking_footer_is_refused(self):
        decisions = decide(
            "fix(security): tighten the token check",
            ["backport:1.1"],
            body="BREAKING-CHANGE: tokens older than a day are rejected",
        )

        self.assertEqual("refuse", decisions[0].action, "BREAKING-CHANGE is the same footer in Conventional Commits")

    def test_security_fix_with_another_scope_is_refused(self):
        decisions = decide("fix(auth): reject traversal", ["backport:1.1"])

        self.assertEqual("refuse", decisions[0].action, "only the scope security marks a backportable fix")


class OtherChangeDecisionTest(unittest.TestCase):

    def test_plain_fix_is_refused_because_it_ships_with_main(self):
        decisions = decide("fix: handle an empty path", ["backport:1.1"])

        self.assertEqual("refuse", decisions[0].action, "a plain fix is not a security fix")
        self.assertIn("next release", decisions[0].reason, "the reason says the fix ships with the next release of main")

    def test_performance_change_is_refused_with_a_reason(self):
        decisions = decide("perf: cache the artifact index", ["backport:1.1"])

        self.assertEqual("refuse", decisions[0].action, "a performance change stays on main")
        self.assertIn("performance", decisions[0].reason, "the reason names the performance rule")

    def test_feature_is_refused_with_a_reason(self):
        decisions = decide("feat(ui): add a search filter", ["backport:1.1"])

        self.assertEqual("refuse", decisions[0].action, "a feature is never backported")
        self.assertIn("feature", decisions[0].reason, "the reason names the feature rule")

    def test_types_that_are_never_backported_are_refused(self):
        for title in (
            "docs: explain the window",
            "test(ui): cover the delete dialog",
            "build: pin the base image",
            "ci(release): add the cut job",
            "refactor(ui): type the dialogs",
            "chore: bump the lock file",
            "revert: undo the cache change",
        ):
            with self.subTest(title=title):
                decisions = decide(title, ["backport:1.1"])

                self.assertEqual("refuse", decisions[0].action, "only a security fix reaches a previous line")

    def test_title_that_is_not_a_conventional_commit_is_refused(self):
        decisions = decide("Fix the security problem", ["backport:1.1"])

        self.assertEqual("refuse", decisions[0].action, "the title must be a Conventional Commit to be classified")
        self.assertIn("Conventional Commit", decisions[0].reason, "the reason names the title rule")

    def test_no_label_overrides_the_security_only_rule(self):
        decisions = decide("docs: explain the window", ["backport:1.1", "needs-review"])

        self.assertEqual(["refuse"], [d.action for d in decisions], "a docs change is refused whatever else it carries")


class LabelHandlingTest(unittest.TestCase):

    def test_pull_request_without_a_backport_label_has_no_decision(self):
        self.assertEqual([], decide("fix(security): reject traversal", ["bug"]), "nothing was asked for")

    def test_each_backport_label_gets_its_own_decision(self):
        decisions = decide("fix(security): reject traversal", ["backport:1.1", "backport:1.0"], lines=("1.1",))

        self.assertEqual(["backport", "refuse"], [d.action for d in decisions], "1.1 has a branch, 1.0 does not")
        self.assertEqual(["1.1", "1.0"], [d.line for d in decisions], "the decisions keep the order of the labels")

    def test_malformed_backport_label_is_refused(self):
        decisions = decide("fix(security): reject traversal", ["backport:1.1.2"])

        self.assertEqual("refuse", decisions[0].action, "a label that names no line cannot be honoured")
        self.assertIsNone(decisions[0].line, "no line is named by a malformed label")

    def test_unmerged_pull_request_has_no_decision(self):
        self.assertEqual(
            [],
            decide("fix(security): reject traversal", ["backport:1.1"], merged=False),
            "only a merged change reaches a line",
        )

    def test_pull_request_into_a_release_branch_has_no_decision(self):
        self.assertEqual(
            [],
            decide("fix(security): reject traversal", ["backport:1.1"], base="release/1.1.x"),
            "a backport is decided from main only",
        )


class CommandLineTest(unittest.TestCase):

    def run_cli(self, event):
        import io
        import json

        stdout = io.StringIO()
        code = backport_decision.main([], stdin=io.StringIO(json.dumps(event)), stdout=stdout)
        return code, stdout.getvalue()

    def event(self, **overrides):
        event = {
            "title": "fix(security): reject a path outside the repository",
            "body": "",
            "labels": ["backport:1.1"],
            "base_ref": "main",
            "merged": True,
            "lines_with_branch": ["1.1"],
        }
        event.update(overrides)
        return event

    def test_command_prints_one_tab_separated_line_per_decision(self):
        code, out = self.run_cli(self.event())

        self.assertEqual(0, code, "a decision is not a failure")
        self.assertEqual("backport\t1.1\trelease/1.1.x\tsecurity fix for line 1.1\n", out,
                         "the workflow reads action, line, branch and reason from one line")

    def test_command_leaves_empty_fields_for_a_refused_label_without_line(self):
        code, out = self.run_cli(self.event(labels=["backport:latest"]))

        self.assertEqual(0, code, "a refusal is reported, not raised")
        self.assertEqual("refuse\t\t\t" + backport_decision.REASON_MALFORMED + "\n", out,
                         "a malformed label has neither a line nor a branch")

    def test_command_prints_nothing_for_an_unmerged_pull_request(self):
        code, out = self.run_cli(self.event(merged=False))

        self.assertEqual(0, code, "a closed pull request is not an error")
        self.assertEqual("", out, "a pull request that was not merged is never backported")


if __name__ == "__main__":
    unittest.main()
