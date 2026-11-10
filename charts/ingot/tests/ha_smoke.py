"""Two-replica smoke test driver for the Ingot Helm chart, run on kind by the ha-smoke job.

The module has two layers. The decision functions at the top take plain values and an injected clock, so
test_ha_smoke.py covers them without a cluster. The driver at the bottom drives Helm and kubectl, and reaches the
public HTTP API of the pods through a curl pod inside the cluster, because pod IPs are not routable from the runner.

Nothing here sleeps as an assertion: every wait is a bounded poll against a deadline computed from the clock.
"""

import base64
import re
import time
import xml.etree.ElementTree as ElementTree

LEASE_TTL_SECONDS = 60
"""The lease time-to-live of a cleanup run, see LEASE_DURATION in CleanupService."""

POLL_SECONDS = 5
"""The poll interval the takeover check allows on top of the time-to-live."""

SESSION_COOKIES = ("ingot_session", "ingot_refresh", "ingot_csrf")

NAMESPACE = "ingot-ha"
RELEASE = "ingot-ha"
S3_ENDPOINT = f"http://floci.{NAMESPACE}.svc.cluster.local:4566"
S3_BUCKET = "ingot-smoke"
S3_REGION = "us-east-1"
CURL_IMAGE = "curlimages/curl:8.23.0@sha256:3fb7e13e5d179a845c97d78204f3f1064d1907037f26a3ebf91710571151fd93"
"""The curl image of the in-cluster client and reader pods, pinned by tag and digest."""


class SmokeFailure(Exception):
    """A check that did not hold. The message names what was expected."""


class SystemClock:
    """The clock of the driver at runtime. Tests inject a fake with the same two methods."""

    def now(self):
        return time.monotonic()

    def sleep(self, seconds):
        time.sleep(seconds)


def wait_until(condition, *, clock, timeout, interval, describe):
    """Polls [condition] until it returns a truthy value and returns that value.

    The deadline is computed once from the clock. The last sleep ends at the deadline, and the condition is evaluated
    once more there, so a condition that becomes true just before the bound is not missed. Past the deadline the
    wait fails with [describe] in its message.
    """
    deadline = clock.now() + timeout
    while True:
        value = condition()
        if value:
            return value
        remaining = deadline - clock.now()
        if remaining <= 0:
            raise SmokeFailure(f"timed out after {timeout:g}s waiting for {describe}")
        clock.sleep(min(interval, remaining))


def failed_reads(lines, expected_sha256):
    """Counts the reads that were not a 200 with the artifact's bytes.

    Each line is "<http status> <sha256 of the body>", as the reader pod prints it. Zero is returned only when every
    read is a 200 whose body hashes to [expected_sha256]. A check without reads counts as one failure, because zero
    reads prove nothing.
    """
    if not lines:
        return 1
    failed = 0
    for line in lines:
        status, _, digest = line.strip().partition(" ")
        if status != "200" or digest != expected_sha256:
            failed += 1
    return failed


def missing_versions(metadata_xml, expected_versions):
    """The expected versions that maven-metadata.xml does not list."""
    try:
        root = ElementTree.fromstring(metadata_xml)
    except ElementTree.ParseError as error:
        raise SmokeFailure(f"maven-metadata.xml is not XML: {error}") from error
    listed = {element.text for element in root.iter("version")}
    return [version for version in expected_versions if version not in listed]


def takeover_deadline(killed_at, ttl_seconds, poll_seconds):
    """The latest moment a takeover may happen: the kill, plus the lease time-to-live, plus the poll interval."""
    return killed_at + ttl_seconds + poll_seconds


def takeover_in_window(taken_over_at, killed_at, ttl_seconds, poll_seconds):
    """Whether the takeover happened within the window. The bound is inclusive."""
    return taken_over_at <= takeover_deadline(killed_at, ttl_seconds, poll_seconds)


def check_run_observed(observed_running, policy_id):
    """Fails when the run finished before the driver saw it hold the lease, so the takeover was never exercised."""
    if not observed_running:
        raise SmokeFailure(
            f"the cleanup run of policy {policy_id} was not observed as running before it ended (missed observation), "
            "so the takeover of a killed run could not be checked"
        )


def parse_instance_ids(status):
    """The sorted identifiers of the instances in the cluster section of an instance status."""
    cluster = status.get("cluster") if isinstance(status, dict) else None
    if not isinstance(cluster, dict) or "instances" not in cluster:
        raise SmokeFailure("the instance status has no cluster section, so the cluster bus is not configured")
    return sorted(instance["id"] for instance in cluster["instances"])


def parse_setup_code(log_text):
    """The one-time setup code from the startup log, or None when the log names none."""
    matches = re.findall(r"setup code: (\S+)", log_text)
    return matches[-1] if matches else None


def parse_set_cookies(headers):
    """The values of the session cookies among the Set-Cookie headers, by cookie name."""
    cookies = {}
    for header in headers:
        pair = header.split(";", 1)[0].strip()
        name, sep, value = pair.partition("=")
        if sep and name in SESSION_COOKIES:
            cookies[name] = value
    return cookies


def single_completed_run(runs, policy_id):
    """The one recorded run of [policy_id]. Any other count fails, naming it."""
    recorded = [run for run in runs if run.get("policyId") == policy_id]
    if len(recorded) != 1:
        raise SmokeFailure(f"expected exactly one recorded run of policy {policy_id}, found {len(recorded)}")
    return recorded[0]


def parse_curl_output(output):
    """Splits the output of the in-cluster curl into (status, header lines, body bytes).

    The curl pod prints the status line, the header lines, a line with three dashes, and the body as base64, so that
    binary artifact bytes survive the exec unchanged.
    """
    head, _, encoded = output.partition("\n---\n")
    lines = head.split("\n")
    status = int(lines[0].strip())
    headers = [line for line in lines[1:] if line.strip()]
    body = base64.b64decode(encoded.strip()) if encoded.strip() else b""
    return status, headers, body
