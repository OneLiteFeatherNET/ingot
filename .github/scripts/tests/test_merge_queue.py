"""Tests that the required checks of the ruleset report in GitHub's merge queue.

The merge queue starts the workflows with the merge_group event. A workflow without that trigger never reports, and a
queue entry waiting for a required check that never comes stalls the queue. A merge_group event has no pull request,
no labels and no path filter, so every scope step diffs the base and the head SHA of the merge group instead, and
nothing that writes to a pull request may run there.

The workflow tests read the YAML. The behaviour tests run the scope step of ci.yml and build_scope.sh in a temporary
git repository of their own. No network, no clock. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import subprocess
import tempfile
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
REPO_DIR = os.path.dirname(os.path.dirname(SCRIPTS_DIR))
WORKFLOWS_DIR = os.path.join(REPO_DIR, ".github", "workflows")
BUILD_SCOPE = os.path.join(SCRIPTS_DIR, "build_scope.sh")

# The required status checks of the ruleset "Protect main" and the workflow that reports each of them.
REQUIRED_CHECKS = {
    "Build / Aggregate test results": "merge-queue.yml",
    "Frontend": "ci.yml",
    "Coverage": "ci.yml",
    "Container images": "heavy.yml",
    "Licenses": "ci.yml",
    "Vulnerabilities": "ci.yml",
    "Helm chart": "heavy.yml",
}

MERGE_GROUP_BASE = "github.event.merge_group.base_sha"
MERGE_GROUP_HEAD = "github.event.merge_group.head_sha"


def load(name):
    with open(os.path.join(WORKFLOWS_DIR, name), encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def triggers(workflow):
    # PyYAML reads the key `on` as the boolean True.
    return workflow.get("on", workflow.get(True))


def step(job, name):
    for entry in job["steps"]:
        if entry.get("name") == name:
            return entry
    raise AssertionError(f"no step named {name!r}")


def git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def commit(cwd, path, message):
    full = os.path.join(cwd, path)
    os.makedirs(os.path.dirname(full) or cwd, exist_ok=True)
    with open(full, "a", encoding="utf-8") as handle:
        handle.write(message + "\n")
    git(cwd, "add", "--all")
    git(cwd, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "-c", "commit.gpgsign=false",
        "commit", "-q", "-m", message)
    return git(cwd, "rev-parse", "HEAD")


class RequiredChecksTest(unittest.TestCase):
    def test_every_required_check_comes_from_a_workflow_with_the_merge_group_trigger(self):
        for check, name in REQUIRED_CHECKS.items():
            with self.subTest(check=check):
                workflow = load(name)
                self.assertEqual({"types": ["checks_requested"]}, triggers(workflow).get("merge_group"))
                names = [job.get("name") for job in workflow["jobs"].values()]
                self.assertIn(check, names)

    def test_no_merge_group_workflow_has_a_path_filter(self):
        # A workflow skipped by a filter leaves its required check pending, which stalls the queue.
        for name in set(REQUIRED_CHECKS.values()):
            with self.subTest(workflow=name):
                event = triggers(load(name))["merge_group"]
                self.assertNotIn("paths", event)
                self.assertNotIn("paths-ignore", event)

    def test_every_merge_group_workflow_keeps_queue_entries_apart(self):
        # A merge_group run has no pull request number. Its ref is unique per queue entry, so the group falls back to
        # it, and only pull request runs are cancelled.
        for name in set(REQUIRED_CHECKS.values()):
            with self.subTest(workflow=name):
                concurrency = load(name)["concurrency"]
                self.assertIn("github.ref", concurrency["group"])
                self.assertNotIn("github.head_ref", concurrency["group"])
                self.assertNotIn("merge_group", str(concurrency.get("cancel-in-progress", False)))
                self.assertIsNot(True, concurrency.get("cancel-in-progress", False))


class MergeQueueBuildTest(unittest.TestCase):
    """The shared Gradle workflow cannot run on merge_group, so merge-queue.yml reports its check name there."""

    def workflow(self):
        return load("merge-queue.yml")

    def build(self):
        return self.workflow()["jobs"]["build"]

    def test_the_workflow_starts_from_the_merge_group_only(self):
        # On a pull request the shared workflow reports the same check name, and two checks of one name would race.
        self.assertEqual({"merge_group"}, set(triggers(self.workflow())))

    def test_the_job_reports_the_check_name_of_the_shared_workflow(self):
        self.assertEqual("Build / Aggregate test results", self.build()["name"])

    def test_the_job_runs_the_tests_of_the_pull_request_build_on_jdk_17(self):
        self.assertEqual("17", str(step(self.build(), "Set up Java")["with"]["java-version"]))
        self.assertIn("./gradlew test -x integration -x integrationTest", step(self.build(), "Run the unit tests")["run"])

    def test_the_build_steps_are_skipped_when_no_code_changed(self):
        for name in ("Set up Java", "Set up Gradle", "Run the unit tests"):
            with self.subTest(step=name):
                self.assertIn("steps.scope.outputs.code == 'true'", step(self.build(), name)["if"])

    def test_the_scope_step_diffs_the_merge_group(self):
        env = step(self.build(), "Decide whether the build is needed")["env"]
        self.assertIn(MERGE_GROUP_BASE, env["BASE"])
        self.assertIn(MERGE_GROUP_HEAD, env["HEAD"])

    def test_the_workflow_writes_nothing(self):
        self.assertEqual({"contents": "read"}, self.workflow()["permissions"])
        self.assertNotIn("permissions", self.build())


class ScopeStepsTest(unittest.TestCase):
    def test_coverage_scope_step_diffs_the_merge_group(self):
        env = step(load("ci.yml")["jobs"]["coverage-changes"], "Check which coverage jobs are needed")["env"]
        self.assertIn(MERGE_GROUP_BASE, env["BASE"])
        self.assertIn(MERGE_GROUP_HEAD, env["HEAD"])

    def test_backend_selection_diffs_the_merge_group(self):
        env = step(load("ci.yml")["jobs"]["coverage-backend"], "Select the backend tests")["env"]
        self.assertIn(MERGE_GROUP_BASE, env["BASE"])
        self.assertIn(MERGE_GROUP_HEAD, env["HEAD"])

    def test_frontend_selection_diffs_the_merge_group(self):
        step_ = step(load("ci.yml")["jobs"]["coverage-frontend"], "Decide which frontend tests the change needs")
        self.assertIn(MERGE_GROUP_BASE, step_["env"]["BASE"])
        self.assertIn(MERGE_GROUP_HEAD, step_["env"]["HEAD"])
        self.assertIn('"$EVENT" = "merge_group"', step_["run"])

    def test_heavy_gate_diffs_the_merge_group(self):
        gate = load("heavy.yml")["jobs"]["gate"]
        env = step(gate, "Decide which heavy jobs run")["env"]
        self.assertIn(MERGE_GROUP_BASE, env["BASE"])
        self.assertIn(MERGE_GROUP_HEAD, env["HEAD"])
        self.assertIn("--scope-file", step(gate, "Decide which heavy jobs run")["run"])


class CoverageScopeOnMergeGroupTest(unittest.TestCase):
    """Runs the script of the coverage scope step the way the runner does, on a merge_group event."""

    def run_scope(self, changed_path):
        script = step(load("ci.yml")["jobs"]["coverage-changes"], "Check which coverage jobs are needed")["run"]
        with tempfile.TemporaryDirectory() as tmp:
            repo = os.path.join(tmp, "repo")
            os.makedirs(os.path.join(repo, ".github", "scripts"))
            git(repo, "init", "-q")
            with open(os.path.join(SCRIPTS_DIR, "ci_scope.sh"), encoding="utf-8") as source, \
                    open(os.path.join(repo, ".github", "scripts", "ci_scope.sh"), "w", encoding="utf-8") as target:
                target.write(source.read())
            base = commit(repo, "README.md", "base")
            head = commit(repo, changed_path, "change")
            output = os.path.join(tmp, "output")
            env = dict(os.environ, EVENT="merge_group", BASE=base, HEAD=head, GITHUB_OUTPUT=output, RUNNER_TEMP=tmp)
            subprocess.run(["bash", "-e", "-c", script], cwd=repo, env=env, check=True, capture_output=True, text=True)
            with open(output, encoding="utf-8") as handle:
                return dict(line.split("=", 1) for line in handle.read().splitlines() if "=" in line)

    def test_a_documentation_only_merge_group_skips_the_coverage_jobs(self):
        outputs = self.run_scope("docs/guide.md")
        self.assertEqual("false", outputs["backend"])
        self.assertEqual("false", outputs["unit"])

    def test_a_backend_merge_group_runs_the_backend_job(self):
        outputs = self.run_scope("reposilite-backend/src/main/kotlin/X.kt")
        self.assertEqual("true", outputs["backend"])
        self.assertEqual("false", outputs["frontend"])


class BuildScopeScriptTest(unittest.TestCase):
    """build_scope.sh mirrors the default path filter of the shared gradle-build-pr workflow."""

    def code(self, paths):
        result = subprocess.run(["bash", BUILD_SCOPE], input="".join(p + "\n" for p in paths),
                                capture_output=True, text=True, check=True)
        return result.stdout.strip()

    def test_documentation_only_needs_no_build(self):
        self.assertEqual("code=false", self.code(["docs/guide.md", "README.md"]))

    def test_frontend_only_needs_no_build(self):
        self.assertEqual("code=false", self.code(["reposilite-frontend/src/App.vue"]))

    def test_chart_only_needs_no_build(self):
        self.assertEqual("code=false", self.code(["charts/ingot/values.yaml"]))

    def test_empty_change_list_needs_no_build(self):
        self.assertEqual("code=false", self.code([]))

    def test_each_path_of_the_shared_filter_needs_the_build(self):
        for path in ("reposilite-backend/build.gradle.kts", "settings.gradle.kts", "buildSrc/x.gradle",
                     "gradle.properties", "sub/gradle.properties", "gradle/wrapper/gradle-wrapper.properties",
                     "gradlew", "gradlew.bat", "buildSrc/src/main/kotlin/A.kt", "src/main/A.java",
                     "reposilite-backend/src/main/kotlin/X.kt", "a/B.java", "a/B.groovy", "a/B.scala",
                     ".github/workflows/ci.yml"):
            with self.subTest(path=path):
                self.assertEqual("code=true", self.code([path]))

    def test_a_path_git_quotes_needs_the_build(self):
        self.assertEqual("code=true", self.code(['"reposilite-backend/\\303\\244.kt"']))


class PullRequestWritesTest(unittest.TestCase):
    def test_the_shared_build_that_comments_on_the_pull_request_stays_on_pull_requests(self):
        self.assertEqual("github.event_name == 'pull_request'", load("ci.yml")["jobs"]["build"]["if"])

    def test_codecov_is_not_uploaded_from_the_merge_group(self):
        upload = step(load("ci.yml")["jobs"]["coverage-backend"], "Upload coverage to Codecov")
        self.assertIn("github.event_name != 'merge_group'", upload["if"])

    def test_the_full_coverage_run_is_not_started_from_the_merge_group(self):
        run = step(load("ci.yml")["jobs"]["coverage-backend"], "Run tests with coverage")
        self.assertIn("github.event_name != 'merge_group'", run["if"])

    def test_the_merge_group_runs_the_backend_unit_tests_like_a_pull_request(self):
        run = step(load("ci.yml")["jobs"]["coverage-backend"], "Run the backend unit tests")
        self.assertIn("github.event_name == 'merge_group'", run["if"])

    def test_no_job_of_a_merge_group_workflow_asks_for_write_access_outside_pull_requests(self):
        for name in set(REQUIRED_CHECKS.values()):
            for job_id, job in load(name)["jobs"].items():
                permissions = job.get("permissions") or {}
                writes = {scope for scope, level in permissions.items() if level == "write"}
                writes.discard("security-events")  # CodeQL, which the heavy gate never starts on a merge group
                if writes:
                    with self.subTest(workflow=name, job=job_id):
                        self.assertEqual("github.event_name == 'pull_request'", job.get("if"))


if __name__ == "__main__":
    unittest.main()
