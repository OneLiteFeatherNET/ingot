"""Tests for .github/scripts/support_window.py.

The window decision is pure: the date is a parameter, so no test reads the clock. Each test names one behaviour and
builds its own lines; the command line tests write into their own temporary directory.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import io
import os
import sys
import tempfile
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)

if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

import support_window  # noqa: E402


def lines():
    return [
        {"line": "2.0", "kind": "regular", "status": "latest", "released": "2026-12-01"},
        {"line": "1.1", "kind": "regular", "status": "previous", "released": "2026-08-09", "end_of_life": "2027-03-01"},
        {"line": "1.0", "kind": "regular", "status": "archived", "released": "2026-08-08", "end_of_life": "2026-11-07"},
    ]


class DueLinesTest(unittest.TestCase):

    def test_open_line_is_not_due_before_its_end_of_life(self):
        self.assertEqual([], support_window.due_lines(lines(), "2027-02-28"), "the window is still open the day before")

    def test_open_line_is_due_on_its_end_of_life(self):
        self.assertEqual(["1.1"], support_window.due_lines(lines(), "2027-03-01"), "the window ends on the date itself")

    def test_open_line_stays_due_after_its_end_of_life(self):
        self.assertEqual(["1.1"], support_window.due_lines(lines(), "2027-04-01"), "a missed run catches up later")

    def test_archived_line_is_never_due_again(self):
        self.assertNotIn("1.0", support_window.due_lines(lines(), "2030-01-01"), "an archived line is done")

    def test_date_is_compared_as_a_date(self):
        entries = [{"line": "1.1", "kind": "regular", "status": "previous", "released": "2026-08-09", "end_of_life": "2027-10-01"}]

        self.assertEqual([], support_window.due_lines(entries, "2027-09-30"), "September comes before October")

    def test_malformed_date_is_refused(self):
        with self.assertRaises(ValueError, msg="a date that is not ISO must not pass as never due"):
            support_window.due_lines(lines(), "01.03.2027")


class ArchiveTest(unittest.TestCase):

    def test_archive_closes_the_line(self):
        updated = support_window.archive(lines(), "1.1")

        self.assertEqual("archived", updated[1]["status"], "the line receives no more fixes")
        self.assertEqual("2027-03-01", updated[1]["end_of_life"], "the end of life date stays as it was")

    def test_archive_leaves_other_lines_and_the_input_alone(self):
        original = lines()
        updated = support_window.archive(original, "1.1")

        self.assertEqual(lines(), original, "the caller keeps its own list")
        self.assertEqual(lines()[0], updated[0], "the latest line is untouched")

    def test_archived_lines_are_listed(self):
        self.assertEqual(["1.0"], support_window.archived_lines(lines()), "only closed lines lose their branch")


class CommandLineTest(unittest.TestCase):

    def write_file(self, root):
        path = os.path.join(root, "support-lines.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(
                '{\n  "lines": [\n'
                '    { "line": "2.0", "kind": "regular", "status": "latest", "released": "2026-12-01" },\n'
                '    { "line": "1.1", "kind": "regular", "status": "previous", "released": "2026-08-09", "end_of_life": "2027-03-01" }\n'
                '  ]\n}\n'
            )
        return path

    def run_cli(self, argv):
        stdout = io.StringIO()
        code = support_window.main(argv, stdout=stdout)
        return code, stdout.getvalue()

    def test_due_command_prints_the_due_lines(self):
        with tempfile.TemporaryDirectory() as root:
            code, out = self.run_cli(["due", "--today", "2027-03-01", "--file", self.write_file(root)])

        self.assertEqual(0, code, "a due line is not an error")
        self.assertEqual("1.1\n", out, "one line per due release line")

    def test_due_command_prints_nothing_before_the_date(self):
        with tempfile.TemporaryDirectory() as root:
            code, out = self.run_cli(["due", "--today", "2027-02-28", "--file", self.write_file(root)])

        self.assertEqual(0, code, "nothing due is not an error")
        self.assertEqual("", out, "the job changes nothing before the end of life date")

    def test_archive_command_rewrites_the_file_in_its_own_layout(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.write_file(root)
            code, _ = self.run_cli(["archive", "1.1", "--file", path])
            with open(path, encoding="utf-8") as handle:
                written = handle.read()

        self.assertEqual(0, code, "archiving succeeds")
        self.assertEqual(
            '{\n  "lines": [\n'
            '    { "line": "2.0", "kind": "regular", "status": "latest", "released": "2026-12-01" },\n'
            '    { "line": "1.1", "kind": "regular", "status": "archived", "released": "2026-08-09", "end_of_life": "2027-03-01" }\n'
            '  ]\n}\n',
            written,
            "one line per entry, as the file is written by hand",
        )

    def test_archived_command_prints_the_closed_lines(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.write_file(root)
            self.run_cli(["archive", "1.1", "--file", path])
            code, out = self.run_cli(["archived", "--file", path])

        self.assertEqual(0, code, "listing succeeds")
        self.assertEqual("1.1\n", out, "the closed line is listed for its branch rename")


if __name__ == "__main__":
    unittest.main()
