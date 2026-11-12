"""Tests for .github/scripts/frontend_scope.sh and the frontend coverage job in ci.yml.

The script reads `git diff --name-status` lines on stdin and prints frontend_scope=related or
frontend_scope=full. A related answer also writes related.txt and readers.txt into the output directory. Each
test names one trigger and runs the script as a subprocess inside a temporary directory that holds a small
fixture tree, so no git repository, network or clock is involved.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import subprocess
import tempfile
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT = os.path.join(SCRIPTS_DIR, "frontend_scope.sh")
CI_WORKFLOW = os.path.join(os.path.dirname(SCRIPTS_DIR), "workflows", "ci.yml")
FRONTEND = "reposilite-frontend"


def write(root, relative, text=""):
    path = os.path.join(root, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def run_scope(root, name_status):
    """Runs the script in root with the given name-status lines. Returns (result, out_dir)."""
    out_dir = os.path.join(root, "out")
    result = subprocess.run(
        ["bash", SCRIPT, out_dir],
        input="".join(line + "\n" for line in name_status),
        capture_output=True,
        text=True,
        cwd=root,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(f"script exited with {result.returncode}: {result.stderr}")
    return result.stdout.strip(), out_dir


def read_lines(path):
    with open(path, encoding="utf-8") as handle:
        return [line for line in handle.read().splitlines() if line]


class FrontendScopeTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = self._tmp.name
        write(self.root, f"{FRONTEND}/src/components/card/CardMenu.vue", "<template />")
        write(self.root, f"{FRONTEND}/src/components/card/CardMenu.test.js", "")
        write(self.root, f"{FRONTEND}/src/store/palette.js", "export {}")
        write(self.root, f"{FRONTEND}/src/store/palette.test.js", "")
        write(self.root, f"{FRONTEND}/src/style.css", "")
        write(self.root, f"{FRONTEND}/src/style.test.js",
              "import { readFileSync } from 'node:fs'\nconst css = readFileSync('x', 'utf8')\n")
        write(self.root, f"{FRONTEND}/src/styles/md3/color.css", "")
        write(self.root, f"{FRONTEND}/src/main.js", "")
        write(self.root, f"{FRONTEND}/src/router/dashboard-segments.json", "[]")
        write(self.root, f"{FRONTEND}/server/lib/config.js", "")
        write(self.root, f"{FRONTEND}/server/lib/config.test.js", "")

    def tearDown(self):
        self._tmp.cleanup()

    def assertRelated(self, name_status):
        result, _ = run_scope(self.root, name_status)
        self.assertEqual(result, "frontend_scope=related")

    def assertFull(self, name_status):
        result, out_dir = run_scope(self.root, name_status)
        self.assertEqual(result, "frontend_scope=full")
        self.assertFalse(os.path.exists(os.path.join(out_dir, "related.txt")),
                         "a full run must not write the related list")

    def test_changed_leaf_component_is_related(self):
        result, out_dir = run_scope(self.root, ["M\t" + f"{FRONTEND}/src/components/card/CardMenu.vue"])
        self.assertEqual(result, "frontend_scope=related")
        self.assertEqual(read_lines(os.path.join(out_dir, "related.txt")), ["src/components/card/CardMenu.vue"])

    def test_changed_store_module_is_related(self):
        self.assertRelated(["M\t" + f"{FRONTEND}/src/store/palette.js"])

    def test_changed_test_file_is_related(self):
        self.assertRelated(["M\t" + f"{FRONTEND}/src/store/palette.test.js"])

    def test_added_module_is_related(self):
        self.assertRelated(["A\t" + f"{FRONTEND}/src/store/new.js"])

    def test_server_module_is_related(self):
        self.assertRelated(["M\t" + f"{FRONTEND}/server/lib/config.js"])

    def test_related_run_lists_test_files_that_read_source_from_disk(self):
        _, out_dir = run_scope(self.root, ["M\t" + f"{FRONTEND}/src/store/palette.js"])
        self.assertEqual(read_lines(os.path.join(out_dir, "readers.txt")), ["src/style.test.js"])

    def test_empty_change_list_is_full(self):
        self.assertFull([])

    def test_package_json_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/package.json"])

    def test_package_lock_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/package-lock.json"])

    def test_vitest_config_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/vitest.config.js"])

    def test_vite_config_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/vite.config.js"])

    def test_eslint_config_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/eslint.config.js"])

    def test_entry_point_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/main.js"])

    def test_global_stylesheet_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/style.css"])

    def test_token_stylesheet_under_styles_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/styles/md3/color.css"])

    def test_js_file_under_styles_is_full_because_tests_read_it_as_text(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/styles/md3/color.test.js"])

    def test_anchor_registry_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/anchors/registry.json"])

    def test_json_read_by_a_test_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/router/dashboard-segments.json"])

    def test_deleted_module_is_full(self):
        self.assertFull(["D\t" + f"{FRONTEND}/src/store/palette.js"])

    def test_renamed_module_is_full(self):
        self.assertFull(["R100\t" + f"{FRONTEND}/src/store/palette.js\t{FRONTEND}/src/store/colors.js"])

    def test_backend_change_in_the_same_pull_request_is_full(self):
        self.assertFull([
            "M\t" + f"{FRONTEND}/src/store/palette.js",
            "M\treposilite-backend/src/main/kotlin/X.kt",
        ])

    def test_workflow_change_in_the_same_pull_request_is_full(self):
        self.assertFull([
            "M\t" + f"{FRONTEND}/src/store/palette.js",
            "M\t.github/workflows/ci.yml",
        ])

    def test_frontend_module_path_outside_src_and_server_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/fake-api/fake-ingot-backend.js"])

    def test_frontend_prefix_look_alike_is_full(self):
        self.assertFull(["M\treposilite-frontend-tools/src/x.js"])

    def test_non_module_file_is_full(self):
        self.assertFull(["M\t" + f"{FRONTEND}/src/assets/logo.svg"])


class FrontendScopeFixtureTest(unittest.TestCase):
    """Cases that need a fixture without the readers, so the readers list is empty."""

    def test_related_run_without_any_reader_test_writes_an_empty_readers_list(self):
        with tempfile.TemporaryDirectory() as root:
            write(root, f"{FRONTEND}/src/a.js", "")
            write(root, f"{FRONTEND}/src/a.test.js", "")
            result, out_dir = run_scope(root, ["M\t" + f"{FRONTEND}/src/a.js"])
            self.assertEqual(result, "frontend_scope=related")
            self.assertEqual(read_lines(os.path.join(out_dir, "readers.txt")), [])


class FrontendJobInWorkflowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(CI_WORKFLOW, encoding="utf-8") as handle:
            cls.workflow = handle.read()
        match = re.search(r"^  coverage-frontend:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)", cls.workflow, re.S | re.M)
        cls.block = match.group(1) if match else ""

    def test_frontend_job_fetches_full_history_for_the_diff(self):
        self.assertIn("fetch-depth: 0", self.block)

    def test_frontend_job_decides_the_scope_with_the_script(self):
        self.assertIn("bash .github/scripts/frontend_scope.sh", self.block)

    def test_frontend_job_diffs_the_pull_request_against_the_merge_base(self):
        self.assertIn('git diff --name-status "$BASE...$HEAD"', self.block)

    def test_frontend_job_runs_full_on_push(self):
        self.assertIn('"$EVENT" = "pull_request"', self.block)

    def test_frontend_job_falls_back_to_full_when_detection_fails(self):
        self.assertIn('|| result="frontend_scope=full"', self.block)

    def test_related_run_uses_vitest_related_with_the_readers_appended(self):
        self.assertIn("npx vitest related", self.block)
        self.assertIn('"$SCOPE_DIR/readers.txt"', self.block)

    def test_zero_graph_matches_fall_back_to_the_full_suite(self):
        self.assertIn('if [ "$graph" != "0" ]', self.block)
        self.assertIn('echo "effective=related"', self.block)
        self.assertIn("npm run test:coverage", self.block)

    def test_partial_run_publishes_a_differently_named_artifact(self):
        self.assertIn("frontend-coverage-partial", self.block)
        self.assertIn("frontend-coverage'", self.block)

    def test_frontend_job_still_publishes_its_report_on_failure(self):
        self.assertIn("if: always()", self.block)


if __name__ == "__main__":
    unittest.main()
