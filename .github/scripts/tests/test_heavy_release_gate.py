"""Tests for the release gate of .github/scripts/heavy_gate.py, driven by a recorded pull request event.

The fixture is the relevant part of the `pull_request` payload of a release-please pull request. The helper reads the
same fields that `heavy.yml` hands to the script. No test reads the network or the clock. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import copy
import importlib.util
import io
import json
import os
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
FIXTURE = os.path.join(TESTS_DIR, "fixtures", "heavy", "release_pull_request_event.json")

_SPEC = importlib.util.spec_from_file_location("heavy_gate", os.path.join(SCRIPTS_DIR, "heavy_gate.py"))
heavy_gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(heavy_gate)

ALL = set(heavy_gate.HEAVY_JOBS)


def load_event():
    with open(FIXTURE, encoding="utf-8") as handle:
        return json.load(handle)


def label_names(event):
    return [label["name"] for label in event["pull_request"]["labels"]]


def decide_from(event):
    """Reads the event the way the workflow does: labels, head ref, head repository and repository."""
    pull_request = event["pull_request"]
    return heavy_gate.decide(
        "pull_request",
        event["action"],
        label_names(event),
        pull_request["head"]["ref"],
        pull_request["head"]["repo"]["full_name"],
        event["repository"]["full_name"],
    )


def without_label(event, name):
    event = copy.deepcopy(event)
    labels = event["pull_request"]["labels"]
    labels[:] = [label for label in labels if label["name"] != name]
    return event


def green_results():
    return {job: "success" for job in heavy_gate.HEAVY_JOBS}


class ReleaseGateTest(unittest.TestCase):
    def test_release_event_with_the_label_runs_every_job_in_full(self):
        decision = decide_from(load_event())
        self.assertEqual(ALL, set(decision.jobs))
        self.assertTrue(decision.full)
        self.assertTrue(decision.release)

    def test_release_event_with_a_green_full_run_passes(self):
        decision = decide_from(load_event())
        self.assertTrue(heavy_gate.verdict(decision, "success", green_results())[0])

    def test_release_event_without_the_label_fails_with_the_instruction(self):
        decision = decide_from(without_label(load_event(), "ci:heavy"))
        passed, message = heavy_gate.verdict(decision, "success", {job: "skipped" for job in ALL})
        self.assertFalse(passed)
        self.assertEqual("run the heavy tests: add ci:heavy", message)

    def test_release_event_with_one_job_skipped_fails(self):
        results = green_results()
        results["integration"] = "skipped"
        self.assertFalse(heavy_gate.verdict(decide_from(load_event()), "success", results)[0])

    def test_release_event_with_one_job_failed_fails(self):
        results = green_results()
        results["images"] = "failure"
        self.assertFalse(heavy_gate.verdict(decide_from(load_event()), "success", results)[0])

    def test_release_event_with_a_failed_scope_step_fails(self):
        self.assertFalse(heavy_gate.verdict(decide_from(load_event()), "failure", green_results())[0])

    def test_a_new_head_commit_decides_again_and_runs_every_job(self):
        event = load_event()
        event["pull_request"]["head"]["sha"] = "2222222222222222222222222222222222222222"
        self.assertEqual(ALL, set(decide_from(event).jobs))

    def test_command_line_writes_the_release_outputs(self):
        event = load_event()
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = heavy_gate.main([
                "decide", "--event-name", "pull_request", "--action", event["action"],
                "--labels", json.dumps(label_names(event)),
                "--head-ref", event["pull_request"]["head"]["ref"],
                "--head-repo", event["pull_request"]["head"]["repo"]["full_name"],
                "--repo", event["repository"]["full_name"],
            ])
        lines = buffer.getvalue().splitlines()
        self.assertEqual(0, code)
        self.assertIn("release=true", lines)
        self.assertIn("full=true", lines)
        self.assertIn("ok=true", lines)


if __name__ == "__main__":
    unittest.main()
