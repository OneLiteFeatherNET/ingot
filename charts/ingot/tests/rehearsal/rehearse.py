"""Upgrade rehearsal: a release of the upstream reposilite chart moves to this chart with only the image changed.

The chart job runs this on its kind cluster, in a namespace of its own. The script installs the upstream chart 1.4.5
(appVersion 3.6.6) as release `reposilite` with persistence, creates a token and uploads one artifact through the HTTP
API. It records the claim UID, the sha1 of the artifact and the token status. Then it runs `helm upgrade` to this chart
with the image set to the CI image and nothing else, waits for the rollout and records the same three values again. It
rolls back to the upstream revision and records them a third time. Any difference fails the run with a message.

No sleeps: the waits are `kubectl rollout status` with a timeout, helm with its own timeout, and a bounded read of the
port-forward output.

Run from the repository root, with a kubectl context that points at an empty cluster:

    python3 charts/ingot/tests/rehearsal/rehearse.py --ingot-image ingot:ci
"""

import argparse
import base64
import hashlib
import json
import os
import re
import secrets
import select
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from contextlib import contextmanager

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rehearsal.compare import compare_identity  # noqa: E402

UPSTREAM_CHART_REPO = "https://helm.reposilite.com/"
UPSTREAM_CHART = "reposilite"
UPSTREAM_CHART_VERSION = "1.4.5"
UPSTREAM_CHART_SHA256 = "625a43aef451c588cd2d9ac0efdc6206f7a678dda28c4f164ecf6796a149f812"
"""The .tgz of the upstream chart 1.4.5, as helm pull returns it from the repository."""

UPSTREAM_IMAGE_NAME = "dzikoysk/reposilite"
UPSTREAM_IMAGE_TAG = "3.6.6"
UPSTREAM_IMAGE_DIGEST = "sha256:2438ab2a01392ea53d62ba83cdca1afe963603518f4282af4712076d39e1dd22"
"""The index digest of dzikoysk/reposilite:3.6.6 on Docker Hub, which covers linux/amd64 and linux/arm64."""

RELEASE = "reposilite"
CONTAINER_PORT = 8080
TIMEOUT_SECONDS = 300
ARTIFACT_PATH = "/releases/com/example/rehearsal/1.0/rehearsal-1.0.txt"
ARTIFACT_BODY = b"ingot upgrade rehearsal\n"
TOKEN_NAME = "rehearsal"
BOOTSTRAP_NAME = "rehearsal-admin"
CHART_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
"""The chart that the upgrade moves to, charts/ingot."""

FORWARDING = re.compile(r"Forwarding from 127\.0\.0\.1:(\d+) -> ")


class RehearsalFailure(Exception):
    """A step that did not hold. The message says what happened."""


def run(argv, *, what, timeout=TIMEOUT_SECONDS):
    try:
        result = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired as error:
        raise RehearsalFailure(f"{what} did not finish within {timeout}s") from error
    if result.returncode != 0:
        raise RehearsalFailure(f"{what} failed: {(result.stderr or result.stdout).strip()}")
    return result.stdout


def verify_sha256(path, expected):
    with open(path, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    if digest != expected:
        raise RehearsalFailure(f"{os.path.basename(path)} has sha256 {digest}, expected {expected}")


def upstream_revision(history):
    """The revision of the upstream chart 1.4.5 in the release history, as returned by helm history -o json."""
    chart = f"{UPSTREAM_CHART}-{UPSTREAM_CHART_VERSION}"
    revisions = [entry["revision"] for entry in history if entry.get("chart") == chart]
    if len(revisions) != 1:
        raise RehearsalFailure(f"expected one revision of {chart} in the release history, found {len(revisions)}")
    return revisions[0]


def forwarded_port(line):
    """The local port of a kubectl port-forward line, or None when the line names none."""
    match = FORWARDING.search(line)
    return int(match.group(1)) if match else None


def split_image(reference):
    """Splits name:tag at the last colon, so that a registry with a port stays in the name."""
    name, _, tag = reference.rpartition(":")
    if not name or not tag:
        raise RehearsalFailure(f"the image {reference!r} must have the form name:tag")
    return name, tag


def upstream_values(bootstrap):
    """The values of the upstream release. Helm reads JSON as YAML, so the file is written as JSON."""
    options = f"--token {bootstrap}"
    return {
        "image": {"name": UPSTREAM_IMAGE_NAME, "tag": f"{UPSTREAM_IMAGE_TAG}@{UPSTREAM_IMAGE_DIGEST}"},
        "persistence": {"enabled": True},
        "resources": {"requests": {"cpu": "100m", "memory": "256Mi"}, "limits": {"memory": "768Mi"}},
        # Both entrypoints read REPOSILITE_OPTS, so the same values work for both images and only the image
        # line changes in the upgrade.
        "env": [
            {"name": "JAVA_OPTS", "value": "-Xmx256M"},
            {"name": "REPOSILITE_OPTS", "value": options},
        ],
    }


def basic_auth(name, secret):
    return "Basic " + base64.b64encode(f"{name}:{secret}".encode()).decode()


def http(method, url, authorization, body=None, content_type=None):
    """One request, returning the status and the body. HTTP error statuses are returned, not raised."""
    headers = {"Authorization": authorization}
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()
    except urllib.error.URLError as error:
        raise RehearsalFailure(f"{method} {url} did not connect: {error.reason}") from error


def create_token(base_url, bootstrap, secret):
    body = json.dumps({
        "type": "PERSISTENT",
        "secret": secret,
        "permissions": [],
        "routes": [{"path": "/releases/", "permissions": ["r", "w"]}],
        "description": "upgrade rehearsal",
    }).encode()
    status, reply = http("PUT", f"{base_url}/api/tokens/{TOKEN_NAME}", basic_auth(*bootstrap), body, "application/json")
    if status != 200:
        raise RehearsalFailure(f"creating the token answered {status}: {reply[:200]!r}")


def upload_artifact(base_url, secret):
    status, reply = http("PUT", base_url + ARTIFACT_PATH, basic_auth(TOKEN_NAME, secret), ARTIFACT_BODY)
    if not 200 <= status < 300:
        raise RehearsalFailure(f"uploading the artifact answered {status}: {reply[:200]!r}")


def kubectl(namespace, *args):
    return run(["kubectl", "-n", namespace, *args], what=f"kubectl {args[0]}")


def running_image(namespace):
    return kubectl(namespace, "get", "deployment", RELEASE, "-o", "jsonpath={.spec.template.spec.containers[0].image}").strip()


def mounted_claims(namespace):
    return kubectl(namespace, "get", "deployment", RELEASE, "-o",
                   "jsonpath={.spec.template.spec.volumes[*].persistentVolumeClaim.claimName}").split()


def claim_uid(namespace):
    return kubectl(namespace, "get", "pvc", RELEASE, "-o", "jsonpath={.metadata.uid}").strip()


def wait_for_rollout(namespace):
    kubectl(namespace, "rollout", "status", f"deployment/{RELEASE}", f"--timeout={TIMEOUT_SECONDS}s")


@contextmanager
def port_forward(namespace):
    """Forwards the service to a local port that kubectl picks from the OS (the :8080 form), and yields its URL."""
    with tempfile.TemporaryFile("w+") as errors:
        process = subprocess.Popen(
            ["kubectl", "-n", namespace, "port-forward", "--address", "127.0.0.1",
             f"svc/{RELEASE}", f":{CONTAINER_PORT}"],
            stdout=subprocess.PIPE, stderr=errors, text=True,
        )
        try:
            ready, _, _ = select.select([process.stdout], [], [], TIMEOUT_SECONDS)
            port = forwarded_port(process.stdout.readline()) if ready else None
            if port is None:
                errors.seek(0)
                raise RehearsalFailure(f"kubectl port-forward named no local port: {errors.read().strip()}")
            yield f"http://127.0.0.1:{port}"
        finally:
            process.terminate()
            process.wait(timeout=30)


def observe(namespace, base_url, secret):
    """The values of the rehearsal, and the claims that the running pod mounts."""
    status, body = http("GET", base_url + ARTIFACT_PATH, basic_auth(TOKEN_NAME, secret))
    sha1 = hashlib.sha1(body).hexdigest() if status == 200 else f"missing (HTTP {status})"
    status, _ = http("GET", f"{base_url}/api/auth/me", basic_auth(TOKEN_NAME, secret))
    token_status = "valid" if status == 200 else f"rejected (HTTP {status})"
    return {
        "claim_uid": claim_uid(namespace),
        "artifact_sha1": sha1,
        "token_status": token_status,
        "mounted_claims": mounted_claims(namespace),
    }


def check(problems, before, after, stage):
    """Adds the differences of one stage: the three values, and the claim that the pod mounts."""
    problems.extend(f"{stage}: {problem}" for problem in compare_identity(before, after))
    if after["mounted_claims"] != [RELEASE]:
        problems.append(f"{stage}: the running pod mounts {after['mounted_claims'] or 'no claim'} "
                        f"instead of {RELEASE}, so the data is not the upstream data")


def rehearse(namespace, ingot_image, workdir):
    """Runs the three stages and returns the list of differences. An empty list means the rehearsal passed."""
    ingot_name, ingot_tag = split_image(ingot_image)
    upstream_chart = pull_upstream_chart(workdir)
    bootstrap = (BOOTSTRAP_NAME, secrets.token_urlsafe(24))
    secret = secrets.token_urlsafe(24)
    values_path = os.path.join(workdir, "values.json")
    with open(values_path, "w", encoding="utf-8") as handle:
        json.dump(upstream_values(f"{bootstrap[0]}:{bootstrap[1]}"), handle)

    kubectl_create_namespace(namespace)
    run(["helm", "install", RELEASE, upstream_chart, "--namespace", namespace, "--values", values_path],
        what="helm install of the upstream chart")
    wait_for_rollout(namespace)
    with port_forward(namespace) as base_url:
        create_token(base_url, bootstrap, secret)
        upload_artifact(base_url, secret)
        before = observe(namespace, base_url, secret)

    run(["helm", "upgrade", RELEASE, CHART_DIR, "--namespace", namespace, "--values", values_path,
         "--set-string", f"image.name={ingot_name}", "--set-string", f"image.tag={ingot_tag}"],
        what="helm upgrade to this chart")
    wait_for_rollout(namespace)
    if running_image(namespace) != ingot_image:
        raise RehearsalFailure(f"after the upgrade the pod runs {running_image(namespace)}, not {ingot_image}")
    problems = []
    with port_forward(namespace) as base_url:
        after_upgrade = observe(namespace, base_url, secret)
    check(problems, before, after_upgrade, "after the upgrade")

    history = json.loads(run(["helm", "history", RELEASE, "--namespace", namespace, "--output", "json"],
                             what="helm history"))
    revision = upstream_revision(history)
    run(["helm", "rollback", RELEASE, str(revision), "--namespace", namespace], what="helm rollback")
    wait_for_rollout(namespace)
    if running_image(namespace) != f"{UPSTREAM_IMAGE_NAME}:{UPSTREAM_IMAGE_TAG}@{UPSTREAM_IMAGE_DIGEST}":
        raise RehearsalFailure(f"after the rollback the pod runs {running_image(namespace)}, not the upstream image")
    with port_forward(namespace) as base_url:
        after_rollback = observe(namespace, base_url, secret)
    check(problems, before, after_rollback, "after the rollback")
    return problems


def pull_upstream_chart(workdir):
    run(["helm", "pull", UPSTREAM_CHART, "--repo", UPSTREAM_CHART_REPO, "--version", UPSTREAM_CHART_VERSION,
         "--destination", workdir], what="helm pull of the upstream chart")
    path = os.path.join(workdir, f"{UPSTREAM_CHART}-{UPSTREAM_CHART_VERSION}.tgz")
    verify_sha256(path, UPSTREAM_CHART_SHA256)
    return path


def kubectl_create_namespace(namespace):
    run(["kubectl", "create", "namespace", namespace], what=f"creating the fresh namespace {namespace}")


def print_diagnostics(namespace):
    """Best effort: the state of the release, printed when the rehearsal fails, because the cluster is kept for it."""
    for argv in (["kubectl", "-n", namespace, "get", "pods,pvc,deployments", "-o", "wide"],
                 ["kubectl", "-n", namespace, "logs", f"deployment/{RELEASE}", "--tail=200", "--all-containers"]):
        result = subprocess.run(argv, capture_output=True, text=True, check=False)
        print(result.stdout or result.stderr, file=sys.stderr)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--ingot-image", default="ingot:ci", help="the image of this chart, name:tag")
    parser.add_argument("--namespace", default="ingot-upgrade-rehearsal", help="a namespace that does not exist yet")
    args = parser.parse_args(argv)
    with tempfile.TemporaryDirectory() as workdir:
        try:
            problems = rehearse(args.namespace, args.ingot_image, workdir)
        except RehearsalFailure as failure:
            print(f"upgrade rehearsal failed: {failure}", file=sys.stderr)
            print_diagnostics(args.namespace)
            return 1
    if problems:
        for problem in problems:
            print(f"upgrade rehearsal: {problem}", file=sys.stderr)
        return 1
    run(["kubectl", "delete", "namespace", args.namespace, "--wait=false"], what="deleting the rehearsal namespace")
    print(f"upgrade rehearsal passed: claim UID, artifact sha1 and token status are unchanged after the upgrade "
          f"and after the rollback to {UPSTREAM_CHART}-{UPSTREAM_CHART_VERSION}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
