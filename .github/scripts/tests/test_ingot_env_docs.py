"""Tests that every INGOT_ variable of the command line client is documented, and that the links of its pages resolve.

The names are read from the Kotlin sources of ingot-cli/src/main. A name counts as documented when it appears in the
command line reference (docs/gitbook/references/cli.md) or in the environment variable reference
(docs/gitbook/references/configuration/environment-variables.md). The links of the pages that describe the client are
resolved against the files on disk. Each test builds its own input in memory or in a temporary directory. No network,
no clock, no shared state.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import tempfile
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GITHUB_DIR = os.path.dirname(SCRIPTS_DIR)
REPO_DIR = os.path.dirname(GITHUB_DIR)
CLI_SOURCE_DIR = os.path.join(REPO_DIR, "ingot-cli", "src", "main")
CLI_REFERENCE = os.path.join(REPO_DIR, "docs", "gitbook", "references", "cli.md")
ENVIRONMENT_REFERENCE = os.path.join(REPO_DIR, "docs", "gitbook", "references", "configuration", "environment-variables.md")
CLI_PAGES = [
    CLI_REFERENCE,
    ENVIRONMENT_REFERENCE,
    os.path.join(REPO_DIR, "docs", "gitbook", "manage", "operate", "run-the-cli.md"),
]

# A variable name is INGOT_ followed by upper-case words. A bare prefix such as INGOT_ names no variable.
VARIABLE = re.compile(r"INGOT_[A-Z0-9]+(?:_[A-Z0-9]+)*")
# An inline link target without its anchor. Images and reference-style links are not used on these pages.
LINK = re.compile(r"\]\(([^)\s]+)\)")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def ingot_names_in_source(texts):
    """Returns the sorted variable names that the given source texts mention."""
    names = set()
    for text in texts:
        names.update(VARIABLE.findall(text))
    return sorted(names)


def cli_source_texts():
    texts = []
    for directory, _subdirectories, files in os.walk(CLI_SOURCE_DIR):
        for name in files:
            if name.endswith(".kt"):
                texts.append(read(os.path.join(directory, name)))
    return texts


def undocumented_names(names, documented_texts):
    """Returns the names that none of the documented texts mentions, in the order given."""
    documented = set()
    for text in documented_texts:
        documented.update(VARIABLE.findall(text))
    return [name for name in names if name not in documented]


def broken_relative_links(page_path):
    """Returns the relative link targets of a page whose file does not exist. The anchor of a link is not checked."""
    page_dir = os.path.dirname(page_path)
    broken = []
    for target in LINK.findall(read(page_path)):
        if target.startswith("#") or "://" in target or target.startswith("mailto:"):
            continue
        file_part = target.split("#", 1)[0]
        if not os.path.isfile(os.path.normpath(os.path.join(page_dir, file_part))):
            broken.append(target)
    return broken


class CliVariableDocumentationTest(unittest.TestCase):

    def test_a_variable_that_no_reference_documents_is_reported(self):
        names = ingot_names_in_source(['val token = env["INGOT_NEW_SWITCH"]'])
        problems = undocumented_names(names, ["| `INGOT_URL` | The server URL. |"])
        self.assertEqual(["INGOT_NEW_SWITCH"], problems, "INGOT_NEW_SWITCH is read but no reference names it")

    def test_a_variable_named_in_the_command_line_reference_is_not_reported(self):
        names = ingot_names_in_source(['env["INGOT_TIMEOUT"]'])
        problems = undocumented_names(names, ["| `INGOT_TIMEOUT` | Seconds to wait for the server. |"])
        self.assertEqual([], problems, "a variable documented in the command line reference is reported")

    def test_a_variable_named_in_the_environment_reference_is_not_reported(self):
        names = ingot_names_in_source(['env["INGOT_CONFIG"]'])
        problems = undocumented_names(names, ["| `INGOT_CONFIG` | The config file. |"])
        self.assertEqual([], problems, "a variable documented in the environment reference is reported")

    def test_a_bare_prefix_names_no_variable(self):
        self.assertEqual([], ingot_names_in_source(['println("Variables: INGOT_")']), "the bare prefix is read as a name")

    def test_each_variable_in_the_cli_source_is_documented(self):
        names = ingot_names_in_source(cli_source_texts())
        self.assertTrue(names, "no INGOT_ variable was found in ingot-cli/src/main, the scan is broken")
        documented = [read(CLI_REFERENCE), read(ENVIRONMENT_REFERENCE)]
        self.assertEqual(
            [], undocumented_names(names, documented),
            "these INGOT_ variables of the command line client are not documented in cli.md or environment-variables.md",
        )


class CliPageLinkTest(unittest.TestCase):

    def test_a_relative_link_to_a_missing_file_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            page = os.path.join(directory, "page.md")
            with open(page, "w", encoding="utf-8") as handle:
                handle.write("See [the reference](missing.md#exit-codes).\n")
            self.assertEqual(["missing.md#exit-codes"], broken_relative_links(page))

    def test_a_relative_link_to_an_existing_file_is_not_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            with open(os.path.join(directory, "target.md"), "w", encoding="utf-8") as handle:
                handle.write("# Target\n")
            page = os.path.join(directory, "page.md")
            with open(page, "w", encoding="utf-8") as handle:
                handle.write("See [the target](target.md#target).\n")
            self.assertEqual([], broken_relative_links(page))

    def test_a_link_to_an_anchor_of_the_same_page_is_not_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            page = os.path.join(directory, "page.md")
            with open(page, "w", encoding="utf-8") as handle:
                handle.write("See [below](#exit-codes).\n")
            self.assertEqual([], broken_relative_links(page))

    def test_an_external_link_is_not_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            page = os.path.join(directory, "page.md")
            with open(page, "w", encoding="utf-8") as handle:
                handle.write("See [the site](https://example.org/missing.md).\n")
            self.assertEqual([], broken_relative_links(page))

    def test_each_relative_link_of_the_cli_pages_resolves(self):
        for page in CLI_PAGES:
            with self.subTest(page=os.path.relpath(page, REPO_DIR)):
                self.assertTrue(os.path.isfile(page), f"the page {page} is missing")
                self.assertEqual([], broken_relative_links(page), "a link of this page points to a missing file")


if __name__ == "__main__":
    unittest.main()
