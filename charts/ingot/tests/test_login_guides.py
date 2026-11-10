"""Consistency checks for the login guides: the summary listing, the settings keys of the recipes, the quoted messages of
the troubleshooting guide and the fixed section order of the provider recipes.

The test reads repository files only. It needs no network, no clock and no temporary directory.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import os
import re
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(TESTS_DIR, "..", "..", ".."))
GITBOOK_DIR = os.path.join(REPO_DIR, "docs", "gitbook")
SUMMARY = os.path.join(GITBOOK_DIR, "SUMMARY.md")
LOGIN_SOURCES = os.path.join(REPO_DIR, "reposilite-backend", "src", "main", "kotlin", "net", "onelitefeather", "ingot", "identity", "login")
OIDC_SETTINGS = os.path.join(LOGIN_SOURCES, "oidc", "OidcSettings.kt")
TROUBLESHOOTING = "how-to/troubleshoot-external-login.md"

NEW_PAGES = [
    "how-to/configure-keycloak-login.md",
    "how-to/configure-authentik-login.md",
    "how-to/configure-okta-login.md",
    "how-to/configure-google-workspace-login.md",
    "how-to/map-provider-groups-to-teams.md",
    TROUBLESHOOTING,
]

# The provider recipes: the four new ones plus the two existing pages brought to the same shape.
RECIPES = [
    "how-to/configure-keycloak-login.md",
    "how-to/configure-authentik-login.md",
    "how-to/configure-okta-login.md",
    "how-to/configure-google-workspace-login.md",
    "how-to/configure-entra-id-login.md",
    "how-to/configure-github-login.md",
]

SECTION_ORDER = [
    "## Before you begin",
    "## At the provider",
    "## In Ingot",
    "## Claims",
    "## Groups",
    "## Check the result",
    "## Troubleshooting",
]

SUMMARY_LINK = re.compile(r"\]\(([^)\s]+\.md)\)")
BACKTICKED = re.compile(r"`([^`]+)`")
TABLE_ROW = re.compile(r"^\|(.*)\|\s*$")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def page(relative):
    return os.path.join(GITBOOK_DIR, relative)


def summary_targets():
    return SUMMARY_LINK.findall(read(SUMMARY))


def kotlin_text(directory):
    """All Kotlin sources below [directory], concatenated. Messages are searched as plain substrings of this text."""
    chunks = []
    for root, _dirs, files in os.walk(directory):
        for name in sorted(files):
            if name.endswith(".kt"):
                chunks.append(read(os.path.join(root, name)))
    return "\n".join(chunks)


def properties_of(source_path, class_name):
    """The names of the constructor properties of [class_name] in [source_path]."""
    text = read(source_path)
    start = text.index("data class " + class_name + "(")
    end = text.index("\n)", start)
    return set(re.findall(r"\bval (\w+)\s*:", text[start:end]))


def table_rows(markdown, header_first_cell):
    """The rows (as lists of cell texts) of the tables whose first header cell is [header_first_cell]."""
    rows = []
    in_table = False
    for line in markdown.splitlines():
        match = TABLE_ROW.match(line)
        if not match:
            in_table = False
            continue
        cells = [cell.strip() for cell in match.group(1).split("|")]
        if not in_table:
            in_table = cells[0] == header_first_cell
            continue
        if all(set(cell) <= set("-: ") for cell in cells):
            continue
        rows.append(cells)
    return rows


def settings_keys(relative):
    """The JSON keys of the settings tables of a recipe: the first cell of rows under a 'JSON key' header."""
    keys = []
    for cells in table_rows(read(page(relative)), "JSON key"):
        keys.extend(BACKTICKED.findall(cells[0]))
    return keys


def message_column_quotes(relative):
    """The backticked texts in the 'Message' column of the troubleshooting guide."""
    quotes = []
    for cells in table_rows(read(page(relative)), "Message"):
        quotes.extend(BACKTICKED.findall(cells[0]))
    return quotes


class SummaryListsNewPages(unittest.TestCase):

    def test_each_new_page_is_listed_once_in_the_summary(self):
        targets = summary_targets()
        for relative in NEW_PAGES:
            with self.subTest(page=relative):
                count = targets.count(relative)
                self.assertEqual(1, count, f"{relative} must be listed once in docs/gitbook/SUMMARY.md, found {count}")


class SettingsKeysExist(unittest.TestCase):

    def test_every_recipe_settings_key_is_a_provider_property(self):
        known = properties_of(OIDC_SETTINGS, "OidcProviderSettings")
        for relative in RECIPES:
            for key in settings_keys(relative):
                with self.subTest(page=relative, key=key):
                    self.assertIn(key, known, f"{relative} documents the key {key}, which is not a property of OidcProviderSettings")


class TroubleshootingMessagesExist(unittest.TestCase):

    def test_every_quoted_message_occurs_in_the_login_sources(self):
        sources = kotlin_text(LOGIN_SOURCES)
        quotes = message_column_quotes(TROUBLESHOOTING)
        self.assertTrue(quotes, f"{TROUBLESHOOTING} has no backticked messages in its Message column")
        for quote in quotes:
            with self.subTest(message=quote):
                self.assertIn(quote, sources, f"{TROUBLESHOOTING} quotes the message '{quote}', which no login source contains")


class RecipesHaveTheFixedShape(unittest.TestCase):

    def test_every_recipe_has_the_redirect_uri_form(self):
        for relative in RECIPES:
            with self.subTest(page=relative):
                self.assertIn("/api/auth/oidc/", read(page(relative)), f"{relative} does not show the redirect URI form /api/auth/oidc/<id>/callback")

    def test_every_recipe_has_the_sections_in_the_fixed_order(self):
        for relative in RECIPES:
            with self.subTest(page=relative):
                headings = [line.strip() for line in read(page(relative)).splitlines() if line.startswith("## ")]
                positions = []
                for section in SECTION_ORDER:
                    self.assertIn(section, headings, f"{relative} lacks the section '{section}'")
                    positions.append(headings.index(section))
                self.assertEqual(sorted(positions), positions, f"{relative} has its sections out of the fixed order {SECTION_ORDER}")


if __name__ == "__main__":
    unittest.main()
