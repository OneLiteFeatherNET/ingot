"""Tests that ci.yml reads the Gradle build cache in its light and coverage jobs and never caches integration results.

The cache is switched on per command with --build-cache, so a local build and the drop-in contract are untouched. The
integration task starts containers that Gradle cannot see, so its result must never be replayed from the cache. Each
test names one behaviour and reads files as text. No network, no clock.

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
BACKEND_BUILD = os.path.join(REPO_DIR, "reposilite-backend", "build.gradle.kts")

# Commands that do not take the flag: the end-to-end job and the container tests are heavy and out of scope here.
EXEMPT = (":reposilite-backend:shadowJar", ":reposilite-backend:containerTest")


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


class BuildCacheInWorkflowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()

    def test_every_light_and_coverage_gradle_command_reads_the_build_cache(self):
        missing = [
            command.strip()
            for command in gradle_commands(self.workflow)
            if not any(exempt in command for exempt in EXEMPT) and "--build-cache" not in command
        ]
        self.assertEqual(missing, [], "gradle commands without --build-cache")

    def test_heavy_gradle_commands_are_left_alone(self):
        for command in gradle_commands(self.workflow):
            if any(exempt in command for exempt in EXEMPT):
                self.assertNotIn("--build-cache", command)

    def test_no_job_switches_the_cache_on_for_the_whole_build_by_property(self):
        self.assertNotIn("org.gradle.caching", self.workflow)

    def test_no_job_writes_the_gradle_cache_from_a_pull_request(self):
        # setup-gradle writes from the default branch only unless a job overrides it. Nothing may override it.
        self.assertNotRegex(self.workflow, r"cache-read-only:\s*false")
        self.assertNotIn("cache-write-only", self.workflow)


class IntegrationResultsAreNotCachedTest(unittest.TestCase):

    def test_integration_task_is_not_cacheable(self):
        with open(BACKEND_BUILD, encoding="utf-8") as handle:
            text = handle.read()
        block = re.search(r'tasks\.named<Test>\("integration"\) \{\n(.*?)\n\}\n', text, re.S)
        self.assertIsNotNone(block, "backend build has no integration task block")
        self.assertIn("outputs.cacheIf { false }", block.group(1))


if __name__ == "__main__":
    unittest.main()
