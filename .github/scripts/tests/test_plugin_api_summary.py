"""Tests for .github/scripts/plugin_api_summary.py, which counts the findings of the Ingot plugin API report by group.

Each test writes its own report into a temporary directory and names one behaviour. The report is read as text, so no test
needs Gradle, the network or the clock.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import importlib.util
import io
import os
import tempfile
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)

_SPEC = importlib.util.spec_from_file_location("plugin_api_summary", os.path.join(SCRIPTS_DIR, "plugin_api_summary.py"))
plugin_api_summary = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(plugin_api_summary)

REPORT = """# Ingot plugin API report

## Promised

- `com.reposilite.frontend.api.PluginUiRegistrar` removed method `registerPage`
- `net.onelitefeather.ingot.search.SearchIndex` changed return type

## Experimental

- `net.onelitefeather.ingot.search.ExperimentalIngotSearchApi` added method `query`
- `net.onelitefeather.ingot.storage.StorageHook` removed method `open`
- `net.onelitefeather.ingot.storage.StorageHook` removed method `close`
"""


class PluginApiSummaryTest(unittest.TestCase):

    def setUp(self):
        self._directory = tempfile.TemporaryDirectory()
        self.addCleanup(self._directory.cleanup)

    def write_report(self, text):
        path = os.path.join(self._directory.name, "ingot-report.md")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
        return path

    def test_counts_the_promised_findings(self):
        counts = plugin_api_summary.count_groups(REPORT)
        self.assertEqual(2, counts["promised"], "the Promised section lists two findings")

    def test_counts_the_experimental_findings(self):
        counts = plugin_api_summary.count_groups(REPORT)
        self.assertEqual(3, counts["experimental"], "the Experimental section lists three findings")

    def test_section_without_findings_counts_zero(self):
        report = "## Promised\n\n_No findings._\n\n## Experimental\n\n- one finding\n"
        counts = plugin_api_summary.count_groups(report)
        self.assertEqual(0, counts["promised"], "a section that says it has no findings counts zero")
        self.assertEqual(1, counts["experimental"], "the other section is still counted")

    def test_nested_bullets_are_not_counted_as_findings(self):
        report = "## Promised\n\n- a finding\n  - a detail of that finding\n  - another detail\n"
        counts = plugin_api_summary.count_groups(report)
        self.assertEqual(1, counts["promised"], "only top-level bullets are findings")

    def test_report_without_either_section_is_rejected(self):
        with self.assertRaises(ValueError, msg="a report with neither group must not be read as zero findings"):
            plugin_api_summary.count_groups("# Ingot plugin API report\n\nNothing was compared.\n")

    def test_main_prints_a_table_with_both_counts(self):
        path = self.write_report(REPORT)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = plugin_api_summary.main([path])
        self.assertEqual(0, status, "a readable report exits with status 0")
        table = output.getvalue()
        self.assertIn("| Promised | 2 |", table, "the table names the promised count")
        self.assertIn("| Experimental | 3 |", table, "the table names the experimental count")

    def test_main_states_the_experimental_group_does_not_block(self):
        path = self.write_report(REPORT)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            plugin_api_summary.main([path])
        self.assertIn("never block.", output.getvalue(), "the summary tells the reader that experimental findings never block")

    def test_main_rejects_a_report_without_groups_with_status_one(self):
        path = self.write_report("# empty\n")
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
            status = plugin_api_summary.main([path])
        self.assertEqual(1, status, "a report without either group exits with status 1")
        self.assertEqual("", output.getvalue(), "no table is printed for a report it cannot read")


if __name__ == "__main__":
    unittest.main()
