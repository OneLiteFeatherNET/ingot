"""Tests for .github/scripts/ci_scope.sh and the coverage jobs in ci.yml.

The script reads changed paths on stdin and prints seven lines: backend=, frontend=, frontend_only=, image=,
chart=, e2e= and unit=, each true or false. The workflow appends those lines to GITHUB_OUTPUT. Each test names one behaviour and runs the
script as a subprocess. No git repository, no network, no clock, no files.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import subprocess
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT = os.path.join(SCRIPTS_DIR, "ci_scope.sh")
CI_WORKFLOW = os.path.join(os.path.dirname(SCRIPTS_DIR), "workflows", "ci.yml")

CORE = ("backend", "frontend", "frontend_only")
FLAGS = CORE + ("image", "chart", "e2e", "unit")
NONE = {"backend": "false", "frontend": "false", "frontend_only": "false"}


def scope(paths):
    """Feeds the changed paths to the script and returns every output as a dict."""
    result = subprocess.run(
        ["bash", SCRIPT],
        input="".join(path + "\n" for path in paths),
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(f"script exited with {result.returncode}: {result.stderr}")
    outputs = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition("=")
        outputs[key] = value
    return outputs


def decide(paths):
    """The three coverage outputs of the script, which the coverage jobs read."""
    outputs = scope(paths)
    return {key: outputs[key] for key in CORE}


def truthy(paths):
    """The names of the flags that are true for the changed paths."""
    return {key for key, value in scope(paths).items() if value == "true"}


def job_block(text, job):
    """Returns the YAML text of one top-level job of the workflow, or None when it does not exist."""
    match = re.search(
        rf"^  {re.escape(job)}:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)",
        text,
        re.S | re.M,
    )
    return match.group(1) if match else None


def step_block(block, name):
    """Returns the YAML text of one step of a job block, identified by its name, or None."""
    match = re.search(
        rf"^      - name: \"{re.escape(name)}\"\n(.*?)(?=^      - name:|\Z)",
        block,
        re.S | re.M,
    )
    return match.group(1) if match else None


class CoverageChangesScriptTest(unittest.TestCase):

    def test_documentation_only_change_skips_every_coverage_job(self):
        self.assertEqual(decide(["docs/guide.md"]), NONE)

    def test_openspec_only_change_skips_every_coverage_job(self):
        self.assertEqual(decide(["openspec/changes/x/proposal.md", "openspec/changes/x/tasks.md"]), NONE)

    def test_markdown_file_in_subfolder_skips_every_coverage_job(self):
        self.assertEqual(decide(["reposilite-backend/README.md"]), NONE)

    def test_root_markdown_file_skips_every_coverage_job(self):
        self.assertEqual(decide(["CHANGELOG.md"]), NONE)

    def test_markdown_file_in_frontend_is_documentation(self):
        self.assertEqual(decide(["reposilite-frontend/README.md"]), NONE)

    def test_empty_change_list_skips_every_coverage_job(self):
        self.assertEqual(decide([]), NONE)

    def test_mixed_docs_and_backend_source_runs_only_the_backend_job(self):
        self.assertEqual(
            decide(["docs/guide.md", "reposilite-backend/x.kt"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_openapi_document_change_runs_the_backend_and_frontend_jobs(self):
        # The backend OpenApiCompletenessTest reads the document, and the dashboard api types are generated from it.
        self.assertEqual(
            decide(["docs/openapi/ingot-openapi.json"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_openapi_document_change_with_frontend_change_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "docs/openapi/ingot-openapi.json"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_other_file_in_openapi_folder_still_skips_every_coverage_job(self):
        # Only the document itself is exempt from the deny list, not the whole docs/openapi/ folder.
        self.assertEqual(decide(["docs/openapi/breaking-changes-ignore.txt"]), NONE)

    def test_frontend_change_runs_the_backend_dashboard_subset_and_the_frontend_job(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue"]),
            {"backend": "true", "frontend": "true", "frontend_only": "true"},
        )

    def test_frontend_lockfile_is_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/package-lock.json"]),
            {"backend": "true", "frontend": "true", "frontend_only": "true"},
        )

    def test_frontend_and_backend_change_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "reposilite-backend/x.kt"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_frontend_and_docs_change_is_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "docs/guide.md"]),
            {"backend": "true", "frontend": "true", "frontend_only": "true"},
        )

    def test_frontend_and_workflow_change_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", ".github/workflows/ci.yml"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_frontend_and_unlisted_root_file_is_not_frontend_only(self):
        self.assertEqual(
            decide(["reposilite-frontend/x.vue", "build.gradle.kts"]),
            {"backend": "true", "frontend": "true", "frontend_only": "false"},
        )

    def test_workflow_change_runs_the_backend_job_only(self):
        self.assertEqual(
            decide([".github/workflows/ci.yml"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_scripts_change_runs_the_backend_job_only(self):
        self.assertEqual(
            decide([".github/scripts/check-image-annotations.sh"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_unlisted_root_file_runs_the_backend_job_only(self):
        self.assertEqual(
            decide(["build.gradle.kts"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_backend_source_runs_the_backend_job_only(self):
        self.assertEqual(
            decide(["reposilite-backend/src/main/kotlin/X.kt"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_directory_with_frontend_prefix_but_other_name_is_not_frontend(self):
        self.assertEqual(
            decide(["reposilite-frontend-tools/x.js"]),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )

    def test_quoted_path_from_git_is_treated_as_backend(self):
        # git quotes non-ASCII names, so the frontend prefix does not match and the full run is kept.
        self.assertEqual(
            decide(['"reposilite-frontend/\\303\\244.vue"']),
            {"backend": "true", "frontend": "false", "frontend_only": "false"},
        )


class ScopeFlagsTest(unittest.TestCase):
    """The image, chart, e2e and unit flags. Anything the script cannot place turns every flag on."""

    EVERYTHING = set(FLAGS)

    def test_script_prints_every_flag_exactly_once_and_in_order(self):
        result = subprocess.run(
            ["bash", SCRIPT], input="docs/guide.md\n", capture_output=True, text=True, check=False
        )
        keys = [line.partition("=")[0] for line in result.stdout.splitlines()]
        self.assertEqual(keys, list(FLAGS))

    def test_documentation_only_change_turns_every_flag_off(self):
        self.assertEqual(truthy(["docs/guide.md", "openspec/changes/x/tasks.md"]), set())

    def test_empty_change_list_turns_every_flag_off(self):
        self.assertEqual(truthy([]), set())

    def test_chart_change_turns_on_the_chart_flag_only(self):
        self.assertEqual(truthy(["charts/ingot/values.yaml"]) - {"backend"}, {"chart"})

    def test_dockerfile_change_turns_on_the_image_flag_without_unit_tests(self):
        flags = truthy(["Dockerfile"])
        self.assertIn("image", flags)
        self.assertNotIn("unit", flags)
        self.assertNotIn("chart", flags)

    def test_entrypoint_change_turns_on_the_image_flag(self):
        self.assertIn("image", truthy(["entrypoint.sh"]))

    def test_compose_file_change_turns_on_the_image_flag(self):
        self.assertIn("image", truthy(["docker-compose.split.yml"]))

    def test_dashboard_dockerfile_change_turns_on_the_image_flag(self):
        self.assertIn("image", truthy(["reposilite-frontend/Dockerfile"]))

    def test_backend_endpoint_turns_on_unit_and_e2e_but_not_image_or_chart(self):
        flags = truthy(["reposilite-backend/src/main/kotlin/com/reposilite/status/StatusEndpoints.kt"])
        self.assertTrue({"unit", "e2e", "backend"} <= flags)
        self.assertFalse({"image", "chart"} & flags)

    def test_backend_internal_source_turns_on_unit_but_not_e2e_image_or_chart(self):
        flags = truthy(["reposilite-backend/src/main/kotlin/X.kt"])
        self.assertTrue({"unit", "backend"} <= flags)
        self.assertFalse({"e2e", "image", "chart"} & flags)

    def test_frontend_source_turns_on_unit_and_e2e_but_not_image_or_chart(self):
        flags = truthy(["reposilite-frontend/src/App.vue"])
        self.assertTrue({"unit", "e2e", "frontend"} <= flags)
        self.assertFalse({"image", "chart"} & flags)

    def test_openapi_document_change_turns_on_unit_tests(self):
        # The document is not a module of any known area, so the scope flags fail open like an unknown path.
        self.assertIn("unit", truthy(["docs/openapi/ingot-openapi.json"]))

    def test_unknown_root_path_turns_every_flag_on(self):
        self.assertEqual(truthy(["something-new/file.txt"]), self.EVERYTHING - {"frontend", "frontend_only"})

    def test_gradle_build_file_turns_every_scope_flag_on(self):
        flags = truthy(["build.gradle.kts"])
        self.assertTrue({"image", "chart", "e2e", "unit"} <= flags)

    def test_heavy_workflow_change_turns_every_scope_flag_on(self):
        flags = truthy([".github/workflows/heavy.yml"])
        self.assertTrue({"image", "chart", "e2e", "unit"} <= flags)

    def test_workflow_script_change_turns_every_scope_flag_on(self):
        flags = truthy([".github/scripts/ci_scope.sh"])
        self.assertTrue({"image", "chart", "e2e", "unit"} <= flags)

    def test_shared_test_module_change_turns_every_scope_flag_on(self):
        flags = truthy(["reposilite-test/workspace/x"])
        self.assertTrue({"image", "chart", "e2e", "unit"} <= flags)

    def test_quoted_path_from_git_turns_every_scope_flag_on(self):
        flags = truthy(['"charts/ingot/\\303\\244.yaml"'])
        self.assertTrue({"image", "chart", "e2e", "unit"} <= flags)

    def test_mixed_chart_and_docs_change_turns_on_the_chart_flag_only(self):
        self.assertEqual(truthy(["charts/ingot/Chart.yaml", "docs/a.md"]) - {"backend"}, {"chart"})


class E2eScopeTest(unittest.TestCase):
    """The e2e flag: on for what the dashboard end-to-end suite can see, off for everything else it can place."""

    SEES = [
        # The dashboard itself, and the specs, harness and fixtures that live beside it.
        "reposilite-frontend/src/App.vue",
        "reposilite-frontend/package-lock.json",
        "reposilite-frontend/e2e/browse.spec.js",
        "reposilite-frontend/e2e/support/server.js",
        "reposilite-frontend/e2e/support/fixtures/e2e-style/Plugin.kt",
        "reposilite-frontend/playwright.config.js",
        # The OpenAPI document the dashboard api types are generated from.
        "docs/openapi/ingot-openapi.json",
        # The HTTP surface of the backend the dashboard calls, and the dashboard serving code.
        "reposilite-backend/src/main/kotlin/com/reposilite/status/StatusEndpoints.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/auth/AuthenticationEndpoint.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/frontend/FrontendSettingsEndpoint.kt",
        "reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/EnrolmentRoutes.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/web/HttpServer.kt",
        "reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/web/Cors.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/token/api/AccessTokenDto.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/frontend/FrontendFacade.kt",
        "reposilite-backend/src/main/resources/static/index.html",
        "reposilite-backend/src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin",
        # What builds or starts the server for the suite.
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradlew",
        "gradle/wrapper/gradle-wrapper.properties",
        "buildSrc/src/main/kotlin/ingot.kotlin.gradle.kts",
        "reposilite-backend/build.gradle.kts",
        ".github/workflows/heavy.yml",
        ".github/scripts/ci_scope.sh",
        ".github/scripts/heavy_gate.py",
    ]

    BLIND = [
        # Backend internals and backend tests.
        "reposilite-backend/src/main/kotlin/com/reposilite/maven/MavenFacade.kt",
        "reposilite-backend/src/main/kotlin/com/reposilite/storage/StorageProviderFactory.kt",
        "reposilite-backend/src/test/kotlin/com/reposilite/status/StatusEndpointsTest.kt",
        "reposilite-backend/src/integration/kotlin/com/reposilite/web/HttpIT.kt",
        "reposilite-backend/detekt.yml",
        # Plugins, including plugin endpoints: the suite loads no plugin of this repository.
        "reposilite-plugins/prometheus-plugin/src/main/kotlin/com/reposilite/plugin/prometheus/PrometheusEndpoints.kt",
        "reposilite-plugins/lucene-plugin/build.gradle.kts",
        # The search sidecar, the chart, the images, the load tests and the other workflows.
        "reposilite-search-http/src/main/kotlin/Server.kt",
        "charts/ingot/values.yaml",
        "Dockerfile",
        "entrypoint.sh",
        "docker-compose.yml",
        "reposilite-frontend/Dockerfile",
        "loadtest/scenarios/download.js",
        ".github/workflows/ci.yml",
        ".github/workflows/codeql.yml",
        ".github/scripts/tests/test_ci_scope.py",
        # Documentation and proposals.
        "docs/openapi/breaking-changes-ignore.txt",
        "docs/guide.md",
        "openspec/changes/x/tasks.md",
    ]

    def test_paths_the_dashboard_can_see_turn_on_e2e(self):
        for path in self.SEES:
            with self.subTest(path=path):
                self.assertIn("e2e", truthy([path]))

    def test_paths_the_dashboard_cannot_see_leave_e2e_off(self):
        for path in self.BLIND:
            with self.subTest(path=path):
                self.assertNotIn("e2e", truthy([path]))

    def test_one_visible_path_among_blind_ones_turns_on_e2e(self):
        self.assertIn("e2e", truthy(self.BLIND + ["reposilite-frontend/src/App.vue"]))

    def test_unknown_path_still_turns_on_e2e(self):
        for path in ("something-new/file.txt", "renovate.json", "ingot-cli/main.go", "reposilite-test/workspace/x",
                     '"reposilite-backend/\\303\\244.kt"'):
            with self.subTest(path=path):
                self.assertIn("e2e", truthy([path]))

    def test_other_flags_are_unchanged_for_paths_that_leave_e2e_off(self):
        # Only e2e was narrowed: these are the flags the paths selected before.
        expected = {
            "reposilite-backend/src/main/kotlin/com/reposilite/maven/MavenFacade.kt": {"backend", "unit"},
            "reposilite-plugins/lucene-plugin/build.gradle.kts": {"backend", "unit"},
            "reposilite-search-http/src/main/kotlin/Server.kt": {"backend", "unit"},
            "charts/ingot/values.yaml": {"backend", "chart"},
            "Dockerfile": {"backend", "image"},
            "loadtest/scenarios/download.js": {"backend", "image", "chart", "unit"},
            ".github/workflows/ci.yml": {"backend", "image", "chart", "unit"},
            ".github/scripts/tests/test_ci_scope.py": {"backend", "image", "chart", "unit"},
        }
        for path, flags in expected.items():
            with self.subTest(path=path):
                self.assertEqual(truthy([path]), flags)

    def test_other_flags_are_unchanged_for_paths_that_turn_e2e_on(self):
        expected = {
            "reposilite-frontend/src/App.vue": {"backend", "frontend", "frontend_only", "unit", "e2e"},
            "reposilite-backend/src/main/kotlin/com/reposilite/status/StatusEndpoints.kt": {"backend", "unit", "e2e"},
            "build.gradle.kts": {"backend", "image", "chart", "unit", "e2e"},
            ".github/workflows/heavy.yml": {"backend", "image", "chart", "unit", "e2e"},
        }
        for path, flags in expected.items():
            with self.subTest(path=path):
                self.assertEqual(truthy([path]), flags)


class CoverageJobsInWorkflowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()

    def test_detection_job_exposes_all_three_outputs(self):
        block = job_block(self.workflow, "coverage-changes")
        self.assertIsNotNone(block, "ci.yml has no coverage-changes job")
        self.assertIn("name: \"Detect coverage changes\"", block)
        self.assertIn("backend: ${{ steps.filter.outputs.backend }}", block)
        self.assertIn("frontend: ${{ steps.filter.outputs.frontend }}", block)
        self.assertIn("frontend_only: ${{ steps.filter.outputs.frontend_only }}", block)

    def test_detection_job_exposes_the_scope_flags(self):
        block = job_block(self.workflow, "coverage-changes")
        for flag in ("image", "chart", "e2e", "unit"):
            self.assertIn(f"{flag}: ${{{{ steps.filter.outputs.{flag} }}}}", block)

    def test_detection_job_turns_every_scope_flag_on_when_the_range_is_unknown(self):
        block = job_block(self.workflow, "coverage-changes")
        for flag in ("image", "chart", "e2e", "unit"):
            self.assertIn(f'echo "{flag}=true" >> "$GITHUB_OUTPUT"', block)

    def test_detection_job_diffs_against_the_merge_base(self):
        block = job_block(self.workflow, "coverage-changes")
        self.assertIn('git diff --name-only "$BASE...$HEAD"', block)

    def test_frontend_and_backend_jobs_run_in_full_when_detection_did_not_succeed(self):
        # A failed detection must never skip a job: each job's condition tests the detection result first.
        for job in ("coverage-backend", "coverage-frontend"):
            block = job_block(self.workflow, job)
            self.assertIn("needs.coverage-changes.result != 'success'", block, job)

    def test_backend_job_is_named_and_gated_on_the_backend_output(self):
        block = job_block(self.workflow, "coverage-backend")
        self.assertIsNotNone(block, "ci.yml has no coverage-backend job")
        self.assertIn("name: \"Coverage (backend)\"", block)
        self.assertIn("needs: coverage-changes", block)
        self.assertIn("needs.coverage-changes.outputs.backend == 'true'", block)
        self.assertIn("!cancelled()", block)

    def test_frontend_job_is_named_and_gated_on_the_frontend_output(self):
        block = job_block(self.workflow, "coverage-frontend")
        self.assertIsNotNone(block, "ci.yml has no coverage-frontend job")
        self.assertIn("name: \"Coverage (frontend)\"", block)
        self.assertIn("needs: coverage-changes", block)
        self.assertIn("needs.coverage-changes.outputs.frontend == 'true'", block)
        self.assertIn("!cancelled()", block)

    def test_frontend_job_runs_the_suite_once_with_coverage(self):
        block = job_block(self.workflow, "coverage-frontend")
        self.assertIn("npm run test:coverage", block)

    def test_frontend_job_uploads_its_coverage_report_as_an_artifact(self):
        block = job_block(self.workflow, "coverage-frontend")
        self.assertIn("actions/upload-artifact@", block)
        self.assertIn("path: reposilite-frontend/coverage", block)

    def test_frontend_lint_job_does_not_run_the_suite_again(self):
        # The vitest suite runs only in Coverage (frontend). Running it in both jobs would double it.
        block = job_block(self.workflow, "frontend")
        self.assertIsNotNone(block, "ci.yml has no frontend job")
        self.assertNotIn("npm test", block)
        self.assertIn("npm run eslint", block)

    def test_backend_job_runs_the_full_suite_unless_only_the_frontend_changed(self):
        block = job_block(self.workflow, "coverage-backend")
        step = step_block(block, "Run tests with coverage")
        self.assertIsNotNone(step, "backend job has no full coverage step")
        self.assertIn("./gradlew --build-cache :reposilite-backend:testCoverage", step)
        self.assertIn("needs.coverage-changes.outputs.frontend_only != 'true'", step)

    def test_backend_job_runs_only_the_dashboard_tests_on_frontend_only_changes(self):
        block = job_block(self.workflow, "coverage-backend")
        step = step_block(block, "Run the dashboard tests of the backend")
        self.assertIsNotNone(step, "backend job has no dashboard-only step")
        self.assertIn("needs.coverage-changes.outputs.frontend_only == 'true'", step)
        self.assertIn(":reposilite-backend:test --tests 'com.reposilite.frontend.*'", step)
        self.assertNotIn(":reposilite-backend:integration", step)
        self.assertNotIn("testCoverage", step)

    def test_backend_job_skips_codecov_upload_on_frontend_only_changes(self):
        block = job_block(self.workflow, "coverage-backend")
        step = step_block(block, "Upload coverage to Codecov")
        self.assertIsNotNone(step, "backend job has no Codecov step")
        self.assertIn("needs.coverage-changes.outputs.frontend_only != 'true'", step)

    def test_coverage_aggregate_is_the_required_check_name(self):
        block = job_block(self.workflow, "coverage")
        self.assertIsNotNone(block, "ci.yml has no coverage aggregate job")
        self.assertIn("name: \"Coverage\"", block)

    def test_coverage_aggregate_runs_even_when_a_needed_job_was_skipped(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("if: ${{ always() }}", block)

    def test_coverage_aggregate_needs_both_coverage_jobs(self):
        block = job_block(self.workflow, "coverage")
        self.assertRegex(block, r"needs:\s*\[\s*coverage-backend\s*,\s*coverage-frontend\s*\]")

    def test_coverage_aggregate_fails_on_failed_or_cancelled_needed_jobs(self):
        block = job_block(self.workflow, "coverage")
        self.assertIn("needs.coverage-backend.result", block)
        self.assertIn("needs.coverage-frontend.result", block)
        self.assertIn("success|skipped)", block)


if __name__ == "__main__":
    unittest.main()
