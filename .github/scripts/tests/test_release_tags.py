"""Tests for .github/scripts/release_tags.py.

The functions are pure: they take the version tags as a list and never read the network, the clock or the file system.
Each test names one behaviour and builds its own inputs.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import importlib.util
import io
import os
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)

_SPEC = importlib.util.spec_from_file_location("release_tags", os.path.join(SCRIPTS_DIR, "release_tags.py"))
release_tags = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(release_tags)


class NewestTagTest(unittest.TestCase):

    def test_newest_tag_is_the_highest_version_across_lines(self):
        tags = ["v1.1.2", "v2.0.0", "v1.1.1"]

        self.assertEqual("v2.0.0", release_tags.newest_tag(tags), "the newest line wins over a later patch of an old line")

    def test_newest_tag_compares_numbers_not_text(self):
        tags = ["v1.9.0", "v1.10.0"]

        self.assertEqual("v1.10.0", release_tags.newest_tag(tags), "1.10.0 is newer than 1.9.0")

    def test_newest_tag_ignores_tags_that_are_not_versions(self):
        tags = ["nightly", "v1.x", "latest", "v1.0.0"]

        self.assertEqual("v1.0.0", release_tags.newest_tag(tags), "only X.Y.Z tags count as releases")

    def test_newest_tag_is_none_without_versions(self):
        self.assertIsNone(release_tags.newest_tag(["nightly"]), "no release exists yet")


class HighestWithMajorTest(unittest.TestCase):

    def test_highest_with_major_stays_within_the_major(self):
        tags = ["v1.1.2", "v1.0.9", "v2.0.0"]

        self.assertEqual("v1.1.2", release_tags.highest_with_major(tags, 1), "2.0.0 is not a 1.x release")

    def test_highest_with_major_is_none_when_the_major_is_absent(self):
        self.assertIsNone(release_tags.highest_with_major(["v2.0.0"], 1), "there is no 1.x release")


class ContainerTagsTest(unittest.TestCase):

    def test_patch_on_old_line_moves_line_and_major_but_not_latest(self):
        tags = ["v1.1.1", "v1.1.2", "v2.0.0"]

        self.assertEqual(
            ["1.1.2", "1.1", "1"],
            release_tags.container_tags("1.1.2", tags),
            "1.1.2 on release/1.1.x while 2.0.0 exists moves 1.1 and 1, never latest",
        )

    def test_release_on_newest_line_moves_latest(self):
        tags = ["v1.1.2", "v2.0.0"]

        self.assertEqual(
            ["2.0.0", "2.0", "2", "latest"],
            release_tags.container_tags("2.0.0", tags),
            "2.0.0 is the highest version across all lines",
        )

    def test_major_tag_does_not_move_when_a_higher_minor_of_the_major_exists(self):
        tags = ["v1.1.2", "v1.2.0"]

        self.assertEqual(
            ["1.1.2", "1.1"],
            release_tags.container_tags("1.1.2", tags),
            "tag 1 belongs to 1.2.0 while that is the highest 1.x release",
        )

    def test_minor_tag_always_moves_with_its_line(self):
        tags = ["v1.1.2", "v1.2.0"]

        self.assertIn("1.1", release_tags.container_tags("1.1.2", tags), "1.1 moves on every release of its line")

    def test_first_release_of_a_new_line_on_main_moves_every_tag(self):
        self.assertEqual(
            ["1.2.0", "1.2", "1", "latest"],
            release_tags.container_tags("1.2.0", ["v1.1.2", "v1.2.0"]),
            "1.2.0 is the newest release and the highest 1.x release",
        )

    def test_version_is_counted_even_when_its_tag_is_not_listed(self):
        self.assertEqual(
            ["1.0.0", "1.0", "1", "latest"],
            release_tags.container_tags("1.0.0", []),
            "the release being built counts as an existing version",
        )

    def test_major_tag_moves_when_the_release_is_listed_with_a_v_prefix(self):
        self.assertEqual(
            ["2.0.0", "2.0", "2", "latest"],
            release_tags.container_tags("2.0.0", ["2.0.0", "v2.0.0"]),
            "the same version listed twice is still the highest one",
        )

    def test_container_tags_accept_a_v_prefixed_version(self):
        self.assertEqual(
            ["2.0.0", "2.0", "2", "latest"],
            release_tags.container_tags("v2.0.0", ["v2.0.0"]),
            "the tag spelling does not change the container tags",
        )


class BranchLineTest(unittest.TestCase):

    def test_branch_line_reads_the_release_branch_name(self):
        self.assertEqual("1.1", release_tags.branch_line("release/1.1.x"), "release/1.1.x is the 1.1 line")

    def test_branch_line_is_none_for_other_branches(self):
        self.assertIsNone(release_tags.branch_line("main"), "main is not a release line")
        self.assertIsNone(release_tags.branch_line("release/1.1"), "the x is part of the branch name")


class VersionOnLineTest(unittest.TestCase):

    def test_version_on_its_own_line_is_accepted(self):
        release_tags.require_version_on_line("1.1.2", "1.1")

    def test_version_of_another_major_is_refused(self):
        with self.assertRaises(release_tags.VersionOutsideLine, msg="2.0.0 cannot be released from release/1.1.x"):
            release_tags.require_version_on_line("2.0.0", "1.1")

    def test_version_with_a_longer_minor_is_refused(self):
        with self.assertRaises(release_tags.VersionOutsideLine, msg="1.10.0 is not on the 1.1 line"):
            release_tags.require_version_on_line("1.10.0", "1.1")

    def test_malformed_version_is_refused(self):
        with self.assertRaises(ValueError, msg="a version without patch part is not a release"):
            release_tags.require_version_on_line("1.1", "1.1")


class PreviousReleaseTest(unittest.TestCase):

    def test_previous_release_on_a_line_is_the_highest_tag_of_that_line(self):
        tags = ["v1.1.1", "v1.1.2", "v2.0.0"]

        self.assertEqual("v1.1.2", release_tags.previous_release(tags, "1.1"), "2.0.0 is not on the 1.1 line")

    def test_previous_release_on_a_line_compares_the_minor_as_a_number(self):
        tags = ["v1.9.0", "v1.10.0"]

        self.assertEqual("v1.9.0", release_tags.previous_release(tags, "1.9"), "1.10.0 is not on the 1.9 line")

    def test_previous_release_on_a_line_is_none_when_the_line_has_no_tag(self):
        self.assertIsNone(release_tags.previous_release(["v2.0.0"], "1.1"), "no 1.1 release exists yet")

    def test_previous_release_without_a_line_is_the_newest_tag(self):
        tags = ["v1.1.2", "v2.0.0"]

        self.assertEqual("v2.0.0", release_tags.previous_release(tags, None), "main compares across all lines")


class CommandLineTest(unittest.TestCase):

    def run_cli(self, argv, stdin_text):
        stdout = io.StringIO()
        code = release_tags.main(argv, stdin=io.StringIO(stdin_text), stdout=stdout)
        return code, stdout.getvalue()

    def test_container_tags_command_prints_one_tag_per_line(self):
        code, out = self.run_cli(["container-tags", "1.1.2"], "v1.1.1\nv2.0.0\nnightly\n")

        self.assertEqual(0, code, "a release on an old line is accepted")
        self.assertEqual("1.1.2\n1.1\n1\n", out, "1.1.2 moves 1.1 and 1, never latest, while 2.0.0 exists")

    def test_previous_command_reads_the_line_of_a_release_branch(self):
        code, out = self.run_cli(["previous", "release/1.1.x"], "v1.1.1\nv1.1.2\nv2.0.0\n")

        self.assertEqual(0, code, "a release branch names its line")
        self.assertEqual("v1.1.2\n", out, "the previous release of a branch is the highest tag of its line")

    def test_previous_command_on_main_reads_the_newest_tag(self):
        code, out = self.run_cli(["previous", "main"], "v1.1.2\nv2.0.0\n")

        self.assertEqual(0, code, "main has no line")
        self.assertEqual("v2.0.0\n", out, "main takes the highest tag overall")

    def test_previous_command_prints_nothing_when_no_release_exists(self):
        code, out = self.run_cli(["previous", "release/1.1.x"], "v2.0.0\n")

        self.assertNotEqual(0, code, "a missing previous release must fail the job")
        self.assertEqual("", out, "nothing is printed when no tag exists")

    def test_newest_command_prints_the_highest_tag_across_lines(self):
        code, out = self.run_cli(["newest"], "v2.0.0\nv1.1.2\nnightly\n")

        self.assertEqual(0, code, "a repository with releases has a newest tag")
        self.assertEqual("v2.0.0\n", out, "a later patch of an old line never becomes the newest tag")

    def test_newest_command_fails_without_a_release(self):
        code, out = self.run_cli(["newest"], "nightly\n")

        self.assertNotEqual(0, code, "without a release there is no tag to mark as Latest")
        self.assertEqual("", out, "nothing is printed when no tag exists")


if __name__ == "__main__":
    unittest.main()
