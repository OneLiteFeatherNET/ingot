"""Tests for .github/scripts/select_backend_tests.py.

The script maps changed backend paths to the test packages they can reach and prints a decision line. Every test
builds its own Kotlin tree in a temporary directory, so there is no network, no clock, no shared state and no
dependency on the real repository. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT = os.path.join(SCRIPTS_DIR, "select_backend_tests.py")

_spec = importlib.util.spec_from_file_location("select_backend_tests", SCRIPT)
select_backend_tests = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(select_backend_tests)

SMOKE_FILES = {
    "DependencyRulesTest": ("test", "net.onelitefeather.ingot.architecture"),
    "WritePathEventCharacterisationTest": ("test", "com.reposilite.maven"),
    "OpenApiCompletenessTest": ("test", "com.reposilite.web.api"),
    "OpenApiAccessIntegrationTest": ("integration", "com.reposilite.web"),
    "OpenApiSecuritySchemesIntegrationTest": ("integration", "com.reposilite.web"),
    "SecurityHeadersSettingsTest": ("test", "net.onelitefeather.ingot.web"),
    "DashboardPolicyTest": ("test", "net.onelitefeather.ingot.web"),
    "StartupParametersIntegrationTest": ("integration", "com.reposilite.configuration"),
    "StartupRefusalTest": ("test", "net.onelitefeather.ingot.deploy"),
    "StartupRulesTest": ("test", "net.onelitefeather.ingot.search.rebuild"),
}


def write(root, rel_path, text):
    path = os.path.join(root, rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def kotlin(root, source_set, package, name, tests=0, imports=()):
    """Writes one Kotlin class of the backend with the given number of @Test annotations and imports."""
    rel_path = f"reposilite-backend/src/{source_set}/kotlin/{package.replace('.', '/')}/{name}.kt"
    lines = [f"package {package}", ""]
    lines += [f"import {name_}" for name_ in imports]
    lines += ["", f"class {name} {{"]
    lines += [f"    @Test fun case{index}() {{}}" for index in range(tests)]
    lines += ["}", ""]
    write(root, rel_path, "\n".join(lines))


def make_backend(root, unrelated_tests=10):
    """A tree with the ten smoke classes, which the script requires, and a package that no change in these tests reaches.

    The unrelated package keeps the selection below the threshold in the tests that do not test the threshold.
    The smoke classes carry no annotation, so they do not count towards the selection.
    """
    for name, (source_set, package) in SMOKE_FILES.items():
        kotlin(root, source_set, package, name)
    if unrelated_tests:
        kotlin(root, "test", "com.reposilite.scheduling", "SchedulerTest", tests=unrelated_tests)


def decide(root, changed):
    return select_backend_tests.decide(root, changed)


MAIN_LEDGER = "reposilite-backend/src/main/kotlin/com/reposilite/audit/Ledger.kt"


class ChangedFileMappingTest(unittest.TestCase):
    def test_changed_file_selects_its_own_package(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)

            decision = decide(root, [MAIN_LEDGER])

            self.assertEqual("partial", decision.mode, decision.reason)
            self.assertIn("com.reposilite.audit", decision.selected, "the changed file's own package must be selected")
            self.assertIn("com.reposilite.audit.*", decision.test_filters, "its unit tests must be in the test filters")


class ImporterSelectionTest(unittest.TestCase):
    def build_chain(self, root):
        """audit is imported by statistics, statistics is imported by cleanup. cleanup imports nothing else."""
        make_backend(root)
        kotlin(root, "main", "com.reposilite.audit", "Ledger")
        kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)
        kotlin(root, "main", "com.reposilite.statistics", "Counter", imports=["com.reposilite.audit.Ledger"])
        kotlin(root, "test", "com.reposilite.statistics", "CounterTest", tests=1)
        kotlin(root, "main", "com.reposilite.cleanup", "Sweeper", imports=["com.reposilite.statistics.Counter"])
        kotlin(root, "test", "com.reposilite.cleanup", "SweeperTest", tests=1)

    def test_direct_importer_is_selected(self):
        with tempfile.TemporaryDirectory() as root:
            self.build_chain(root)

            decision = decide(root, [MAIN_LEDGER])

            self.assertEqual("partial", decision.mode, decision.reason)
            self.assertIn("com.reposilite.statistics", decision.selected, "statistics imports audit directly")

    def test_second_order_importer_is_not_selected(self):
        with tempfile.TemporaryDirectory() as root:
            self.build_chain(root)

            decision = decide(root, [MAIN_LEDGER])

            self.assertNotIn("com.reposilite.cleanup", decision.selected, "cleanup reaches audit only through statistics")
            self.assertNotIn("com.reposilite.cleanup.*", decision.test_filters)


class FullRunTriggerTest(unittest.TestCase):
    TRIGGERS = [
        "build.gradle.kts",
        "gradle/libs.versions.toml",
        ".github/workflows/ci.yml",
        "reposilite-frontend/src/App.vue",
        "reposilite-plugins/checksum-plugin/src/main/kotlin/com/reposilite/plugin/Checksum.kt",
        "reposilite-backend/src/main/resources/banner.txt",
        "reposilite-backend/src/testFixtures/kotlin/com/reposilite/maven/Fixture.kt",
        "reposilite-backend/src/containerTest/kotlin/com/reposilite/image/ImageUnderTest.kt",
        "new-top-level/src/Unknown.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/configuration/Settings.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/web/Routes.kt",
        "reposilite-backend/src/test/kotlin/com/reposilite/SharedSpecification.kt",
        "reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/web/Headers.kt",
        "reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/schema/Lock.kt",
    ]

    def test_each_full_run_trigger_yields_full_mode(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)
            for path in self.TRIGGERS:
                with self.subTest(path=path):
                    decision = decide(root, [path])
                    self.assertEqual("full", decision.mode, f"{path} must run the full suite: {decision.reason}")

    def test_full_run_trigger_beats_a_narrow_selection(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)

            decision = decide(root, [MAIN_LEDGER, "build.gradle.kts"])

            self.assertEqual("full", decision.mode, "one trigger in the change must make the whole run full")

    def test_path_outside_kotlin_roots_yields_full(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)

            decision = decide(root, ["reposilite-backend/src/main/resources/logback.xml"])

            self.assertEqual("full", decision.mode, decision.reason)
            self.assertIn("logback.xml", decision.reason, "the reason must name the path that forced the full run")


class SelectionThresholdTest(unittest.TestCase):
    def build(self, root, audit_tests, other_tests):
        make_backend(root, unrelated_tests=0)
        kotlin(root, "main", "com.reposilite.audit", "Ledger")
        kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=audit_tests)
        kotlin(root, "test", "com.reposilite.cleanup", "SweeperTest", tests=other_tests)

    def test_selection_above_sixty_percent_yields_full(self):
        with tempfile.TemporaryDirectory() as root:
            self.build(root, audit_tests=61, other_tests=39)

            decision = decide(root, [MAIN_LEDGER])

            self.assertEqual("full", decision.mode, "61 of 100 test annotations is above the threshold")

    def test_selection_at_sixty_percent_stays_partial(self):
        with tempfile.TemporaryDirectory() as root:
            self.build(root, audit_tests=60, other_tests=40)

            decision = decide(root, [MAIN_LEDGER])

            self.assertEqual("partial", decision.mode, "60 of 100 is not above the threshold: " + decision.reason)


class SmokeSetTest(unittest.TestCase):
    def test_missing_smoke_class_fails_the_script(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            os.remove(os.path.join(root, "reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/architecture/DependencyRulesTest.kt"))

            with self.assertRaises(select_backend_tests.SelectionError) as raised:
                decide(root, [MAIN_LEDGER])

            self.assertIn("DependencyRulesTest", str(raised.exception), "the error must name the missing smoke class")

    def test_partial_run_always_includes_the_smoke_set(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)

            decision = decide(root, [MAIN_LEDGER])

            self.assertEqual("partial", decision.mode, decision.reason)
            self.assertIn("net.onelitefeather.ingot.architecture.DependencyRulesTest", decision.test_filters)
            self.assertIn("com.reposilite.maven.WritePathEventCharacterisationTest", decision.test_filters)
            self.assertIn("com.reposilite.web.OpenApiAccessIntegrationTest", decision.integration_filters)
            self.assertIn("com.reposilite.configuration.StartupParametersIntegrationTest", decision.integration_filters)


class FilterShapeTest(unittest.TestCase):
    def test_filters_name_only_the_source_set_that_holds_the_tests(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)
            kotlin(root, "integration", "com.reposilite.audit", "LedgerIntegrationTest", tests=1)
            kotlin(root, "main", "com.reposilite.statistics", "Counter", imports=["com.reposilite.audit.Ledger"])
            kotlin(root, "integration", "com.reposilite.statistics", "CounterIntegrationTest", tests=1)

            decision = decide(root, [MAIN_LEDGER])

            self.assertEqual("partial", decision.mode, decision.reason)
            self.assertIn("com.reposilite.audit.*", decision.test_filters)
            self.assertIn("com.reposilite.audit.*", decision.integration_filters)
            self.assertIn("com.reposilite.statistics.*", decision.integration_filters)
            self.assertNotIn("com.reposilite.statistics.*", decision.test_filters, "statistics has no unit tests")


class DenyListTest(unittest.TestCase):
    def test_documentation_and_openspec_paths_are_dropped(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "com.reposilite.audit", "Ledger")
            kotlin(root, "test", "com.reposilite.audit", "LedgerTest", tests=2)

            decision = decide(root, ["docs/guide.md", "openspec/changes/x/tasks.md", MAIN_LEDGER])

            self.assertEqual("partial", decision.mode, "documentation paths must not force a full run: " + decision.reason)
            self.assertEqual(["com.reposilite.audit"], decision.selected)


class UnmappedPackageTest(unittest.TestCase):
    def test_changed_main_file_without_any_test_in_reach_yields_full(self):
        with tempfile.TemporaryDirectory() as root:
            make_backend(root)
            kotlin(root, "main", "net.onelitefeather.ingot.redirect", "Redirect")

            decision = decide(root, ["reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/redirect/Redirect.kt"])

            self.assertEqual("full", decision.mode, "a package with no test in reach is treated as unmapped")


class CommandLineTest(unittest.TestCase):
    def test_script_failure_prints_full_mode_and_exits_zero(self):
        with tempfile.TemporaryDirectory() as root:
            kotlin(root, "main", "com.reposilite.audit", "Ledger")

            result = subprocess.run(
                [sys.executable, SCRIPT, "--root", root],
                input=MAIN_LEDGER + "\n",
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("mode=full", result.stdout.splitlines(), "a failing script must make the job run the full suite")


if __name__ == "__main__":
    unittest.main()
