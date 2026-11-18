"""Tests that the Ingot package lists of the plugin API gate match the plugin API documentation.

The build script lists the packages that the Ingot plugin API report compares (ingotPluginApiPackages) and the packages
whose findings are experimental (ingotExperimentalPackages). Every package that the documentation names under
net.onelitefeather.ingot must be in the first list, the dashboard facade package com.reposilite.frontend.api must be in
it too, and com.reposilite.journalist, a third-party library, must be in neither. The files are read as text. No network,
no clock, no shared state. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GITHUB_DIR = os.path.dirname(SCRIPTS_DIR)
REPO_DIR = os.path.dirname(GITHUB_DIR)
BACKEND_BUILD = os.path.join(REPO_DIR, "reposilite-backend", "build.gradle.kts")
PLUGIN_API_DOC = os.path.join(REPO_DIR, "docs", "gitbook", "references", "plugins", "plugin-api.md")

PLUGIN_API_LIST = "ingotPluginApiPackages"
EXPERIMENTAL_LIST = "ingotExperimentalPackages"
FRONTEND_FACADE_PACKAGE = "com.reposilite.frontend.api"
JOURNALIST_PACKAGE = "com.reposilite.journalist"

# A package is a dotted run of lower-case segments. A type name such as RepositoryFormat ends the package name.
INGOT_PACKAGE = re.compile(r"net\.onelitefeather\.ingot(?:\.[a-z][a-z0-9]*)+")


def read_text(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def listed_packages(build_script, list_name):
    """The quoted packages of `val <list_name> = listOf(...)`, or None when the list is not declared."""
    declaration = re.search(
        r"val\s+" + re.escape(list_name) + r"\s*=\s*listOf\((.*?)\n\)",
        build_script,
        re.DOTALL,
    )
    if declaration is None:
        return None
    return re.findall(r'"([^"]+)"', declaration.group(1))


def documented_ingot_packages(doc):
    return sorted(set(INGOT_PACKAGE.findall(doc)))


class IngotPackageListsTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.build_script = read_text(BACKEND_BUILD)
        cls.doc = read_text(PLUGIN_API_DOC)

    def plugin_api_packages(self):
        packages = listed_packages(self.build_script, PLUGIN_API_LIST)
        if packages is None:
            self.fail(f"{PLUGIN_API_LIST} is not declared in reposilite-backend/build.gradle.kts")
        return packages

    def test_documentation_names_at_least_one_ingot_package(self):
        self.assertTrue(
            documented_ingot_packages(self.doc),
            "plugin-api.md names no net.onelitefeather.ingot package, so the check below would pass for nothing",
        )

    def test_every_documented_ingot_package_is_in_the_plugin_api_list(self):
        listed = self.plugin_api_packages()
        missing = [name for name in documented_ingot_packages(self.doc) if name not in listed]
        self.assertEqual(
            missing,
            [],
            f"documented in plugin-api.md but missing from {PLUGIN_API_LIST}: {', '.join(missing)}",
        )

    def test_frontend_facade_package_is_in_the_plugin_api_list(self):
        self.assertIn(
            FRONTEND_FACADE_PACKAGE,
            self.plugin_api_packages(),
            f"{FRONTEND_FACADE_PACKAGE} (the dashboard facade) is missing from {PLUGIN_API_LIST}",
        )

    def test_journalist_is_in_no_ingot_package_list(self):
        for list_name in (PLUGIN_API_LIST, EXPERIMENTAL_LIST):
            packages = listed_packages(self.build_script, list_name) or []
            offending = [name for name in packages if name.startswith(JOURNALIST_PACKAGE)]
            self.assertEqual(
                offending,
                [],
                f"{JOURNALIST_PACKAGE} is a third-party library and must not be in {list_name}",
            )


if __name__ == "__main__":
    unittest.main()
