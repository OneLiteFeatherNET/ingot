"""Consistency checks for the Kubernetes guides: listing in the summary and chart keys in the values blocks.

The test reads repository files only. It needs no network, no clock and no temporary directory.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import os
import re
import textwrap
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(TESTS_DIR, "..", "..", ".."))
GITBOOK_DIR = os.path.join(REPO_DIR, "docs", "gitbook")
SUMMARY = os.path.join(GITBOOK_DIR, "SUMMARY.md")
VALUES = os.path.join(REPO_DIR, "charts", "ingot", "values.yaml")

NEW_GUIDES = [
    "how-to/terminate-tls-at-the-gateway.md",
    "how-to/configure-the-edge-for-large-uploads.md",
    "how-to/manage-secrets-with-gitops.md",
    "how-to/size-the-deployment.md",
    "explanation/deployment-options-compared.md",
]

# Pages that carry a values block of this change: the new guides and the pages they update.
CHECKED_PAGES = NEW_GUIDES + ["how-to/use-s3-storage.md", "how-to/run-on-kubernetes.md"]

SUMMARY_LINK = re.compile(r"\]\(([^)\s]+\.md)\)")
YAML_BLOCK = re.compile(r"^([ \t]*)```yaml[^\n]*\n(.*?)^\1```", re.DOTALL | re.MULTILINE)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def summary_targets():
    return [os.path.normpath(os.path.join(GITBOOK_DIR, target)) for target in SUMMARY_LINK.findall(read(SUMMARY))]


def chart_defaults():
    return yaml.safe_load(read(VALUES))


def values_blocks(page_path):
    """Yields the parsed mappings of the yaml blocks that set a top-level key of values.yaml."""
    defaults = chart_defaults()
    for _indent, block in YAML_BLOCK.findall(read(page_path)):
        try:
            documents = [doc for doc in yaml.safe_load_all(textwrap.dedent(block)) if doc is not None]
        except yaml.YAMLError:
            continue
        for document in documents:
            if isinstance(document, dict) and any(key in defaults for key in document):
                yield document


def unknown_keys(node, default, path=""):
    """Returns the paths of keys in node that the default structure does not define.

    A default that is an empty mapping or any list is free-form, for example annotations or environment entries.
    """
    found = []
    if isinstance(node, dict) and isinstance(default, dict) and default:
        for key, value in node.items():
            here = f"{path}.{key}" if path else str(key)
            if key not in default:
                found.append(here)
            else:
                found.extend(unknown_keys(value, default[key], here))
    return found


class GuideListingTest(unittest.TestCase):

    def test_each_new_guide_is_listed_in_the_summary(self):
        targets = summary_targets()
        for guide in NEW_GUIDES:
            path = os.path.normpath(os.path.join(GITBOOK_DIR, guide))
            with self.subTest(guide=guide):
                self.assertIn(path, targets, f"{guide} is missing from docs/gitbook/SUMMARY.md")

    def test_each_new_guide_is_listed_once_in_the_summary(self):
        targets = summary_targets()
        for guide in NEW_GUIDES:
            path = os.path.normpath(os.path.join(GITBOOK_DIR, guide))
            with self.subTest(guide=guide):
                self.assertEqual(1, targets.count(path), f"{guide} must appear exactly once in docs/gitbook/SUMMARY.md")

    def test_each_summary_link_points_to_an_existing_file(self):
        for target in summary_targets():
            with self.subTest(target=target):
                self.assertTrue(os.path.isfile(target), f"docs/gitbook/SUMMARY.md links to the missing file {target}")


class ValuesBlockTest(unittest.TestCase):

    def test_each_values_block_uses_only_keys_that_values_yaml_defines(self):
        defaults = chart_defaults()
        for page in CHECKED_PAGES:
            path = os.path.join(GITBOOK_DIR, page)
            with self.subTest(page=page):
                self.assertTrue(os.path.isfile(path), f"the page {page} is missing")
                for document in values_blocks(path):
                    problems = unknown_keys(document, defaults)
                    self.assertEqual(
                        [], problems,
                        f"{page} sets chart keys that charts/ingot/values.yaml does not define: {problems}",
                    )


if __name__ == "__main__":
    unittest.main()
