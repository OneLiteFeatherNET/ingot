"""Tests for the shape of .github/workflows/heavy.yml.

The tests read the workflow fresh in every test and check what it promises: it starts from pull request events only, it
holds no secret, its concurrency group lets a foreign label cancel nothing, it has no workflow-level path filter (a
skipped required check would stay pending) and every heavy job is gated on the heavy gate. They need no network, no
clock and no GitHub API. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import importlib.util
import os
import re
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
REPO_DIR = os.path.dirname(os.path.dirname(SCRIPTS_DIR))
WORKFLOW = os.path.join(REPO_DIR, ".github", "workflows", "heavy.yml")

_SPEC = importlib.util.spec_from_file_location("heavy_gate", os.path.join(SCRIPTS_DIR, "heavy_gate.py"))
heavy_gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(heavy_gate)

SHA_PINNED_USES = re.compile(r"^\s*(?:- )?uses:\s*\S+@[0-9a-f]{40}\s+# v\d+\.\d+\.\d+\s*$")
ANY_USES = re.compile(r"^\s*(?:- )?uses:\s*\S+")
AGGREGATE = "heavy-tests"
GATE = "gate"


def read_text():
    with open(WORKFLOW, encoding="utf-8") as handle:
        return handle.read()


def load():
    return yaml.safe_load(read_text())


def triggers():
    # PyYAML reads the key `on` as the boolean True.
    workflow = load()
    return workflow.get("on", workflow.get(True))


# Jobs that only prepare or split one heavy job. They are gated like the job they belong to, and the aggregate does
# not wait for them directly because the heavy job they belong to does. A helper may serve several heavy jobs, and it
# runs when any of their flags is set.
HELPERS = {
    "images-build": ("images", "helm", "ha"),
    "images-arm64": ("images",),
    "container-tests": ("images",),
    "integration-prepare": ("integration",),
    "integration-shards": ("integration",),
}
SHARD_COUNT = 8


def all_job_ids():
    return [job for job in load()["jobs"] if job not in (GATE, AGGREGATE)]


def heavy_job_ids():
    return [job for job in all_job_ids() if job not in HELPERS]


class TriggerTest(unittest.TestCase):
    def test_pull_request_types_include_label_events(self):
        types = triggers()["pull_request"]["types"]
        self.assertEqual({"opened", "reopened", "synchronize", "labeled", "unlabeled"}, set(types))

    def test_pull_request_targets_main(self):
        self.assertEqual(["main"], triggers()["pull_request"]["branches"])

    def test_there_is_no_schedule(self):
        self.assertNotIn("schedule", triggers())

    def test_there_is_no_workflow_level_path_or_branch_filter(self):
        pull_request = triggers()["pull_request"]
        for key in ("paths", "paths-ignore", "branches-ignore"):
            self.assertNotIn(key, pull_request)

    def test_workflow_dispatch_is_offered_and_the_gate_receives_the_ref(self):
        self.assertIn("workflow_dispatch", triggers())
        self.assertIn('--ref "$REF"', read_text())
        self.assertIn("REF: ${{ github.ref }}", read_text())

    def test_only_expected_events_start_the_workflow(self):
        self.assertLessEqual(set(triggers()), {"pull_request", "workflow_dispatch", "merge_group"})

    def test_the_merge_queue_starts_the_workflow(self):
        self.assertEqual({"types": ["checks_requested"]}, triggers()["merge_group"])


class SafetyTest(unittest.TestCase):
    def test_pull_request_target_is_never_used(self):
        self.assertNotIn("pull_request_target", read_text())

    def test_workflow_run_is_never_used(self):
        self.assertNotIn("workflow_run", read_text())

    def test_no_secret_is_read(self):
        self.assertNotIn("secrets.", read_text())

    def test_no_secrets_inherit(self):
        self.assertNotIn("secrets: inherit", read_text())

    def test_default_permission_is_read_only(self):
        self.assertEqual({"contents": "read"}, load()["permissions"])

    def test_no_job_asks_for_a_write_permission_except_code_scanning_for_codeql(self):
        for name, job in load()["jobs"].items():
            for scope, level in (job.get("permissions") or {}).items():
                if (name, scope) == ("codeql", "security-events"):
                    continue
                with self.subTest(job=name, scope=scope):
                    self.assertNotEqual("write", level)

    def test_every_action_is_pinned_to_a_sha_with_a_version_comment(self):
        for line in read_text().splitlines():
            if ANY_USES.match(line) and "uses: ./" not in line:  # a local workflow is pinned by the commit itself
                with self.subTest(line=line.strip()):
                    self.assertRegex(line, SHA_PINNED_USES)

    def test_checkout_does_not_persist_credentials(self):
        text = read_text()
        self.assertEqual(text.count("actions/checkout@"), text.count("persist-credentials: false"))


class ConcurrencyTest(unittest.TestCase):
    def test_group_is_keyed_on_the_pull_request(self):
        group = load()["concurrency"]["group"]
        self.assertIn("github.event.pull_request.number", group)

    def test_group_of_a_foreign_label_is_unique_per_run(self):
        group = load()["concurrency"]["group"]
        self.assertIn("startsWith(github.event.label.name, 'ci:')", group)
        self.assertIn("github.run_id", group)

    def test_only_pull_request_runs_are_cancelled(self):
        self.assertIn("github.event_name == 'pull_request'", str(load()["concurrency"]["cancel-in-progress"]))


class IntegrationJobTest(unittest.TestCase):
    """The aggregate keeps the check name; the matrix of shards does the work."""

    def job(self):
        return load()["jobs"]["integration"]

    def shards(self):
        return load()["jobs"]["integration-shards"]

    def shard_steps(self):
        return {step["name"]: step for step in self.shards()["steps"]}

    def test_the_check_name_is_the_one_the_design_names(self):
        self.assertEqual("Integration tests (backend)", self.job()["name"])

    def test_the_aggregate_waits_for_the_shards(self):
        self.assertIn("integration-shards", self.job()["needs"])

    def test_the_aggregate_has_the_same_gate_condition_as_the_shards(self):
        self.assertEqual(self.shards()["if"], self.job()["if"])

    def test_the_aggregate_fails_unless_every_shard_succeeded(self):
        steps = self.job()["steps"]
        self.assertEqual(1, len(steps))
        self.assertEqual("${{ needs.integration-shards.result }}", steps[0]["env"]["SHARDS"])
        self.assertIn('test "$SHARDS" = success', steps[0]["run"])

    def test_the_aggregate_does_not_run_gradle(self):
        self.assertNotIn("gradlew", str(self.job()["steps"]))

    def test_the_shards_are_indexed_zero_to_seven_and_never_fail_fast(self):
        strategy = self.shards()["strategy"]
        self.assertIs(False, strategy["fail-fast"])
        self.assertEqual(list(range(SHARD_COUNT)), strategy["matrix"]["shard"])
        self.assertEqual(SHARD_COUNT, len(strategy["matrix"]["shard"]))

    def test_each_shard_has_a_time_limit_of_ninety_minutes(self):
        self.assertEqual(90, self.shards()["timeout-minutes"])

    def test_the_shard_job_runs_with_four_forks(self):
        self.assertEqual("4", str(self.shards()["env"]["INGOT_TEST_FORKS"]))

    def test_the_gradle_runs_pass_the_shard_index_and_the_count(self):
        for name in ("Run the selected integration tests", "Run the full integration suite"):
            with self.subTest(step=name):
                self.assertIn("-PintegrationShard=${{ matrix.shard }} -PintegrationShards=8 $GRADLE_RETRY",
                              self.shard_steps()[name]["run"])

    def test_the_shards_retry_downloads_that_maven_central_throttles(self):
        retry = self.shards()["env"]["GRADLE_RETRY"]
        self.assertIn("-Dorg.gradle.internal.repository.max.retries=10", retry)
        self.assertIn("-Dorg.gradle.internal.repository.initial.backoff=2000", retry)

    def test_each_shard_publishes_its_own_report(self):
        step = self.shard_steps()["Publish the integration test report"]
        self.assertEqual("integration-test-report-shard-${{ matrix.shard }}", step["with"]["name"])

    def test_a_run_that_asked_for_full_ignores_the_selection(self):
        text = read_text()
        self.assertIn("FULL: ${{ needs.gate.outputs.full }}", text)
        self.assertIn('[ "$EVENT" != "pull_request" ] || [ "$FULL" = "true" ]', text)

    def test_a_selection_without_an_integration_filter_runs_the_full_suite(self):
        self.assertIn("grep -q '^integration='", read_text())

    def test_the_full_suite_step_runs_unless_the_selection_is_partial(self):
        steps = self.shard_steps()
        self.assertIn("!= 'partial'", steps["Run the full integration suite"]["if"])
        self.assertIn("== 'partial'", steps["Run the selected integration tests"]["if"])


class IntegrationPrepareTest(unittest.TestCase):
    """The prepare job compiles the suite once and saves the Gradle caches; the shards restore exactly those caches."""

    PREPARE = "integration-prepare"

    def prepare(self):
        return load()["jobs"][self.PREPARE]

    def shards(self):
        return load()["jobs"]["integration-shards"]

    def aggregate(self):
        return load()["jobs"]["integration"]

    def gradle_runs(self, job):
        return [step["run"] for step in job["steps"] if "gradlew" in step.get("run", "")]

    def uses_of(self, job, action):
        return [step for step in job["steps"] if step.get("uses", "").startswith(action + "@")]

    def test_the_prepare_job_has_the_name_the_design_names(self):
        self.assertEqual("Prepare the integration shards", self.prepare()["name"])

    def test_the_prepare_job_has_the_gate_condition_of_the_shards(self):
        self.assertEqual(self.shards()["if"], self.prepare()["if"])

    def test_the_prepare_job_needs_only_the_gate(self):
        self.assertEqual([GATE], self.prepare()["needs"])

    def test_the_shards_wait_for_the_prepare_job(self):
        self.assertIn(self.PREPARE, self.shards()["needs"])

    def test_the_prepare_job_runs_one_gradle_build_and_no_test(self):
        runs = self.gradle_runs(self.prepare())
        self.assertEqual(1, len(runs))
        self.assertIn(":reposilite-backend:prepareIntegration", runs[0])
        self.assertNotIn(":reposilite-backend:integration", runs[0])

    def test_the_prepare_build_retries_throttled_downloads_and_uses_the_build_cache(self):
        run = self.gradle_runs(self.prepare())[0]
        self.assertIn("$GRADLE_RETRY", run)
        self.assertIn("--build-cache", run)
        self.assertEqual(self.shards()["env"]["GRADLE_RETRY"], self.prepare()["env"]["GRADLE_RETRY"])

    def test_every_shard_gradle_run_keeps_the_build_cache(self):
        for run in self.gradle_runs(self.shards()):
            with self.subTest(run=run):
                self.assertIn("--build-cache", run)

    def test_the_prepare_job_saves_exactly_one_cache(self):
        self.assertEqual(1, len(self.uses_of(self.prepare(), "actions/cache/save")))

    def test_the_shards_restore_exactly_one_cache(self):
        self.assertEqual(1, len(self.uses_of(self.shards(), "actions/cache/restore")))

    def test_the_shards_restore_the_key_the_prepare_job_saves(self):
        saved = self.uses_of(self.prepare(), "actions/cache/save")[0]["with"]["key"]
        restored = self.uses_of(self.shards(), "actions/cache/restore")[0]["with"]["key"]
        self.assertEqual(saved, restored)

    def test_the_key_names_the_run_so_a_rerun_of_failed_shards_finds_it(self):
        key = self.uses_of(self.prepare(), "actions/cache/save")[0]["with"]["key"]
        self.assertIn("github.run_id", key)
        self.assertNotIn("github.run_attempt", key)

    def test_the_prepare_job_saves_the_dependency_and_the_build_cache(self):
        path = self.uses_of(self.prepare(), "actions/cache/save")[0]["with"]["path"]
        self.assertIn("~/.gradle/caches/modules-2", path)
        self.assertIn("~/.gradle/caches/build-cache-1", path)

    def test_the_prepare_job_saves_only_after_a_successful_build(self):
        self.assertEqual("${{ success() }}", self.uses_of(self.prepare(), "actions/cache/save")[0]["if"])

    def test_the_shards_do_not_fail_on_a_cache_miss(self):
        self.assertIs(False, self.uses_of(self.shards(), "actions/cache/restore")[0]["with"]["fail-on-cache-miss"])

    def test_the_shards_restore_before_their_gradle_run(self):
        names = [step.get("name", "") for step in self.shards()["steps"]]
        restore = names.index("Restore the integration caches of the prepare job")
        self.assertLess(names.index("Set up Gradle"), restore)
        self.assertLess(restore, names.index("Run the full integration suite"))
        self.assertLess(restore, names.index("Run the selected integration tests"))

    def test_save_and_restore_come_from_the_same_pinned_commit(self):
        saved = self.uses_of(self.prepare(), "actions/cache/save")[0]["uses"]
        restored = self.uses_of(self.shards(), "actions/cache/restore")[0]["uses"]
        self.assertEqual(saved.split("@")[1], restored.split("@")[1])

    def test_the_prepare_job_is_a_helper_of_the_integration_flag(self):
        self.assertEqual(("integration",), HELPERS[self.PREPARE])

    def test_the_aggregate_also_waits_for_the_prepare_job(self):
        self.assertIn(self.PREPARE, self.aggregate()["needs"])

    def test_the_aggregate_fails_when_the_prepare_job_failed(self):
        step = self.aggregate()["steps"][0]
        self.assertEqual("${{ needs.integration-prepare.result }}", step["env"]["PREPARE"])
        self.assertIn('test "$PREPARE" = success', step["run"])


class GateAndAggregateTest(unittest.TestCase):
    def test_the_gate_and_the_aggregate_exist_with_their_check_names(self):
        jobs = load()["jobs"]
        self.assertEqual("Heavy gate", jobs[GATE]["name"])
        self.assertEqual("Heavy tests", jobs[AGGREGATE]["name"])

    def test_the_aggregate_always_runs(self):
        self.assertIn("always()", str(load()["jobs"][AGGREGATE]["if"]))

    def test_the_aggregate_waits_for_the_gate_and_every_heavy_job(self):
        needs = load()["jobs"][AGGREGATE]["needs"]
        self.assertEqual({GATE, *heavy_job_ids()}, set(needs))

    def test_every_helper_belongs_to_a_job_the_gate_script_knows(self):
        for helper, flags in HELPERS.items():
            for flag in flags:
                self.assertIn(flag, heavy_gate.HEAVY_JOBS, helper)

    def test_every_heavy_job_id_is_known_to_the_gate_script(self):
        self.assertLessEqual(set(heavy_job_ids()), set(heavy_gate.HEAVY_JOBS))

    def test_every_heavy_job_runs_when_the_gate_failed_and_otherwise_on_its_flag(self):
        for job_id in all_job_ids():
            condition = str(load()["jobs"][job_id]["if"])
            flags = HELPERS.get(job_id, (job_id,))
            with self.subTest(job=job_id):
                self.assertIn("!cancelled()", condition)
                self.assertIn("needs.gate.result != 'success'", condition)
                for flag in flags:
                    self.assertIn(f"needs.gate.outputs.{flag} == 'true'", condition)

    def test_every_heavy_job_needs_the_gate(self):
        for job_id in all_job_ids():
            with self.subTest(job=job_id):
                self.assertIn(GATE, load()["jobs"][job_id]["needs"])

    def test_the_gate_exposes_one_output_per_heavy_job(self):
        outputs = load()["jobs"][GATE]["outputs"]
        for job in heavy_gate.HEAVY_JOBS:
            with self.subTest(job=job):
                self.assertIn(job, outputs)

    def test_all_seven_heavy_jobs_exist(self):
        self.assertEqual(set(heavy_gate.HEAVY_JOBS), set(heavy_job_ids()))

    def test_search_and_codeql_call_their_own_workflows(self):
        jobs = load()["jobs"]
        self.assertEqual("./.github/workflows/search-backends.yml", jobs["search"]["uses"])
        self.assertEqual("./.github/workflows/codeql.yml", jobs["codeql"]["uses"])

    def test_codeql_asks_for_the_one_extra_scope_it_needs_and_search_for_none(self):
        jobs = load()["jobs"]
        self.assertEqual({"contents": "read", "security-events": "write"}, jobs["codeql"]["permissions"])
        self.assertEqual({"contents": "read"}, jobs["search"]["permissions"])

    def test_every_job_has_a_timeout(self):
        for name, job in load()["jobs"].items():
            if "uses" in job:
                continue  # a job that calls a workflow takes its time limit from the called jobs
            with self.subTest(job=name):
                self.assertIn("timeout-minutes", job)


if __name__ == "__main__":
    unittest.main()
