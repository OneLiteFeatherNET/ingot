"""Shape tests for renovate.json.

The server Dockerfile and the CLI Dockerfile share their eclipse-temurin bases, pinned by tag and digest. A Renovate
group keeps one pull request raising the digest for both. Each test reads the file fresh.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import json
import os
import re
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(TESTS_DIR)))
DOCKERFILES = ("Dockerfile", os.path.join("ingot-cli", "Dockerfile"))
PINNED_FROM = re.compile(r"^FROM\s+(?:--platform=\S+\s+)?eclipse-temurin:\S+@sha256:[0-9a-f]{64}\b", re.MULTILINE)


def package_rules():
    with open(os.path.join(REPO_DIR, "renovate.json"), encoding="utf-8") as handle:
        return json.load(handle)["packageRules"]


class RenovateConfigTest(unittest.TestCase):
    def test_temurin_bases_are_grouped_for_both_dockerfiles(self):
        rules = [r for r in package_rules()
                 if "eclipse-temurin" in r.get("matchPackageNames", []) and "dockerfile" in r.get("matchManagers", [])]
        self.assertEqual(1, len(rules), "expected one dockerfile rule for eclipse-temurin")
        self.assertTrue(rules[0].get("groupName"), "the eclipse-temurin rule must group its updates")

    def test_both_dockerfiles_pin_temurin_by_tag_and_digest(self):
        for name in DOCKERFILES:
            with open(os.path.join(REPO_DIR, name), encoding="utf-8") as handle:
                found = PINNED_FROM.findall(handle.read())
            self.assertEqual(2, len(found), f"{name} must pin both eclipse-temurin bases by tag and digest")


if __name__ == "__main__":
    unittest.main()
