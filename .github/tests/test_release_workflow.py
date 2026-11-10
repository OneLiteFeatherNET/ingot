"""Policy tests for .github/workflows/release-please.yml.

The release workflow carries the signing and attestation rules of the release artifacts: which jobs may request an OIDC
identity token, which may write attestations, which steps run only for a release, and the order of attest, verify and
upload. These tests read the workflow file fresh in every test and assert one behaviour each. No network, no clock, no
registry. A real signature only exists after a release, so these tests guard the permissions and the ordering that a
release cannot exercise before it runs.

Run from the repository root:

    python3 -m unittest discover -s .github/tests -t .github/tests -v
"""

import os
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
WORKFLOW = os.path.join(os.path.dirname(TESTS_DIR), "workflows", "release-please.yml")

IDENTITY_JOBS = ("container", "chart", "jar")
ATTESTATION_JOBS = ("container", "jar")
NO_IDENTITY_JOBS = ("changes", "maven", "release-please")


def load_workflow():
    """Reads and parses the release workflow from the repository."""
    with open(WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def job_permissions(workflow, name):
    """Returns the permissions mapping of one job, or fails with a message naming the job."""
    job = workflow["jobs"][name]
    permissions = job.get("permissions")
    if not isinstance(permissions, dict):
        raise AssertionError(f"job '{name}' must declare its permissions as a mapping, found: {permissions!r}")
    return permissions


def steps_of(workflow, name):
    """Returns the steps of one job."""
    return workflow["jobs"][name].get("steps", [])


def step_by_id(workflow, job, step_id):
    """Returns the step of a job with the given id, or fails with a message naming it."""
    for step in steps_of(workflow, job):
        if step.get("id") == step_id:
            return step
    raise AssertionError(f"job '{job}' has no step with id '{step_id}'")


def is_signing_or_attestation_step(step):
    """True for the steps that install cosign, sign or verify with cosign, or attest or verify an attestation."""
    uses = step.get("uses", "") or ""
    run = step.get("run", "") or ""
    return (
        "cosign-installer" in uses
        or "attest-build-provenance" in uses
        or "cosign " in run
        or "gh attestation" in run
    )


class TopLevelPermissionsTest(unittest.TestCase):

    def test_top_level_permissions_are_contents_read(self):
        workflow = load_workflow()
        self.assertEqual(
            workflow.get("permissions"),
            {"contents": "read"},
            "the workflow must keep its top-level permissions at contents: read",
        )


class IdentityTokenPermissionTest(unittest.TestCase):

    def test_container_job_requests_identity_token(self):
        permissions = job_permissions(load_workflow(), "container")
        self.assertEqual(
            permissions.get("id-token"), "write",
            "the container job must declare id-token: write for keyless cosign signing",
        )

    def test_chart_job_requests_identity_token(self):
        permissions = job_permissions(load_workflow(), "chart")
        self.assertEqual(
            permissions.get("id-token"), "write",
            "the chart job must declare id-token: write for keyless cosign signing",
        )

    def test_jar_job_requests_identity_token(self):
        permissions = job_permissions(load_workflow(), "jar")
        self.assertEqual(
            permissions.get("id-token"), "write",
            "the jar job must declare id-token: write for the attestation",
        )

    def test_jobs_without_signing_do_not_request_identity_token(self):
        workflow = load_workflow()
        for name in NO_IDENTITY_JOBS:
            with self.subTest(job=name):
                permissions = job_permissions(workflow, name)
                self.assertNotIn(
                    "id-token", permissions,
                    f"job '{name}' must not declare an id-token permission",
                )


class AttestationPermissionTest(unittest.TestCase):

    def test_container_job_requests_attestations_write(self):
        permissions = job_permissions(load_workflow(), "container")
        self.assertEqual(
            permissions.get("attestations"), "write",
            "the container job must declare attestations: write",
        )

    def test_jar_job_requests_attestations_write(self):
        permissions = job_permissions(load_workflow(), "jar")
        self.assertEqual(
            permissions.get("attestations"), "write",
            "the jar job must declare attestations: write",
        )

    def test_chart_job_does_not_request_attestations_write(self):
        permissions = job_permissions(load_workflow(), "chart")
        self.assertNotIn(
            "attestations", permissions,
            "the chart job signs but does not attest, so it must not declare attestations",
        )

    def test_jobs_without_attestation_do_not_request_attestations_write(self):
        workflow = load_workflow()
        for name in NO_IDENTITY_JOBS:
            with self.subTest(job=name):
                permissions = job_permissions(workflow, name)
                self.assertNotIn(
                    "attestations", permissions,
                    f"job '{name}' must not declare an attestations permission",
                )


class ReleaseGatingTest(unittest.TestCase):

    def test_signing_and_attestation_steps_are_gated_on_release(self):
        workflow = load_workflow()
        for name in IDENTITY_JOBS:
            signing_steps = [step for step in steps_of(workflow, name) if is_signing_or_attestation_step(step)]
            self.assertTrue(
                signing_steps,
                f"job '{name}' must contain at least one cosign or attestation step to gate",
            )
            for step in signing_steps:
                label = step.get("name") or step.get("uses") or step.get("id")
                condition = str(step.get("if", ""))
                with self.subTest(job=name, step=label):
                    self.assertTrue(
                        "released" in condition or "release_created" in condition,
                        f"step '{label}' in job '{name}' must have an if that mentions released or release_created, "
                        f"found: {condition!r}",
                    )


class RetryStepIdTest(unittest.TestCase):

    def test_server_retry_step_has_id(self):
        step = step_by_id(load_workflow(), "container", "server-retry")
        self.assertEqual(step.get("id"), "server-retry")

    def test_dashboard_retry_step_has_id(self):
        step = step_by_id(load_workflow(), "container", "dashboard-retry")
        self.assertEqual(step.get("id"), "dashboard-retry")


class JarAttestationOrderTest(unittest.TestCase):

    def test_jar_attestation_runs_before_upload(self):
        steps = steps_of(load_workflow(), "jar")
        attest_index = None
        upload_index = None
        for index, step in enumerate(steps):
            if "attest-build-provenance" in (step.get("uses") or ""):
                attest_index = index
            if "gh release upload" in (step.get("run") or ""):
                upload_index = index
        self.assertIsNotNone(attest_index, "the jar job must run actions/attest-build-provenance")
        self.assertIsNotNone(upload_index, "the jar job must still upload the jar to the release")
        self.assertLess(
            attest_index, upload_index,
            "the attestation must be created before the jar is uploaded to the release",
        )


if __name__ == "__main__":
    unittest.main()
