"""Tests for the shape of .github/workflows/codeql.yml and .github/codeql/codeql-config.yml.

The tests read the two files fresh in every test and check what the workflow promises: the triggers, the languages,
the build of the Kotlin backend, the permissions, the SHA pinning and the timeouts. They need no network, no clock and
no GitHub API. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

import yaml

REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
WORKFLOW = os.path.join(REPO_DIR, ".github", "workflows", "codeql.yml")
CONFIG = os.path.join(REPO_DIR, ".github", "codeql", "codeql-config.yml")

SHA_PINNED_USES = re.compile(r"^\s*(?:- )?uses:\s*\S+@[0-9a-f]{40}\s+# v\d+\.\d+\.\d+\s*$")
ANY_USES = re.compile(r"^\s*(?:- )?uses:\s*\S+")


def load_workflow():
    with open(WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def read_workflow_text():
    with open(WORKFLOW, encoding="utf-8") as handle:
        return handle.read()


def load_config():
    with open(CONFIG, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def triggers(workflow):
    # PyYAML reads the bare key `on` as the boolean True (YAML 1.1), so accept both spellings.
    return workflow.get("on", workflow.get(True))


def matrix_entries(workflow):
    return workflow["jobs"]["analyze"]["strategy"]["matrix"]["include"]


def entry_for(workflow, language):
    matches = [entry for entry in matrix_entries(workflow) if entry["language"] == language]
    if len(matches) != 1:
        raise AssertionError(f"expected exactly one matrix entry for {language}, found {len(matches)}")
    return matches[0]


class CodeqlTriggerTests(unittest.TestCase):

    def test_workflow_runs_on_pull_requests_into_main(self):
        self.assertIn("main", triggers(load_workflow())["pull_request"]["branches"])

    def test_workflow_runs_on_pushes_to_main(self):
        self.assertIn("main", triggers(load_workflow())["push"]["branches"])

    def test_workflow_runs_on_a_weekly_schedule(self):
        schedule = triggers(load_workflow())["schedule"]
        self.assertEqual(1, len(schedule), "exactly one schedule entry is expected")
        self.assertRegex(schedule[0]["cron"], r"^\S+ \S+ \* \* 1$", "the cron must fire once a week on Monday")


class CodeqlLanguageTests(unittest.TestCase):

    def test_matrix_contains_java_kotlin(self):
        self.assertEqual("java-kotlin", entry_for(load_workflow(), "java-kotlin")["language"])

    def test_matrix_contains_javascript_typescript(self):
        self.assertEqual("javascript-typescript", entry_for(load_workflow(), "javascript-typescript")["language"])

    def test_java_kotlin_uses_manual_build_mode(self):
        self.assertEqual("manual", entry_for(load_workflow(), "java-kotlin")["build-mode"])

    def test_java_kotlin_is_never_analysed_with_build_mode_none(self):
        for entry in matrix_entries(load_workflow()):
            if entry["language"] == "java-kotlin":
                self.assertNotEqual("none", entry["build-mode"])

    def test_gradle_classes_task_compiles_the_backend_before_analysis(self):
        steps = load_workflow()["jobs"]["analyze"]["steps"]
        runs = [step.get("run", "") for step in steps]
        self.assertIn("./gradlew classes --no-daemon --max-workers=2", runs)

    def test_gradle_classes_task_runs_only_for_java_kotlin(self):
        steps = load_workflow()["jobs"]["analyze"]["steps"]
        gradle = [step for step in steps if step.get("run") == "./gradlew classes --no-daemon --max-workers=2"]
        self.assertEqual(1, len(gradle))
        self.assertIn("java-kotlin", gradle[0]["if"])

    def test_codeql_uses_the_repository_configuration_file(self):
        steps = load_workflow()["jobs"]["analyze"]["steps"]
        init = [step for step in steps if str(step.get("uses", "")).startswith("github/codeql-action/init@")]
        self.assertEqual(1, len(init), "exactly one init step is expected")
        self.assertEqual("./.github/codeql/codeql-config.yml", init[0]["with"]["config-file"])


class CodeqlPermissionTests(unittest.TestCase):

    def test_top_level_permissions_are_contents_read_only(self):
        self.assertEqual({"contents": "read"}, load_workflow()["permissions"])

    def test_only_the_analyze_job_declares_security_events_write(self):
        jobs = load_workflow()["jobs"]
        writers = [name for name, job in jobs.items()
                   if job.get("permissions", {}).get("security-events") == "write"]
        self.assertEqual(["analyze"], writers)

    def test_no_job_declares_packages_id_token_or_attestations_write(self):
        for name, job in load_workflow()["jobs"].items():
            permissions = job.get("permissions", {})
            for scope in ("packages", "id-token", "attestations"):
                self.assertNotEqual("write", permissions.get(scope), f"job {name} must not write {scope}")


class CodeqlPinningTests(unittest.TestCase):

    def test_every_uses_line_is_pinned_to_a_full_sha_with_a_version_comment(self):
        for line in read_workflow_text().splitlines():
            if ANY_USES.match(line):
                self.assertRegex(line, SHA_PINNED_USES, f"unpinned or uncommented action reference: {line.strip()}")

    def test_the_workflow_references_at_least_one_action(self):
        self.assertTrue(any(ANY_USES.match(line) for line in read_workflow_text().splitlines()))


class CodeqlTimeoutTests(unittest.TestCase):

    def test_every_job_declares_timeout_minutes(self):
        for name, job in load_workflow()["jobs"].items():
            self.assertIn("timeout-minutes", job, f"job {name} needs a timeout")


class CodeqlConfigTests(unittest.TestCase):

    def test_config_excludes_node_modules_and_build_output(self):
        ignored = load_config()["paths-ignore"]
        self.assertIn("**/node_modules/**", ignored)
        self.assertIn("**/build/**", ignored)

    def test_config_excludes_the_maven_fixture_tree(self):
        self.assertIn("reposilite-test/workspace/**", load_config()["paths-ignore"])


if __name__ == "__main__":
    unittest.main()
