"""Tests for .github/scripts/render_support_tables.py and the support-lines job of .github/workflows/ci.yml.

Every test works in its own temporary directory with explicit dates, so no test reads the clock or the network. The
repository tests read the files of this checkout only. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import datetime
import importlib.util
import io
import os
import shutil
import tempfile
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
REPO_DIR = os.path.dirname(os.path.dirname(SCRIPTS_DIR))
WORKFLOW = os.path.join(REPO_DIR, ".github", "workflows", "ci.yml")

_SPEC = importlib.util.spec_from_file_location(
    "render_support_tables", os.path.join(SCRIPTS_DIR, "render_support_tables.py")
)
render = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(render)

NEWEST_RELEASE = datetime.date(2026, 11, 9)


def line(name, kind="regular", status="previous", released="2026-08-09", end_of_life=None):
    entry = {"line": name, "kind": kind, "status": status, "released": released}
    if end_of_life is not None:
        entry["end_of_life"] = end_of_life
    return entry


def window_end(successor_release):
    """The regular end of life date of a line whose successor was released on the given ISO date."""
    return (datetime.date.fromisoformat(successor_release) + datetime.timedelta(days=90)).isoformat()


def regular_fixture():
    """A latest line 2.0 and a previous line 1.1 whose window ends 90 days after 2.0.0 shipped."""
    return [
        line("2.0", status="latest", released="2026-11-09"),
        line("1.1", status="previous", released="2026-08-09", end_of_life=window_end("2026-11-09")),
    ]


class Workspace:
    """Writes a support-lines.json and the two markdown files with their markers into a temporary root."""

    def __init__(self, lines):
        self.root = tempfile.mkdtemp(prefix="support-tables-")
        self.lines = lines
        self.write_lines(lines)
        os.makedirs(os.path.join(self.root, ".github"))
        self.write_text("SUPPORT.md", "# Support\n\nintro\n\n<!-- support-table:start -->\nold\n<!-- support-table:end -->\n\ntail\n")
        self.write_text(
            ".github/SECURITY.md",
            "# Security\n\n<!-- security-table:start -->\nold\n<!-- security-table:end -->\n\ncontact\n",
        )

    def write_lines(self, lines):
        import json

        self.write_text("support-lines.json", json.dumps({"lines": lines}, indent=2) + "\n")

    def write_text(self, relative, text):
        with open(os.path.join(self.root, relative), "w", encoding="utf-8") as handle:
            handle.write(text)

    def read_text(self, relative):
        with open(os.path.join(self.root, relative), encoding="utf-8") as handle:
            return handle.read()

    def run(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = render.main(["--root", self.root, *args])
        return code, out.getvalue(), err.getvalue()

    def close(self):
        shutil.rmtree(self.root, ignore_errors=True)


class ValidationTest(unittest.TestCase):
    def test_regular_line_ending_ninety_days_after_its_successor_is_valid(self):
        self.assertEqual([], render.check_lines(regular_fixture()))

    def test_regular_line_with_a_wrong_end_of_life_date_is_reported(self):
        lines = regular_fixture()
        lines[1]["end_of_life"] = "2027-02-01"

        errors = render.check_lines(lines)

        self.assertEqual(1, len(errors), errors)
        self.assertIn("1.1", errors[0], "the error must name the line whose date is wrong")

    def test_lts_line_with_a_later_end_of_life_date_is_valid(self):
        lines = regular_fixture()
        lines[1]["kind"] = "lts"
        lines[1]["end_of_life"] = "2027-06-30"

        self.assertEqual([], render.check_lines(lines))

    def test_lts_line_with_an_end_of_life_date_not_later_than_the_regular_one_is_reported(self):
        lines = regular_fixture()
        lines[1]["kind"] = "lts"
        lines[1]["end_of_life"] = window_end("2026-11-09")

        errors = render.check_lines(lines)

        self.assertEqual(1, len(errors), errors)
        self.assertIn("1.1", errors[0])

    def test_latest_line_with_an_end_of_life_date_is_reported(self):
        lines = regular_fixture()
        lines[0]["end_of_life"] = "2027-02-07"

        errors = render.check_lines(lines)

        self.assertTrue(any("2.0" in error for error in errors), errors)

    def test_first_line_that_is_not_latest_is_reported(self):
        lines = regular_fixture()
        lines[0]["status"] = "previous"

        self.assertTrue(render.check_lines(lines), "the first listed line must be the latest one")

    def test_unknown_kind_is_reported(self):
        lines = regular_fixture()
        lines[1]["kind"] = "forever"

        self.assertTrue(render.check_lines(lines))


class RenderingTest(unittest.TestCase):
    def test_support_table_lists_the_latest_line_first_with_every_fix(self):
        table = render.render_support_table(regular_fixture())

        first_row = table.splitlines()[2]
        self.assertIn("`2.0.x` (latest)", first_row)
        self.assertIn("Every fix, bugs and security", first_row)
        self.assertIn("Supported", first_row)

    def test_support_table_ends_with_the_older_lines_row(self):
        rows = render.render_support_table(regular_fixture()).splitlines()

        self.assertIn("Older lines", rows[-1])
        self.assertIn("Not supported", rows[-1])

    def test_archived_line_renders_the_end_of_its_window_and_no_fixes(self):
        lines = regular_fixture()
        lines[1]["status"] = "archived"

        table = render.render_support_table(lines)

        row = [r for r in table.splitlines() if "`1.1.x`" in r][0]
        self.assertIn(f"Window ended on {window_end('2026-11-09')}", row)
        self.assertNotIn("Security fixes only", row)

    def test_security_table_keeps_main_and_upstream_rows_after_the_lines(self):
        table = render.render_security_table(regular_fixture())

        rows = table.splitlines()
        self.assertIn("Ingot `1.1.x`", rows[3])
        self.assertIn("Ingot `main`", "\n".join(rows[4:]))
        self.assertIn("Upstream Reposilite < 3.x", rows[-1])

    def test_rendering_the_same_lines_twice_gives_the_same_text(self):
        first = render.render_support_table(regular_fixture())
        second = render.render_support_table(regular_fixture())

        self.assertEqual(first, second)


class LabelsAndBranchesTest(unittest.TestCase):
    def test_no_label_or_branch_is_listed_while_only_the_latest_line_is_open(self):
        lines = [line("2.0", status="latest", released="2026-11-09")]

        self.assertEqual([], render.open_branches(lines))

    def test_open_previous_line_yields_its_backport_label_and_release_branch(self):
        self.assertEqual(
            [("backport:1.1", "release/1.1.x")],
            render.open_branches(regular_fixture()),
        )

    def test_archived_line_yields_no_label_and_no_branch(self):
        lines = regular_fixture()
        lines[1]["status"] = "archived"

        self.assertEqual([], render.open_branches(lines))


class MarkerAndCommandTest(unittest.TestCase):
    def setUp(self):
        self.workspace = Workspace(regular_fixture())

    def tearDown(self):
        self.workspace.close()

    def test_check_fails_when_the_support_file_differs_from_the_lines(self):
        code, _, err = self.workspace.run("--mode", "check")

        self.assertEqual(1, code)
        self.assertIn("SUPPORT.md", err, "the message must name the file that differs")

    def test_write_then_check_passes_and_a_second_write_changes_nothing(self):
        self.assertEqual(0, self.workspace.run("--mode", "write")[0])
        after_first = self.workspace.read_text("SUPPORT.md")

        self.assertEqual(0, self.workspace.run("--mode", "check")[0])
        self.assertEqual(0, self.workspace.run("--mode", "write")[0])
        self.assertEqual(after_first, self.workspace.read_text("SUPPORT.md"))

    def test_write_keeps_the_text_outside_the_markers(self):
        self.workspace.run("--mode", "write")

        text = self.workspace.read_text("SUPPORT.md")
        self.assertTrue(text.startswith("# Support\n\nintro\n\n<!-- support-table:start -->\n"))
        self.assertTrue(text.endswith("<!-- support-table:end -->\n\ntail\n"))

    def test_missing_marker_is_reported_and_no_file_is_changed(self):
        self.workspace.write_text("SUPPORT.md", "# Support\n\nno markers here\n")

        code, _, err = self.workspace.run("--mode", "write")

        self.assertEqual(1, code)
        self.assertIn("SUPPORT.md", err)
        self.assertEqual("# Support\n\nno markers here\n", self.workspace.read_text("SUPPORT.md"))

    def test_invalid_lines_file_fails_before_any_table_is_written(self):
        lines = regular_fixture()
        lines[1]["end_of_life"] = "2027-02-01"
        self.workspace.write_lines(lines)

        code, _, err = self.workspace.run("--mode", "write")

        self.assertEqual(1, code)
        self.assertIn("1.1", err)
        self.assertEqual("# Support\n\nintro\n\n<!-- support-table:start -->\nold\n<!-- support-table:end -->\n\ntail\n",
                         self.workspace.read_text("SUPPORT.md"))

    def test_labels_mode_prints_one_label_and_branch_per_open_previous_line(self):
        code, out, _ = self.workspace.run("--mode", "labels")

        self.assertEqual(0, code)
        self.assertEqual("backport:1.1 release/1.1.x\n", out)


class RepositoryTest(unittest.TestCase):
    def test_repository_tables_match_support_lines(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = render.main(["--root", REPO_DIR, "--mode", "check"])

        self.assertEqual(0, code, err.getvalue() or "the tables differ from support-lines.json")


class WorkflowTest(unittest.TestCase):
    def test_ci_runs_the_support_lines_check_in_its_own_job(self):
        with open(WORKFLOW, encoding="utf-8") as handle:
            jobs = yaml.safe_load(handle)["jobs"]

        self.assertIn("support-lines", jobs)
        runs = "\n".join(step.get("run", "") for step in jobs["support-lines"]["steps"])
        self.assertIn("render_support_tables.py --mode check", runs)
        self.assertIn("render_support_tables.py --mode labels", runs)


if __name__ == "__main__":
    unittest.main()
