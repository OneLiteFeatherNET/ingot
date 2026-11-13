"""Decision logic of the two-replica smoke test, without a cluster.

The driver in ha_smoke.py drives Helm, kubectl and the public HTTP API of real pods on kind. The decisions that can be
wrong are plain functions of their inputs and an injected clock, so they are tested here: no socket, no subprocess,
no system clock and no sleep. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import unittest

from ha_smoke import (
    CURL_SCRIPT,
    run_checks,
    READER_SCRIPT,
    READ_TIMEOUT_SECONDS,
    generate_pom_request,
    BOOTSTRAP_SECRET,
    BOOTSTRAP_USER,
    LEASE_TTL_SECONDS,
    POLL_SECONDS,
    SmokeFailure,
    Smoke,
    ROLLING_GROUP,
    ROLLING_PATH,
    artifact_path,
    failed_reads,
    read_outcomes,
    finished_before_kill,
    iso_to_epoch,
    missing_versions,
    parse_curl_output,
    parse_instance_ids,
    parse_set_cookies,
    parse_setup_code,
    takeover_deadline,
    takeover_in_window,
    takeover_run,
    wait_until,
)

EXPECTED_SHA = "a" * 64


class FakeClock:
    """A clock that moves only when the test or the code under test sleeps on it."""

    def __init__(self, start=1000.0):
        self.time = start
        self.sleeps = []

    def now(self):
        return self.time

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.time += seconds

    def wall(self):
        return self.time


class WaitUntilTest(unittest.TestCase):

    def setUp(self):
        self.clock = FakeClock()

    def test_wait_returns_the_value_of_a_condition_that_holds_at_once(self):
        result = wait_until(lambda: "ready", clock=self.clock, timeout=30, interval=2, describe="the pods")
        self.assertEqual("ready", result, "a condition that holds returns its value")
        self.assertEqual([], self.clock.sleeps, "a condition that holds at once does not sleep")

    def test_wait_fails_with_the_condition_name_once_the_deadline_has_passed(self):
        with self.assertRaises(SmokeFailure) as caught:
            wait_until(lambda: None, clock=self.clock, timeout=30, interval=2, describe="both replicas ready")
        self.assertIn("both replicas ready", str(caught.exception), "the timeout must name the condition it waited for")
        self.assertIn("30", str(caught.exception), "the timeout must name the bound it waited for")

    def test_wait_never_sleeps_past_the_deadline(self):
        with self.assertRaises(SmokeFailure):
            wait_until(lambda: False, clock=self.clock, timeout=5, interval=2, describe="the status")
        self.assertLessEqual(self.clock.time, 1005.0, "the last sleep must end at the deadline, not after it")

    def test_wait_checks_the_condition_again_at_the_deadline(self):
        checks = []

        def condition():
            checks.append(self.clock.now())
            return self.clock.now() >= 1005.0 and "late"

        self.assertEqual("late", wait_until(condition, clock=self.clock, timeout=5, interval=2, describe="x"))
        self.assertEqual(1005.0, checks[-1], "the condition is evaluated once more exactly at the deadline")


class FailedReadsTest(unittest.TestCase):

    def test_no_failed_reads_when_every_read_is_200_with_the_expected_bytes(self):
        lines = [f"200 {EXPECTED_SHA}"] * 25
        self.assertEqual(0, failed_reads(lines, EXPECTED_SHA), "every read is a 200 with the artifact's bytes")

    def test_a_server_error_counts_as_a_failed_read(self):
        lines = [f"200 {EXPECTED_SHA}", f"503 {EXPECTED_SHA}"]
        self.assertEqual(1, failed_reads(lines, EXPECTED_SHA), "a 503 during the rollout is a failed read")

    def test_a_200_with_other_bytes_counts_as_a_failed_read(self):
        lines = ["200 " + "b" * 64]
        self.assertEqual(1, failed_reads(lines, EXPECTED_SHA), "a 200 that returns other bytes is not the artifact")

    def test_no_reads_at_all_counts_as_a_failed_read(self):
        self.assertEqual(1, failed_reads([], EXPECTED_SHA), "a check without reads proves nothing and must fail")


class RollingArtifactTest(unittest.TestCase):

    def test_the_reader_reads_the_path_the_rolling_check_deploys(self):
        self.assertEqual(ROLLING_PATH, artifact_path(ROLLING_GROUP, "rolling", "1.0.0"),
                         "the reader must read the artifact the check deployed, or every read is a 404")


class GeneratePomRequestTest(unittest.TestCase):

    def test_the_request_targets_the_pom_whose_parent_holds_the_metadata(self):
        path, _ = generate_pom_request("net/onelitefeather/smoke/deploy", "deploy", "1.0.1")
        self.assertEqual("/api/maven/generate/pom/releases/net/onelitefeather/smoke/deploy/deploy/1.0.1/deploy-1.0.1.pom", path,
                         "the server appends the version to the metadata two levels above the pom it generates")

    def test_the_body_names_the_coordinates_with_a_dotted_group(self):
        _, body = generate_pom_request("net/onelitefeather/smoke/deploy", "deploy", "1.0.1")
        self.assertEqual({"groupId": "net.onelitefeather.smoke.deploy", "artifactId": "deploy", "version": "1.0.1"}, body,
                         "the generated pom and the metadata carry the coordinates from the body")


class ReaderScriptTest(unittest.TestCase):

    def test_a_read_without_an_answer_names_the_curl_exit_code(self):
        self.assertIn('code="000 curl-$?"', READER_SCRIPT,
                      "a read that got no answer must tell a timeout from a refused connection in the failure message")

    def test_a_slow_answer_is_not_counted_as_unavailable(self):
        self.assertIn(f"--max-time {READ_TIMEOUT_SECONDS}", READER_SCRIPT, "the reader waits as long as the timeout says")
        self.assertGreaterEqual(READ_TIMEOUT_SECONDS, 30, "the first read on a fresh replica may take seconds")


class ReadOutcomesTest(unittest.TestCase):

    def test_outcomes_are_counted_most_frequent_first(self):
        lines = ["401 none", f"200 {EXPECTED_SHA}", "401 none", "000 none", "401 none"]
        self.assertEqual("3x 401 none, 1x 200 " + EXPECTED_SHA + ", 1x 000 none", read_outcomes(lines),
                         "a failed rollout check names what the reads answered, so the log shows the cause")

    def test_outcomes_are_limited_to_the_most_frequent(self):
        lines = [f"{500 + index} none" for index in range(10)]
        self.assertEqual(5, read_outcomes(lines, limit=5).count("x "), "only the most frequent outcomes are listed")


class MissingVersionsTest(unittest.TestCase):

    METADATA = """<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>net.onelitefeather.smoke</groupId>
  <artifactId>deploy</artifactId>
  <versioning>
    <versions>
      <version>1.0.0</version>
    </versions>
  </versioning>
</metadata>
"""

    def test_a_version_missing_from_the_metadata_is_reported(self):
        self.assertEqual(["1.0.1"], missing_versions(self.METADATA, ["1.0.0", "1.0.1"]),
                         "a version that a concurrent deploy lost must be named")

    def test_no_version_is_missing_when_the_metadata_lists_all_of_them(self):
        self.assertEqual([], missing_versions(self.METADATA, ["1.0.0"]), "all expected versions are listed")

    def test_unparsable_metadata_fails_with_a_message(self):
        with self.assertRaises(SmokeFailure):
            missing_versions("<metadata>", ["1.0.0"])


class TakeoverWindowTest(unittest.TestCase):

    def test_the_window_is_the_lease_time_to_live_plus_the_poll_interval(self):
        self.assertEqual(1000.0 + LEASE_TTL_SECONDS + POLL_SECONDS, takeover_deadline(1000.0, LEASE_TTL_SECONDS, POLL_SECONDS),
                         "the deadline is the kill time plus the time-to-live plus the poll interval")

    def test_a_takeover_exactly_at_the_deadline_is_in_the_window(self):
        self.assertTrue(takeover_in_window(1065.0, 1000.0, LEASE_TTL_SECONDS, POLL_SECONDS),
                        "the bound is inclusive")

    def test_a_takeover_after_the_deadline_is_outside_the_window(self):
        self.assertFalse(takeover_in_window(1065.5, 1000.0, LEASE_TTL_SECONDS, POLL_SECONDS),
                         "a takeover later than the window fails the check")


class FakeKubectl:
    """Records the kubectl calls of the driver instead of running them."""

    def __init__(self):
        self.calls = []

    def run(self, args, **kwargs):
        self.calls.append(list(args))


class FakeHeldRun:
    """The held lease request of the driver, which the test can inspect after the attempt."""

    def __init__(self):
        self.killed = False
        self.waited = False

    def kill(self):
        self.killed = True

    def wait(self):
        self.waited = True


class FakeHttp:
    def __init__(self, held):
        self.held = held

    def start(self, method, url, *, headers=()):
        return self.held


class ScriptedSmoke(Smoke):
    """A driver whose replicas, uploads and run probes are scripted, so one lease attempt runs without a cluster."""

    def __init__(self, probe_outcomes):
        self.lines = []
        super().__init__(FakeKubectl(), FakeClock(), out=self.lines.append)
        self.held = FakeHeldRun()
        self.http = FakeHttp(self.held)
        self.probe_outcomes = list(probe_outcomes)

    def pods(self, expected=2):
        return {"ha-0": "10.0.0.1", "ha-1": "10.0.0.2"}

    def deploy(self, ip, group, artifact, version, body):
        pass

    def probe_run(self, ip):
        return self.probe_outcomes.pop(0)


class LeaseTakeoverAttemptTest(unittest.TestCase):

    def test_a_run_not_observed_as_running_loses_the_race_instead_of_failing_the_check(self):
        smoke = ScriptedSmoke(probe_outcomes=["ran"])
        self.assertFalse(smoke.lease_takeover_attempt(1),
                         "a run that finished before the probe saw it hold the lease is a lost race")
        self.assertTrue(smoke.held.killed and smoke.held.waited,
                        "the held request is killed and reaped on the way out")
        self.assertEqual([], smoke.kubectl.calls, "no pod is deleted when the run was not observed")
        self.assertTrue(any("not observed" in line for line in smoke.lines),
                        "the attempt logs the missed observation before it starts over")


class InstanceStatusTest(unittest.TestCase):

    def test_two_instances_are_listed_by_their_identifiers(self):
        status = {"cluster": {"instances": [{"id": "ingot-ha-0"}, {"id": "ingot-ha-1"}]}}
        self.assertEqual(["ingot-ha-0", "ingot-ha-1"], parse_instance_ids(status),
                         "both replicas must be listed in the cluster section")

    def test_a_status_without_a_cluster_section_fails_naming_the_section(self):
        with self.assertRaises(SmokeFailure) as caught:
            parse_instance_ids({"version": "2.6.0"})
        self.assertIn("cluster", str(caught.exception), "the failure must name the missing cluster section")


class SetupCodeTest(unittest.TestCase):

    def test_the_setup_code_is_read_from_the_log_line(self):
        log = "No user exists yet.\n$ setup code: K7Q-93XT\nlater line\n"
        self.assertEqual("K7Q-93XT", parse_setup_code(log), "the code follows the marker in the startup log")

    def test_a_log_without_a_setup_code_yields_none(self):
        self.assertIsNone(parse_setup_code("Server started\n"), "no code means the caller must fail with diagnostics")


class SetCookieTest(unittest.TestCase):

    def test_cookie_values_are_read_by_name_ignoring_attributes(self):
        headers = [
            "ingot_session=s1; Path=/; HttpOnly",
            "ingot_refresh=r1; Path=/api/auth; HttpOnly; SameSite=Strict",
            "ingot_csrf=c1; Path=/",
        ]
        self.assertEqual({"ingot_session": "s1", "ingot_refresh": "r1", "ingot_csrf": "c1"}, parse_set_cookies(headers),
                         "the attributes after the first segment are not part of the value")


class RawSetCookieTest(unittest.TestCase):

    def test_set_cookie_header_lines_are_read_with_their_prefix(self):
        headers = ["HTTP/1.1 200 OK", "Set-Cookie: ingot_refresh=r9; Path=/api/auth; HttpOnly"]
        self.assertEqual({"ingot_refresh": "r9"}, parse_set_cookies(headers),
                         "the header lines of a curl response carry the Set-Cookie prefix")


class BootstrapTokenTest(unittest.TestCase):

    def test_the_driver_authenticates_with_the_token_the_values_file_creates(self):
        import test_ha_dependencies
        options = next(entry["value"] for entry in test_ha_dependencies.load_values()["env"]
                       if entry["name"] == "REPOSILITE_OPTS")
        self.assertIn(f"--token {BOOTSTRAP_USER}:{BOOTSTRAP_SECRET}", options,
                      "the bootstrap token in the values must be the one the driver sends")


KILLED_AT = iso_to_epoch("2026-11-10T10:00:30Z")


def run(run_id, started, finished=None, policy="smoke"):
    """A run as the cleanup endpoint lists it. Runs are recorded only once they have finished."""
    record = {"policyId": policy, "id": run_id, "startedAt": started}
    if finished is not None:
        record["finishedAt"] = finished
    return record


class TakeoverRunTest(unittest.TestCase):

    def test_the_run_started_after_the_kill_is_the_takeover(self):
        runs = [run("r1", "2026-11-10T10:00:10Z", "2026-11-10T10:00:12Z"),
                run("r2", "2026-11-10T10:00:40Z", "2026-11-10T10:00:41Z")]
        self.assertEqual("r2", takeover_run(runs, "smoke", KILLED_AT)["id"],
                         "the run that started after the kill is the takeover")

    def test_a_run_started_before_the_kill_is_ignored(self):
        runs = [run("r1", "2026-11-10T10:00:10Z", "2026-11-10T10:00:12Z")]
        with self.assertRaises(SmokeFailure) as caught:
            takeover_run(runs, "smoke", KILLED_AT)
        self.assertIn("found 0", str(caught.exception), "a run from before the kill is not the takeover")

    def test_a_run_started_at_the_kill_instant_counts_as_after_it(self):
        runs = [run("r2", "2026-11-10T10:00:30Z", "2026-11-10T10:00:31Z")]
        self.assertEqual("r2", takeover_run(runs, "smoke", KILLED_AT)["id"], "the bound is inclusive")

    def test_runs_of_other_policies_are_ignored(self):
        runs = [run("r2", "2026-11-10T10:00:40Z", policy="other")]
        with self.assertRaises(SmokeFailure):
            takeover_run(runs, "smoke", KILLED_AT)

    def test_no_run_after_the_kill_fails_naming_the_count(self):
        with self.assertRaises(SmokeFailure) as caught:
            takeover_run([], "smoke", KILLED_AT)
        self.assertIn("exactly one recorded run of policy smoke after the kill, found 0", str(caught.exception),
                      "the failure must name the policy, the kill and the count")

    def test_two_runs_after_the_kill_fail_naming_the_count(self):
        runs = [run("r2", "2026-11-10T10:00:40Z"), run("r3", "2026-11-10T10:00:50Z")]
        with self.assertRaises(SmokeFailure) as caught:
            takeover_run(runs, "smoke", KILLED_AT)
        self.assertIn("found 2", str(caught.exception), "the failure must name the count of runs after the kill")


class FinishedBeforeKillTest(unittest.TestCase):

    def test_a_run_finished_before_the_kill_is_detected(self):
        runs = [run("r1", "2026-11-10T10:00:10Z", "2026-11-10T10:00:12Z")]
        self.assertTrue(finished_before_kill(runs, "smoke", KILLED_AT),
                        "a run that ended before the kill means the race was lost, the kill found no lease to take over")

    def test_a_run_started_before_and_finished_after_the_kill_is_not_detected(self):
        runs = [run("r1", "2026-11-10T10:00:10Z", "2026-11-10T10:00:45Z")]
        self.assertFalse(finished_before_kill(runs, "smoke", KILLED_AT),
                         "a run that was still going at the kill is the run the takeover exercises")

    def test_only_a_run_started_after_the_kill_is_not_detected(self):
        runs = [run("r2", "2026-11-10T10:00:40Z", "2026-11-10T10:00:41Z")]
        self.assertFalse(finished_before_kill(runs, "smoke", KILLED_AT),
                         "the takeover run itself is not a run that finished before the kill")

    def test_a_finished_run_of_another_policy_is_not_detected(self):
        runs = [run("r1", "2026-11-10T10:00:10Z", "2026-11-10T10:00:12Z", policy="other")]
        self.assertFalse(finished_before_kill(runs, "smoke", KILLED_AT), "only runs of the policy count")

    def test_a_run_of_an_earlier_attempt_is_not_detected(self):
        runs = [run("r1", "2026-11-10T10:00:10Z", "2026-11-10T10:00:12Z")]
        since = iso_to_epoch("2026-11-10T10:00:20Z")
        self.assertFalse(finished_before_kill(runs, "smoke", KILLED_AT, since=since),
                         "a run that started before the current attempt belongs to an earlier attempt")


class CurlScriptTest(unittest.TestCase):

    def test_each_request_keeps_its_files_in_a_directory_of_its_own(self):
        self.assertIn("mktemp -d", CURL_SCRIPT, "two requests at the same moment run in one client pod")
        for shared in ("/tmp/in", "/tmp/out", "/tmp/hdr"):
            self.assertNotIn(shared, CURL_SCRIPT, f"{shared} is shared, so a concurrent request empties the body of another")


class RunChecksTest(unittest.TestCase):

    def test_a_failed_check_does_not_stop_the_checks_after_it(self):
        ran = []

        def failing():
            ran.append("first")
            raise SmokeFailure("no answer")

        failures = run_checks([("check 1", failing), ("check 2", lambda: ran.append("second"))])
        self.assertEqual(["first", "second"], ran, "every check runs, so one run shows every failure")
        self.assertEqual(["check 1: no answer"], failures, "the failure names its check and its cause")

    def test_an_unexpected_error_counts_as_a_failure_of_its_check(self):
        def broken():
            raise ValueError("not JSON")

        self.assertEqual(["check 4: ValueError: not JSON"], run_checks([("check 4", broken)]),
                         "an error outside the expected failures still fails its check")

    def test_passing_checks_report_no_failure(self):
        self.assertEqual([], run_checks([("check 5", lambda: None)]), "no failure when every check passes")


class CurlOutputTest(unittest.TestCase):

    def test_status_headers_and_body_are_split_from_the_exec_output(self):
        output = "200\nContent-Type: text/plain\nETag: \"x\"\n---\naGVsbG8=\n"
        status, headers, body = parse_curl_output(output)
        self.assertEqual(200, status, "the first line is the HTTP status")
        self.assertEqual(["Content-Type: text/plain", 'ETag: "x"'], headers, "the header lines come before the separator")
        self.assertEqual(b"hello", body, "the body is base64 encoded so that binary bytes survive the exec")

    def test_an_empty_body_is_read_as_empty_bytes(self):
        self.assertEqual(b"", parse_curl_output("401\n\n---\n")[2], "a response without a body has no bytes")


if __name__ == "__main__":
    unittest.main()
