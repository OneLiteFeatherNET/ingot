"""Two-replica smoke test driver for the Ingot Helm chart, run on kind by the ha-smoke job.

The module has two layers. The decision functions at the top take plain values and an injected clock, so
test_ha_smoke.py covers them without a cluster. The driver at the bottom drives Helm and kubectl, and reaches the
public HTTP API of the pods through a curl pod inside the cluster, because pod IPs are not routable from the runner.

Nothing here sleeps as an assertion: every wait is a bounded poll against a deadline computed from the clock.
"""

import argparse
import base64
import concurrent.futures
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree
from datetime import datetime

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
BOOTSTRAP_USER = "smoke"
BOOTSTRAP_SECRET = "smoke-ci-only-secret"
"""The bootstrap token of the values file, sent as Basic authentication. Throwaway CI value."""
CURL_IMAGE = "curlimages/curl:8.23.0@sha256:3fb7e13e5d179a845c97d78204f3f1064d1907037f26a3ebf91710571151fd93"
"""The curl image of the in-cluster client and reader pods, pinned by tag and digest."""


class SmokeFailure(Exception):
    """A check that did not hold. The message names what was expected."""


class SystemClock:
    """The clock of the driver at runtime. Tests inject a fake with the same two methods."""

    def now(self):
        return time.monotonic()

    def wall(self):
        return time.time()

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


def read_outcomes(lines, limit=5):
    """Names the most frequent "<status> <sha256>" lines with their counts, most frequent first, for a failure message."""
    counts = {}
    for line in lines:
        key = line.strip()
        counts[key] = counts.get(key, 0) + 1
    ranked = sorted(counts.items(), key=lambda item: -item[1])[:limit]
    return ", ".join(f"{count}x {key}" for key, count in ranked)


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
    """The values of the session cookies among the Set-Cookie header lines, by cookie name."""
    cookies = {}
    for header in headers:
        if header.lower().startswith("set-cookie:"):
            header = header.split(":", 1)[1]
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


# ---------------------------------------------------------------------------------------------------------------------
# The driver. Everything below talks to Helm, kubectl and the pods. The decisions above it stay pure.
# ---------------------------------------------------------------------------------------------------------------------

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
CHART_DIR = os.path.dirname(TESTS_DIR)
HA_DIR = os.path.join(TESTS_DIR, "ha")
DEPENDENCIES = os.path.join(HA_DIR, "dependencies.yaml")
VALUES = os.path.join(HA_DIR, "values.yaml")

SERVER_SELECTOR = f"app.kubernetes.io/name=ingot,app.kubernetes.io/instance={RELEASE}"
SERVICE_URL = f"http://{RELEASE}.{NAMESPACE}.svc.cluster.local:8080"
POLICY = "smoke"
CLIENT = "ha-client"
READER = "ha-reader"
ROLLING_PATH = "net/onelitefeather/smoke/rolling/1.0.0/rolling-1.0.0.pom"
LEASE_GROUP = "net/onelitefeather/smoke/lease"
LEASE_VERSIONS = 80
"""Versions seeded for the lease check. A run that keeps all but the newest one deletes this many less one."""
DEPLOY_GROUP = "net/onelitefeather/smoke/deploy"
DEPLOY_VERSIONS = ("1.0.0", "1.0.1")
ADMIN = ("smoke-admin", "smoke-ci-only-password")
"""The first administrator of the smoke instance. Throwaway CI value."""

READER_SCRIPT = (
    'i=0; while [ ! -e /tmp/stop ] && [ "$i" -lt 20000 ]; do rm -f /tmp/b; '
    'code=$(curl -s -o /tmp/b -w "%{http_code}" --max-time 5 "$1") || code=000; '
    'sha=$(sha256sum /tmp/b 2>/dev/null | cut -d" " -f1); echo "$code ${sha:-none}"; i=$((i+1)); done'
)
"""Reads the artifact through the Service until /tmp/stop exists, printing "<status> <sha256>" for every read."""

CURL_SCRIPT = (
    'm="$1"; u="$2"; shift 2; cat > /tmp/in\n'
    'if [ -s /tmp/in ]; then set -- --data-binary @/tmp/in "$@"; fi\n'
    'code=$(curl -sS -X "$m" --max-time 240 -o /tmp/out -D /tmp/hdr -w "%{http_code}" "$@" "$u") || code=000\n'
    'echo "$code"; cat /tmp/hdr 2>/dev/null | tr -d "\\r"; echo "---"; base64 /tmp/out 2>/dev/null | tr -d "\\n"; echo\n'
)
"""Runs one request from the client pod and prints the status, the headers and the base64 body."""


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def header_value(headers, name):
    prefix = name.lower() + ":"
    for header in headers:
        if header.lower().startswith(prefix):
            return header.split(":", 1)[1].strip()
    return None


def basic_auth_header():
    token = base64.b64encode(f"{BOOTSTRAP_USER}:{BOOTSTRAP_SECRET}".encode()).decode()
    return ["-H", f"Authorization: Basic {token}"]


def iso_to_epoch(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


class Kubectl:
    """kubectl and helm in one namespace. Failures raise SmokeFailure with the tool's own message."""

    def __init__(self, namespace):
        self.namespace = namespace

    def argv(self, args):
        return ["kubectl", "-n", self.namespace, *args]

    def run(self, args, *, stdin=None, timeout=300, check=True):
        data = stdin if stdin is not None else b""
        result = subprocess.run(self.argv(args), input=data, capture_output=True, timeout=timeout)
        if check and result.returncode != 0:
            message = result.stderr.decode("utf-8", errors="replace").strip()
            raise SmokeFailure(f"kubectl {' '.join(args[:2])} failed: {message}")
        return result

    def apply_file(self, path):
        self.run(["apply", "-f", path])

    def apply_json(self, document):
        self.run(["apply", "-f", "-"], stdin=json.dumps(document).encode())

    def server_pods(self):
        return json.loads(self.run(["get", "pods", "-l", SERVER_SELECTOR, "-o", "json"]).stdout)["items"]

    def ready_server_pods(self):
        """Name and IP of every running server pod with the Ready condition, sorted by name."""
        ready = {}
        for pod in self.server_pods():
            conditions = pod.get("status", {}).get("conditions", [])
            is_ready = any(c["type"] == "Ready" and c["status"] == "True" for c in conditions)
            if is_ready and "deletionTimestamp" not in pod["metadata"]:
                ready[pod["metadata"]["name"]] = pod["status"].get("podIP", "")
        return dict(sorted(ready.items()))

    def logs(self, pod):
        return self.run(["logs", pod], check=False).stdout.decode("utf-8", errors="replace")

    def phase(self, pod):
        return self.run(["get", "pod", pod, "-o", "jsonpath={.status.phase}"], check=False).stdout.decode().strip()


class ClusterHttp:
    """Requests to the pods and the Service, sent from the in-cluster client pod."""

    def __init__(self, kubectl):
        self.kubectl = kubectl

    def request(self, method, url, *, headers=(), body=b"", timeout=300):
        args = ["exec", "-i", CLIENT, "--", "sh", "-c", CURL_SCRIPT, "sh", method, url, *headers]
        result = self.kubectl.run(args, stdin=body, timeout=timeout)
        return parse_curl_output(result.stdout.decode("utf-8", errors="replace"))

    def start(self, method, url, *, headers=()):
        args = ["exec", "-i", CLIENT, "--", "sh", "-c", CURL_SCRIPT, "sh", method, url, *headers]
        return subprocess.Popen(self.kubectl.argv(args), stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def pod_url(ip, path):
    return f"http://{ip}:8080{path}"


def client_pod():
    return {
        "apiVersion": "v1", "kind": "Pod", "metadata": {"name": CLIENT, "labels": {"app": CLIENT}},
        "spec": {"restartPolicy": "Never", "containers": [{
            "name": "curl", "image": CURL_IMAGE, "command": ["sleep", "3600"],
        }]},
    }


def reader_pod():
    return {
        "apiVersion": "v1", "kind": "Pod", "metadata": {"name": READER, "labels": {"app": READER}},
        "spec": {"restartPolicy": "Never", "containers": [{
            "name": "reader", "image": CURL_IMAGE,
            "command": ["sh", "-c", READER_SCRIPT, "sh", f"{SERVICE_URL}/releases/{ROLLING_PATH}"],
        }]},
    }


def policy_settings():
    return {
        "id": POLICY, "enabled": False, "repositories": ["releases"], "scope": "RELEASES",
        "keepLatest": 1, "olderThanDays": None, "notDownloadedForDays": None,
        "groupFilter": f"{LEASE_GROUP}/.*", "schedule": "manual",
    }


class Smoke:
    """Runs the five checks in order. Each check leaves the instance with two ready replicas."""

    def __init__(self, kubectl, clock, out=print):
        self.kubectl = kubectl
        self.http = ClusterHttp(kubectl)
        self.clock = clock
        self.out = out

    # -- helpers -------------------------------------------------------------------------------------------------------

    def pods(self, expected=2):
        """The ready replicas by name and IP, once there are [expected] of them."""
        def ready():
            found = self.kubectl.ready_server_pods()
            return found if len(found) == expected else None

        return wait_until(ready, clock=self.clock, timeout=300, interval=3,
                          describe=f"{expected} ready server replicas")

    def deploy(self, ip, group, artifact, version, body):
        path = f"/releases/{group}/{artifact}/{version}/{artifact}-{version}.pom"
        status, _, reply = self.http.request("PUT", pod_url(ip, path), headers=basic_auth_header(), body=body)
        if not 200 <= status < 300:
            raise SmokeFailure(f"deploying {path} answered {status}: {reply[:200]!r}")

    def get_json(self, url, headers=()):
        status, response_headers, body = self.http.request("GET", url, headers=headers)
        if status != 200:
            raise SmokeFailure(f"GET {url} answered {status}")
        return json.loads(body), response_headers

    def put_domain(self, ip, domain, data, etag):
        url = pod_url(ip, f"/api/settings/domain/{domain}")
        headers = [*basic_auth_header(), "-H", "Content-Type: application/json", "-H", f"If-Match: {etag}"]
        status, _, reply = self.http.request("PUT", url, headers=headers, body=json.dumps(data).encode())
        if status != 200:
            raise SmokeFailure(f"updating the {domain} settings answered {status}: {reply[:200]!r}")

    def runs(self, ip):
        data, _ = self.get_json(pod_url(ip, f"/api/cleanup/runs?policy={POLICY}"), basic_auth_header())
        return data

    def probe_run(self, ip):
        """Starts a manual run of the policy on [ip]. 409 means another run holds the lease, 200 means it ran."""
        status, _, reply = self.http.request("POST", pod_url(ip, f"/api/cleanup/policies/{POLICY}/run"),
                                             headers=basic_auth_header())
        if status == 409:
            return "held"
        if status == 200:
            return "ran"
        raise SmokeFailure(f"the run of policy {POLICY} on {ip} answered {status}: {reply[:200]!r}")

    def reader_lines(self):
        try:
            text = self.kubectl.logs(READER)
        except SmokeFailure:
            return []
        return [line for line in text.splitlines() if line.strip()]

    # -- setup ---------------------------------------------------------------------------------------------------------

    def install(self):
        self.out("installing the dependencies")
        self.kubectl.run(["create", "namespace", NAMESPACE], check=False)
        self.kubectl.apply_file(DEPENDENCIES)
        self.kubectl.run(["wait", "deployment/mariadb", "deployment/floci", "deployment/nats",
                          "--for=condition=Available", "--timeout=300s"])
        self.kubectl.run(["wait", "job/ingot-smoke-bucket", "--for=condition=complete", "--timeout=300s"])
        self.out("installing two server replicas from the CI values")
        result = subprocess.run(["helm", "install", RELEASE, CHART_DIR, "--namespace", NAMESPACE,
                                 "--create-namespace", "-f", VALUES, "--timeout", "300s"],
                                capture_output=True, timeout=600)
        if result.returncode != 0:
            raise SmokeFailure(f"helm install failed: {result.stderr.decode(errors='replace').strip()}")
        self.pods()
        self.kubectl.apply_json(client_pod())
        self.kubectl.run(["wait", f"pod/{CLIENT}", "--for=condition=Ready", "--timeout=180s"])

    def configure(self):
        """Points the releases repository at the Floci bucket and defines the cleanup policy of the lease check."""
        ip = next(iter(self.pods().values()))
        maven, headers = self.get_json(pod_url(ip, "/api/settings/domain/maven"), basic_auth_header())
        for repository in maven["repositories"]:
            if repository["id"] == "releases":
                repository["storageProvider"] = {"type": "s3", "bucketName": S3_BUCKET, "endpoint": S3_ENDPOINT,
                                                 "region": S3_REGION}
                break
        else:
            raise SmokeFailure("the maven settings have no releases repository")
        self.put_domain(ip, "maven", maven, header_value(headers, "etag"))
        cleanup, headers = self.get_json(pod_url(ip, "/api/settings/domain/cleanup"), basic_auth_header())
        cleanup["policies"] = [policy_settings()]
        self.put_domain(ip, "cleanup", cleanup, header_value(headers, "etag"))

    # -- checks --------------------------------------------------------------------------------------------------------

    def check_rolling_restart(self):
        self.out("check 1: reads through the Service during a rolling restart")
        body = b"<project>rolling</project>\n"
        ip = next(iter(self.pods().values()))
        self.deploy(ip, "net/onelitefeather/smoke/rolling", "rolling", "1.0.0", body)
        expected = sha256_hex(body)
        self.kubectl.apply_json(reader_pod())
        self.kubectl.run(["wait", f"pod/{READER}", "--for=condition=Ready", "--timeout=180s"])
        wait_until(lambda: len(self.reader_lines()) >= 5, clock=self.clock, timeout=120, interval=1,
                   describe="five reads through the Service before the restart")
        self.kubectl.run(["rollout", "restart", f"deployment/{RELEASE}"])
        self.kubectl.run(["rollout", "status", f"deployment/{RELEASE}", "--timeout=300s"])
        after_rollout = len(self.reader_lines())
        wait_until(lambda: len(self.reader_lines()) >= after_rollout + 5, clock=self.clock, timeout=120,
                   interval=1, describe="five reads through the Service after the restart")
        self.kubectl.run(["exec", READER, "--", "touch", "/tmp/stop"])
        wait_until(lambda: self.kubectl.phase(READER) == "Succeeded", clock=self.clock, timeout=120, interval=1,
                   describe="the reader to finish")
        lines = self.reader_lines()
        if len(lines) < 10:
            raise SmokeFailure(f"only {len(lines)} reads ran, too few to show anything about the rollout")
        failed = failed_reads(lines, expected)
        if failed:
            raise SmokeFailure(f"{failed} of {len(lines)} reads through the Service failed during the rolling restart, "
                               f"expected 200 {expected}, answered: {read_outcomes(lines)}")
        self.kubectl.run(["delete", "pod", READER, "--ignore-not-found"])
        self.pods()

    def check_lease_takeover(self):
        self.out("check 2: takeover of a cleanup run whose holder is killed")
        ready = self.pods()
        first, second = list(ready)
        first_ip, second_ip = ready[first], ready[second]
        for index in range(LEASE_VERSIONS):
            self.deploy(first_ip, LEASE_GROUP, "lease", f"1.0.{index}", f"<project>lease {index}</project>\n".encode())
        held = self.http.start("POST", pod_url(first_ip, f"/api/cleanup/policies/{POLICY}/run"),
                               headers=basic_auth_header())
        try:
            outcome = wait_until(lambda: self.probe_run(second_ip), clock=self.clock, timeout=180, interval=1,
                                 describe="the run on the first replica to hold the lease")
            check_run_observed(outcome == "held", POLICY)
            killed_at = self.clock.wall()
            self.kubectl.run(["delete", "pod", first, "--force", "--grace-period=0"])
        finally:
            held.kill()
            held.wait()
        window_end = takeover_deadline(killed_at, LEASE_TTL_SECONDS, POLL_SECONDS)
        wait_until(lambda: self.probe_run(second_ip) == "ran", clock=self.clock,
                   timeout=LEASE_TTL_SECONDS + POLL_SECONDS + 120, interval=1,
                   describe="the other replica to take over the killed run")
        recorded = single_completed_run(self.runs(second_ip), POLICY)
        started = iso_to_epoch(recorded["startedAt"])
        if started > window_end:
            raise SmokeFailure(f"the takeover started {started - window_end:.1f}s after the window of "
                               f"{LEASE_TTL_SECONDS}s time-to-live plus {POLL_SECONDS}s poll ended")
        if recorded["deletedCount"] <= 0:
            raise SmokeFailure("the taken-over run deleted nothing, so the takeover did no work")
        self.pods()

    def check_concurrent_deploys(self):
        self.out("check 3: two deploys of one artifact at the same moment")
        ready = self.pods()
        first, second = list(ready.values())

        def deploy_both(version_pair):
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                futures = [pool.submit(self.deploy, ip, DEPLOY_GROUP, "deploy", version,
                                       f"<project>deploy {version}</project>\n".encode())
                           for ip, version in zip((first, second), version_pair)]
                for future in futures:
                    future.result()

        deploy_both(DEPLOY_VERSIONS)
        metadata_url = pod_url(first, f"/releases/{DEPLOY_GROUP}/deploy/maven-metadata.xml")
        last = {}

        def all_listed():
            status, _, body = self.http.request("GET", metadata_url)
            if status != 200:
                return False
            last["missing"] = missing_versions(body.decode("utf-8", errors="replace"), list(DEPLOY_VERSIONS))
            return not last["missing"]

        try:
            wait_until(all_listed, clock=self.clock, timeout=60, interval=2,
                       describe="maven-metadata.xml to list both versions")
        except SmokeFailure as failure:
            raise SmokeFailure(f"{failure}; the metadata lacks {last.get('missing', 'everything')}") from failure
        self.pods()

    def check_sessions(self):
        self.out("check 4: a refresh token moves between replicas")
        ready = self.pods()
        first, second = list(ready.values())
        status, _, body = self.http.request("GET", pod_url(first, "/api/auth/setup"))
        if status != 200 or not json.loads(body).get("required"):
            raise SmokeFailure("the first administrator is not required, so the smoke instance is not empty")
        code = parse_setup_code(self.kubectl.logs(next(iter(ready))))
        if code is None:
            raise SmokeFailure("the startup log names no setup code for the first administrator")
        username, password = ADMIN
        setup = json.dumps({"setupCode": code, "username": username, "password": password,
                            "displayName": "Smoke"}).encode()
        status, _, reply = self.http.request("POST", pod_url(first, "/api/auth/setup"),
                                             headers=["-H", "Content-Type: application/json"], body=setup)
        if status != 200:
            raise SmokeFailure(f"creating the first administrator answered {status}: {reply[:200]!r}")

        login = json.dumps({"provider": "local", "username": username, "password": password}).encode()
        status, headers, reply = self.http.request("POST", pod_url(first, "/api/auth/login"),
                                                   headers=["-H", "Content-Type: application/json"], body=login)
        if status != 200:
            raise SmokeFailure(f"login on the first replica answered {status}")
        cookies = parse_set_cookies(headers)
        refresh_1, csrf_1 = cookies["ingot_refresh"], cookies["ingot_csrf"]

        def cookie_headers(refresh, csrf):
            return ["-H", f"Cookie: ingot_refresh={refresh}; ingot_csrf={csrf}", "-H", f"X-CSRF-Token: {csrf}"]

        status, headers, reply = self.http.request("POST", pod_url(second, "/api/auth/refresh"),
                                                   headers=cookie_headers(refresh_1, csrf_1))
        if status != 200:
            raise SmokeFailure(f"the refresh on the second replica answered {status}: {reply[:200]!r}")
        cookies = parse_set_cookies(headers)
        refresh_2 = cookies.get("ingot_refresh")
        csrf_2 = cookies.get("ingot_csrf", csrf_1)
        if not refresh_2 or refresh_2 == refresh_1:
            raise SmokeFailure("the refresh on the second replica did not issue a new refresh token")

        status, _, reply = self.http.request("POST", pod_url(second, "/api/auth/logout"),
                                             headers=cookie_headers(refresh_2, csrf_2))
        if status != 200:
            raise SmokeFailure(f"the logout on the second replica answered {status}: {reply[:200]!r}")

        status, _, reply = self.http.request("POST", pod_url(first, "/api/auth/refresh"),
                                             headers=cookie_headers(refresh_2, csrf_2))
        if status != 401:
            raise SmokeFailure(f"after the logout, the first replica answered {status} to the refresh, expected 401")
        self.pods()

    def check_cluster_view(self):
        self.out("check 5: both replicas list both instances")
        ready = self.pods()
        for ip in ready.values():
            def instances(ip=ip):
                status, _, body = self.http.request("GET", pod_url(ip, "/api/status/instance"),
                                                    headers=basic_auth_header())
                if status != 200:
                    return None
                ids = parse_instance_ids(json.loads(body))
                return ids if len(ids) == 2 else None

            ids = wait_until(instances, clock=self.clock, timeout=180, interval=3,
                             describe=f"both instances listed by the replica at {ip}")
            if len(set(ids)) != 2:
                raise SmokeFailure(f"the cluster section lists duplicate instances: {ids}")

    def run_all(self):
        self.install()
        self.configure()
        self.check_rolling_restart()
        self.check_lease_takeover()
        self.check_concurrent_deploys()
        self.check_sessions()
        self.check_cluster_view()
        self.out("all five checks passed")


def diagnostics(kubectl, out=print):
    """Pod state, events and logs of the replicas and the dependencies, printed after a failed check."""
    commands = [
        ["get", "pods", "-o", "wide"],
        ["get", "events", "--sort-by=.lastTimestamp"],
        ["logs", "-l", SERVER_SELECTOR, "--all-containers", "--prefix", "--tail=300"],
        ["logs", "-l", "app in (mariadb,floci,nats)", "--all-containers", "--prefix", "--tail=100"],
    ]
    for args in commands:
        result = kubectl.run(args, check=False)
        out(f"--- kubectl {' '.join(args)}")
        out(result.stdout.decode("utf-8", errors="replace"))
        out(result.stderr.decode("utf-8", errors="replace"))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.parse_args(argv)
    kubectl = Kubectl(NAMESPACE)
    smoke = Smoke(kubectl, SystemClock())
    try:
        smoke.run_all()
    except Exception as failure:  # the diagnostics must print for every failure, then the job fails
        print(f"FAILED: {failure}", flush=True)
        diagnostics(kubectl)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
