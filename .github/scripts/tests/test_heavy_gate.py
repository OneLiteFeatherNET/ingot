"""Tests for .github/scripts/heavy_gate.py.

The script is pure: every test passes the event in and reads the decision out. No test reads the network, the clock or
the filesystem beyond its own temporary directory. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

_SPEC = importlib.util.spec_from_file_location("heavy_gate", os.path.join(SCRIPTS_DIR, "heavy_gate.py"))
heavy_gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(heavy_gate)

REPO = "OneLiteFeatherNET/ingot"
ALL = set(heavy_gate.HEAVY_JOBS)


def pull_request(labels=(), action="synchronize", head_ref="feature/x", head_repo=REPO):
    return heavy_gate.decide("pull_request", action, list(labels), head_ref, head_repo, REPO)


class DecideTest(unittest.TestCase):
    def test_no_label_requests_nothing_and_passes(self):
        decision = pull_request()
        self.assertEqual((), decision.jobs)
        self.assertTrue(decision.ok)
        self.assertIn("not requested", decision.reason)

    def test_unrelated_label_requests_nothing(self):
        self.assertEqual((), pull_request(["bug", "ci:red-main"]).jobs)

    def test_heavy_label_requests_every_job_as_selection(self):
        decision = pull_request(["ci:heavy"])
        self.assertEqual(ALL, set(decision.jobs))
        self.assertFalse(decision.full)

    def test_e2e_label_requests_only_e2e(self):
        self.assertEqual(("e2e",), pull_request(["ci:e2e"]).jobs)

    def test_images_label_also_requests_helm(self):
        self.assertEqual({"images", "helm"}, set(pull_request(["ci:images"]).jobs))

    def test_each_area_label_requests_its_job(self):
        expected = {"ci:integration": "integration", "ci:ha": "ha", "ci:search": "search", "ci:codeql": "codeql"}
        for label, job in expected.items():
            with self.subTest(label=label):
                self.assertEqual((job,), pull_request([label]).jobs)

    def test_two_area_labels_request_both_jobs(self):
        self.assertEqual({"e2e", "ha"}, set(pull_request(["ci:e2e", "ci:ha"]).jobs))

    def test_labelled_same_repository_push_runs_again(self):
        self.assertEqual(("ha",), pull_request(["ci:ha"], action="synchronize").jobs)

    def test_unlabeling_leaves_nothing_requested(self):
        decision = pull_request([], action="unlabeled")
        self.assertEqual((), decision.jobs)
        self.assertTrue(decision.ok)


class ForkTest(unittest.TestCase):
    def test_fork_push_after_label_does_not_rerun_and_fails_the_aggregate(self):
        decision = pull_request(["ci:heavy"], head_repo="someone/ingot")
        self.assertEqual((), decision.jobs)
        self.assertFalse(decision.ok)
        self.assertIn("re-add", decision.reason)

    def test_fork_push_without_label_passes(self):
        decision = pull_request([], head_repo="someone/ingot")
        self.assertTrue(decision.ok)

    def test_fork_labeled_event_runs_the_jobs(self):
        decision = pull_request(["ci:heavy"], action="labeled", head_repo="someone/ingot")
        self.assertEqual(ALL, set(decision.jobs))

    def test_fork_with_a_release_like_head_ref_is_not_trusted_as_release_without_label(self):
        decision = pull_request([], head_ref="release-please--branches--main", head_repo="someone/ingot")
        self.assertFalse(decision.ok)


class ReleaseTest(unittest.TestCase):
    def test_release_without_label_fails_with_instruction(self):
        decision = pull_request([], head_ref="release-please--branches--main")
        self.assertTrue(decision.release)
        self.assertFalse(decision.ok)
        self.assertEqual("run the heavy tests: add ci:heavy", decision.reason)

    def test_release_with_only_an_area_label_still_fails(self):
        decision = pull_request(["ci:e2e"], head_ref="release-please--branches--main")
        self.assertFalse(decision.ok)

    def test_release_with_label_runs_everything_in_full(self):
        decision = pull_request(["ci:heavy"], head_ref="release-please--branches--main")
        self.assertEqual(ALL, set(decision.jobs))
        self.assertTrue(decision.full)
        self.assertTrue(decision.release)
        self.assertTrue(decision.ok)

    def test_release_runs_again_on_a_new_head_commit(self):
        decision = pull_request(["ci:heavy"], action="synchronize", head_ref="release-please--branches--main")
        self.assertEqual(ALL, set(decision.jobs))

    def test_prefix_must_be_at_the_start(self):
        decision = pull_request([], head_ref="feature/release-please--x")
        self.assertFalse(decision.release)


class FailSafeTest(unittest.TestCase):
    def test_unknown_event_runs_everything_in_full_and_fails(self):
        decision = heavy_gate.decide("push", "", [], "main", REPO, REPO)
        self.assertEqual(ALL, set(decision.jobs))
        self.assertTrue(decision.full)
        self.assertFalse(decision.ok)

    def test_unknown_pull_request_action_fails_safe(self):
        decision = heavy_gate.decide("pull_request", "closed", [], "x", REPO, REPO)
        self.assertFalse(decision.ok)
        self.assertEqual(ALL, set(decision.jobs))

    def test_missing_repository_fails_safe(self):
        decision = heavy_gate.decide("pull_request", "opened", [], "x", "", REPO)
        self.assertFalse(decision.ok)

    def test_unparsable_labels_exit_one_and_write_the_failsafe_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "output")
            code = heavy_gate.main(["decide", "--event-name", "pull_request", "--action", "opened",
                                    "--labels", "not json", "--head-repo", REPO, "--repo", REPO, "--output", out])
            with open(out, encoding="utf-8") as handle:
                text = handle.read()
        self.assertEqual(1, code)
        self.assertIn("ok=false", text)
        self.assertIn("full=true", text)
        self.assertIn("images=true", text)

    def test_labels_that_are_not_a_list_of_strings_fail_safe(self):
        with contextlib.redirect_stdout(io.StringIO()):
            code = heavy_gate.main(["decide", "--event-name", "pull_request", "--action", "opened",
                                    "--labels", '{"a": 1}', "--head-repo", REPO, "--repo", REPO])
        self.assertEqual(1, code)


class DispatchTest(unittest.TestCase):
    def test_dispatch_on_main_runs_everything_in_full(self):
        decision = heavy_gate.decide("workflow_dispatch", "", [], "", "", "", ref="refs/heads/main")
        self.assertEqual(ALL, set(decision.jobs))
        self.assertTrue(decision.full)
        self.assertTrue(decision.ok)

    def test_dispatch_on_another_branch_runs_nothing_and_fails(self):
        decision = heavy_gate.decide("workflow_dispatch", "", [], "", "", "", ref="refs/heads/feature/x")
        self.assertEqual((), decision.jobs)
        self.assertFalse(decision.ok)
        self.assertIn("main", decision.reason)

    def test_dispatch_without_a_ref_runs_nothing_and_fails(self):
        self.assertFalse(heavy_gate.decide("workflow_dispatch", "", [], "", "", "").ok)

    def test_dispatch_command_line_takes_the_ref(self):
        with contextlib.redirect_stdout(io.StringIO()) as buffer:
            code = heavy_gate.main(["decide", "--event-name", "workflow_dispatch", "--ref", "refs/heads/main"])
        self.assertEqual(0, code)
        self.assertIn("full=true", buffer.getvalue())


class MergeGroupTest(unittest.TestCase):
    """A merge group has no labels: the changed paths, read through ci_scope.sh, decide what runs."""

    NONE = {"image": "false", "chart": "false", "e2e": "false"}

    def merge_group(self, **flags):
        return heavy_gate.decide("merge_group", "checks_requested", [], "", "", REPO, scope={**self.NONE, **flags})

    def test_untouched_scope_runs_nothing_and_passes(self):
        decision = self.merge_group()
        self.assertEqual((), decision.jobs)
        self.assertTrue(decision.ok)

    def test_image_change_runs_the_images_and_the_chart_install(self):
        self.assertEqual({"images", "helm"}, set(self.merge_group(image="true").jobs))

    def test_chart_change_runs_only_the_chart(self):
        self.assertEqual(("helm",), self.merge_group(chart="true").jobs)

    def test_source_change_runs_only_the_end_to_end_tests(self):
        self.assertEqual(("e2e",), self.merge_group(e2e="true").jobs)

    def test_a_merge_group_never_starts_the_jobs_outside_the_scope_flags(self):
        jobs = set(self.merge_group(image="true", chart="true", e2e="true").jobs)
        self.assertEqual({"images", "helm", "e2e"}, jobs)

    def test_unknown_scope_runs_every_scoped_job_and_passes(self):
        decision = heavy_gate.decide("merge_group", "checks_requested", [], "", "", REPO, scope=None)
        self.assertEqual({"images", "helm", "e2e"}, set(decision.jobs))
        self.assertTrue(decision.ok)
        self.assertFalse(decision.full)

    def test_command_line_reads_the_scope_file_and_null_labels(self):
        with tempfile.TemporaryDirectory() as tmp:
            scope = os.path.join(tmp, "scope.txt")
            with open(scope, "w", encoding="utf-8") as handle:
                handle.write("backend=true\nimage=false\nchart=true\ne2e=false\nunit=false\n")
            with contextlib.redirect_stdout(io.StringIO()) as buffer:
                code = heavy_gate.main(["decide", "--event-name", "merge_group", "--action", "checks_requested",
                                        "--labels", "null", "--repo", REPO, "--scope-file", scope])
        self.assertEqual(0, code)
        self.assertIn("jobs=helm", buffer.getvalue())
        self.assertIn("ok=true", buffer.getvalue())

    def test_command_line_without_a_scope_file_runs_every_scoped_job(self):
        with tempfile.TemporaryDirectory() as tmp:
            with contextlib.redirect_stdout(io.StringIO()) as buffer:
                code = heavy_gate.main(["decide", "--event-name", "merge_group", "--labels", "[]", "--repo", REPO,
                                        "--scope-file", os.path.join(tmp, "missing.txt")])
        self.assertEqual(0, code)
        self.assertIn("jobs=images,e2e,helm", buffer.getvalue())


class RenderTest(unittest.TestCase):
    def test_render_has_a_flag_per_job(self):
        lines = heavy_gate.render_decision(pull_request(["ci:e2e"]))
        self.assertIn("e2e=true", lines)
        self.assertIn("images=false", lines)
        self.assertIn("jobs=e2e", lines)
        self.assertIn("requested=true", lines)
        self.assertIn("full=false", lines)

    def test_main_prints_to_stdout_without_an_output_file(self):
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = heavy_gate.main(["decide", "--event-name", "pull_request", "--action", "opened",
                                    "--labels", '["ci:ha"]', "--head-repo", REPO, "--repo", REPO])
        self.assertEqual(0, code)
        self.assertIn("ha=true", buffer.getvalue())


def results(**overrides):
    base = {job: "skipped" for job in heavy_gate.HEAVY_JOBS}
    base.update(overrides)
    return base


class VerdictTest(unittest.TestCase):
    def test_nothing_requested_and_everything_skipped_passes(self):
        passed, message = heavy_gate.verdict(pull_request(), "success", results())
        self.assertTrue(passed)
        self.assertIn("not requested", message)

    def test_requested_job_that_succeeded_passes(self):
        passed, _ = heavy_gate.verdict(pull_request(["ci:e2e"]), "success", results(e2e="success"))
        self.assertTrue(passed)

    def test_requested_job_that_was_skipped_fails(self):
        passed, message = heavy_gate.verdict(pull_request(["ci:e2e"]), "success", results())
        self.assertFalse(passed)
        self.assertIn("e2e: skipped", message)

    def test_requested_job_that_failed_fails(self):
        passed, _ = heavy_gate.verdict(pull_request(["ci:e2e"]), "success", results(e2e="failure"))
        self.assertFalse(passed)

    def test_job_that_was_not_requested_but_failed_fails(self):
        passed, _ = heavy_gate.verdict(pull_request(), "success", results(images="failure"))
        self.assertFalse(passed)

    def test_cancelled_job_fails(self):
        passed, _ = heavy_gate.verdict(pull_request(["ci:ha"]), "success", results(ha="cancelled"))
        self.assertFalse(passed)

    def test_scope_failure_fails_even_when_every_job_is_skipped(self):
        passed, _ = heavy_gate.verdict(pull_request(), "failure", results())
        self.assertFalse(passed)

    def test_decision_not_ok_fails_even_when_every_job_is_skipped(self):
        decision = pull_request([], head_ref="release-please--branches--main")
        passed, message = heavy_gate.verdict(decision, "success", results())
        self.assertFalse(passed)
        self.assertIn("add ci:heavy", message)

    def test_release_passes_only_when_every_job_succeeded(self):
        decision = pull_request(["ci:heavy"], head_ref="release-please--branches--main")
        every = {job: "success" for job in heavy_gate.HEAVY_JOBS}
        self.assertTrue(heavy_gate.verdict(decision, "success", every)[0])
        partial = dict(every, helm="skipped")
        self.assertFalse(heavy_gate.verdict(decision, "success", partial)[0])

    def test_job_that_is_not_wired_into_the_workflow_yet_is_ignored(self):
        passed, _ = heavy_gate.verdict(pull_request(["ci:ha"]), "success", {})
        self.assertTrue(passed)

    def test_results_in_the_shape_of_the_needs_context_are_read(self):
        needs = {"gate": {"result": "success"}, "ha": {"result": "failure", "outputs": {}}}
        passed, message = heavy_gate.verdict(pull_request(["ci:ha"]), "success", needs)
        self.assertFalse(passed)
        self.assertIn("ha: failure", message)


class VerdictCliTest(unittest.TestCase):
    def run_cli(self, *argv):
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = heavy_gate.main(["verdict", *argv])
        return code, buffer.getvalue()

    def test_cli_passes_for_a_green_labelled_run(self):
        code, out = self.run_cli("--gate-result", "success", "--results", json.dumps(results(e2e="success")),
                                 "--jobs", "e2e", "--ok", "true", "--reason", "x")
        self.assertEqual(0, code)
        self.assertIn("e2e", out)

    def test_cli_fails_for_unusable_results(self):
        code, _ = self.run_cli("--gate-result", "success", "--results", "[1]", "--ok", "true")
        self.assertEqual(1, code)


if __name__ == "__main__":
    unittest.main()
