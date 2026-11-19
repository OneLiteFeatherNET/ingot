"""Tests that the CI Gradle jobs can route Maven Central through the project mirror, and only there.

The mirror is opt-in for CI: the repository variable INGOT_CI_MAVEN_MIRROR feeds the workflow environment, and the
init script under .github/gradle/ only acts when that variable is set. Each test names one behaviour and reads the
files as text. No network, no clock.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CI_WORKFLOW = os.path.join(REPO_DIR, "workflows", "ci.yml")
INIT_SCRIPT = os.path.join(REPO_DIR, "gradle", "ci-maven-mirror.init.gradle")
VARIABLE = "INGOT_CI_MAVEN_MIRROR"


def job_blocks(text):
    """Maps each job id to the text of its block."""
    blocks = {}
    matches = list(re.finditer(r"^  ([a-z][a-z-]*):\n", text, re.M))
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        blocks[match.group(1)] = text[match.start():end]
    return blocks


def gradle_setup_jobs(blocks):
    return {job for job, block in blocks.items() if '- name: "Set up Gradle"' in block}


class CiMavenMirrorTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()
        cls.blocks = job_blocks(cls.workflow)

    def test_init_script_exists_next_to_the_workflows(self):
        self.assertTrue(os.path.isfile(INIT_SCRIPT), "the CI mirror init script is missing")

    def test_init_script_reads_the_mirror_from_the_environment_only(self):
        with open(INIT_SCRIPT, encoding="utf-8") as handle:
            script = handle.read()
        self.assertIn(f"getenv('{VARIABLE}')", script)

    def test_init_script_does_not_touch_publishing(self):
        with open(INIT_SCRIPT, encoding="utf-8") as handle:
            script = handle.read()
        self.assertIn("contains('publish')", script, "publishing must keep the Maven Central URL")

    def test_workflow_passes_the_repository_variable_to_every_job(self):
        self.assertRegex(
            self.workflow,
            rf"(?m)^env:\n  {VARIABLE}: \$\{{\{{ vars\.{VARIABLE} \}}\}}\n",
        )

    def test_every_job_that_sets_up_gradle_installs_the_init_script_after_it(self):
        jobs = gradle_setup_jobs(self.blocks)
        self.assertTrue(jobs, "no job sets up Gradle")
        for job in sorted(jobs):
            block = self.blocks[job]
            setup = block.index('- name: "Set up Gradle"')
            install = block.find('- name: "Route Maven Central through the CI mirror"')
            self.assertNotEqual(install, -1, f"{job} has no mirror step")
            self.assertGreater(install, setup, f"{job} installs the mirror before Gradle is set up")
            self.assertIn("cp .github/gradle/ci-maven-mirror.init.gradle", block[install:], job)

    def test_mirror_step_only_runs_when_the_variable_is_set(self):
        for job in sorted(gradle_setup_jobs(self.blocks)):
            block = self.blocks[job]
            install = block.index('- name: "Route Maven Central through the CI mirror"')
            step = block[install:].split("\n      - name:", 1)[0]
            self.assertIn(f"if: ${{{{ vars.{VARIABLE} != '' }}}}", step, job)

    def test_no_ci_job_publishes_artifacts(self):
        # The init script leaves publishing alone only when no publish task runs. The light jobs never publish to a
        # remote repository. The Publications job only publishes into a throwaway local repository.
        for line in re.findall(r"\./gradlew[^\n]*publish[^\n]*", self.workflow):
            tasks = re.findall(r"\S*publish\S*", line, re.I)
            self.assertTrue(all(re.search(r"(^|:)publishToMavenLocal$", task) for task in tasks), line)
            self.assertIn('-Dmaven.repo.local="$RUNNER_TEMP/', line)

    def test_publications_job_publishes_every_project_into_the_runner_temp_directory(self):
        # The release publishes every project, so the local check must cover the same set instead of one module.
        publish = [line for line in re.findall(r"\./gradlew[^\n]*publish[^\n]*", self.workflow)]
        self.assertEqual(len(publish), 1, "the Publications job runs one publish command")
        self.assertRegex(publish[0], r"\./gradlew --build-cache publishToMavenLocal -Dmaven\.repo\.local=\"\$RUNNER_TEMP/m2\"$")


if __name__ == "__main__":
    unittest.main()
