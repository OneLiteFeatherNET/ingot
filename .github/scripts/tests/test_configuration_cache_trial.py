"""Tests that ci.yml trials the Gradle configuration cache on the backend unit tests and nowhere else.

The flag is passed per command, so a local build, the coverage run and the defaults stay as they are. The trial is kept only
after a green run that is faster; until then the unit test commands carry the flag and no property switches it on for everyone.
Each test names one behaviour and reads files as text. No network, no clock.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GITHUB_DIR = os.path.dirname(SCRIPTS_DIR)
REPO_DIR = os.path.dirname(GITHUB_DIR)
CI_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "ci.yml")
GRADLE_PROPERTIES = os.path.join(REPO_DIR, "gradle.properties")

# The backend unit test task, but not testCoverage, which the coverage step runs on main only.
UNIT_TEST_TASK = re.compile(r":reposilite-backend:test(?![A-Za-z])")


def gradle_commands(text):
    """Every ./gradlew invocation with the continuation lines that belong to it."""
    commands = []
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if "./gradlew " not in line or line.lstrip().startswith("#"):
            continue
        command = line
        while command.rstrip().endswith("\\") and index + 1 < len(lines):
            index += 1
            command += "\n" + lines[index]
        commands.append(command)
    return commands


class ConfigurationCacheTrialTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()
        with open(GRADLE_PROPERTIES, encoding="utf-8") as handle:
            cls.properties = handle.read()

    def test_every_backend_unit_test_command_reads_the_configuration_cache(self):
        unit_commands = [command.strip() for command in gradle_commands(self.workflow) if UNIT_TEST_TASK.search(command)]
        self.assertTrue(unit_commands, "ci.yml runs no backend unit test command")
        missing = [command for command in unit_commands if "--configuration-cache" not in command]
        self.assertEqual(missing, [], "backend unit test commands without --configuration-cache")

    def test_unit_test_commands_keep_the_build_cache_flag(self):
        for command in gradle_commands(self.workflow):
            if UNIT_TEST_TASK.search(command):
                self.assertIn("--build-cache", command)

    def test_coverage_run_does_not_trial_the_configuration_cache(self):
        coverage = [command for command in gradle_commands(self.workflow) if ":reposilite-backend:testCoverage" in command]
        self.assertTrue(coverage, "ci.yml runs no coverage command")
        for command in coverage:
            self.assertNotIn("--configuration-cache", command)

    def test_no_property_switches_the_configuration_cache_on_for_every_build(self):
        self.assertNotIn("org.gradle.configuration-cache", self.properties)
        self.assertNotIn("org.gradle.configuration-cache", self.workflow)


if __name__ == "__main__":
    unittest.main()
