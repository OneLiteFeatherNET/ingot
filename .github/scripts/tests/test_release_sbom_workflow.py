"""Shape tests for the SBOM steps of .github/workflows/release-please.yml and .github/workflows/ci.yml.

A release SBOM is only trustworthy if it is generated after the jar it describes, validated before it is attested,
attested before it is uploaded, and never produced for a nightly. These tests read both workflow files fresh in every
test and check that order, that gating and that permission scope. They need no network and no release: the signatures
themselves only exist after a release, so these tests guard what a release cannot exercise before it runs.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import json
import os
import re
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")
CI_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "ci.yml")

# Any step that mentions one of these belongs to the SBOM chain of a release.
SBOM_MARKERS = (
    "cyclonedxDirectBom",
    "cyclonedx-npm",
    "verify_sbom.py",
    "actions/attest-sbom",
    "cdx.json",
    "https://cyclonedx.org/bom",
)
SHA_PIN = re.compile(r"@[0-9a-f]{40}$")


def load_workflow(path):
    """Reads and parses one workflow file."""
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def job_steps(path, job):
    """Returns the steps of one job of one workflow file."""
    return load_workflow(path)["jobs"][job].get("steps", [])


def is_sbom_step(step):
    """True for a step that generates, validates, attests, verifies or uploads an SBOM."""
    text = json.dumps(step)
    return any(marker in text for marker in SBOM_MARKERS)


def is_generation_step(step):
    run = step.get("run") or ""
    return "cyclonedxDirectBom" in run or "cyclonedx-npm" in run


def is_validation_step(step):
    return "verify_sbom.py" in (step.get("run") or "")


def is_sbom_attestation_step(step):
    return (step.get("uses") or "").startswith("actions/attest-sbom@")


def is_attestation_step(step):
    uses = step.get("uses") or ""
    run = step.get("run") or ""
    return uses.startswith("actions/attest") or "gh attestation verify" in run


def is_upload_step(step):
    return "gh release upload" in (step.get("run") or "")


def indices(steps, predicate):
    return [index for index, step in enumerate(steps) if predicate(step)]


def first_index(steps, predicate, description):
    """Returns the index of the first step that matches, or fails naming what is missing."""
    found = indices(steps, predicate)
    if not found:
        raise AssertionError(f"no step {description}")
    return found[0]


class ReleaseSbomGenerationTest(unittest.TestCase):

    def setUp(self):
        self.steps = job_steps(RELEASE_WORKFLOW, "jar")

    def test_sbom_generation_follows_the_shadow_jar_build(self):
        shadow_jar = first_index(self.steps, lambda s: "shadowJar" in (s.get("run") or ""), "builds the shadow jar")
        generation = indices(self.steps, is_generation_step)

        self.assertEqual(
            len(generation), 2,
            f"the jar job must generate the server and the dashboard SBOM, found {len(generation)} generation steps",
        )
        for index in generation:
            self.assertGreater(index, shadow_jar, "every SBOM must be generated after the shadow jar is built")

    def test_validation_runs_before_any_attestation(self):
        validation = first_index(self.steps, is_validation_step, "validates the SBOMs with verify_sbom.py")
        attestations = indices(
            self.steps,
            lambda s: is_sbom_attestation_step(s) or (s.get("uses") or "").startswith("actions/attest-build-provenance"),
        )

        self.assertTrue(attestations, "the jar job must attest the jar and the SBOMs")
        for index in attestations:
            self.assertLess(validation, index, "an SBOM must be validated before it is attested")

    def test_both_sbom_attestations_precede_the_upload(self):
        attestations = indices(self.steps, is_sbom_attestation_step)
        upload = first_index(self.steps, is_upload_step, "uploads the assets to the release")

        self.assertEqual(
            len(attestations), 2,
            f"each SBOM must be attested once, found {len(attestations)} attest-sbom steps",
        )
        for index in attestations:
            self.assertLess(index, upload, "an SBOM must be attested before it is uploaded to the release")

    def test_upload_step_names_both_sbom_files(self):
        upload = self.steps[first_index(self.steps, is_upload_step, "uploads the assets to the release")]
        run = upload["run"]

        self.assertIn("ingot-${VERSION}.cdx.json", run, "the upload must name the server SBOM")
        self.assertIn("ingot-frontend-${VERSION}.cdx.json", run, "the upload must name the dashboard SBOM")

    def test_every_sbom_step_is_gated_on_release(self):
        sbom_steps = [step for step in self.steps if is_sbom_step(step)]

        self.assertTrue(sbom_steps, "the jar job must contain SBOM steps")
        for step in sbom_steps:
            self.assertIn(
                "release_created", str(step.get("if", "")),
                f"step '{step.get('name', step.get('id'))}' must carry the release_created condition",
            )

    def test_sbom_steps_stay_out_of_the_other_release_jobs(self):
        workflow = load_workflow(RELEASE_WORKFLOW)
        for name, job in workflow["jobs"].items():
            if name == "jar":
                continue
            with self.subTest(job=name):
                leaked = [step.get("name", step.get("id")) for step in job.get("steps", []) if is_sbom_step(step)]
                self.assertEqual(leaked, [], f"job '{name}' must not generate, attest or upload an SBOM")

    def test_every_action_in_the_jar_job_is_pinned_by_full_sha(self):
        for step in self.steps:
            uses = step.get("uses")
            if uses is None:
                continue
            with self.subTest(step=step.get("name", uses)):
                self.assertRegex(uses, SHA_PIN, f"'{uses}' must be pinned by a full commit SHA")


class CiSbomJobTest(unittest.TestCase):

    def setUp(self):
        self.workflow = load_workflow(CI_WORKFLOW)

    def test_sbom_job_requests_read_only_permissions(self):
        permissions = self.workflow["jobs"]["sbom"].get("permissions")

        self.assertEqual(
            permissions, {"contents": "read"},
            "the sbom job must hold contents: read and nothing else, so no identity token is requested",
        )

    def test_sbom_job_runs_on_pull_requests_only(self):
        condition = str(self.workflow["jobs"]["sbom"].get("if", ""))

        self.assertIn("github.event_name == 'pull_request'", condition)

    def test_sbom_job_validates_the_generated_sboms(self):
        steps = self.workflow["jobs"]["sbom"]["steps"]

        self.assertTrue(
            indices(steps, is_validation_step),
            "the sbom job must run verify_sbom.py on the SBOMs it generates",
        )

    def test_sbom_job_generates_the_server_and_dashboard_sboms(self):
        steps = self.workflow["jobs"]["sbom"]["steps"]

        self.assertEqual(
            len(indices(steps, is_generation_step)), 2,
            "the sbom job must generate both SBOMs, the same ones the release job generates",
        )

    def test_sbom_job_actions_are_pinned_by_full_sha(self):
        for step in self.workflow["jobs"]["sbom"]["steps"]:
            uses = step.get("uses")
            if uses is None:
                continue
            with self.subTest(step=step.get("name", uses)):
                self.assertRegex(uses, SHA_PIN, f"'{uses}' must be pinned by a full commit SHA")

    def test_no_sbom_step_appears_outside_the_sbom_job(self):
        for name, job in self.workflow["jobs"].items():
            if name == "sbom":
                continue
            with self.subTest(job=name):
                leaked = [step.get("name", step.get("id")) for step in job.get("steps", []) if is_sbom_step(step)]
                self.assertEqual(leaked, [], f"job '{name}' must not generate or validate an SBOM")

    def test_jsonschema_is_installed_before_the_discover_command_runs(self):
        steps = self.workflow["jobs"]["images"]["steps"]
        install = first_index(
            steps,
            lambda s: "pip install" in (s.get("run") or "") and "jsonschema==" in (s.get("run") or ""),
            "installs the pinned jsonschema for the image job",
        )
        discover = first_index(
            steps,
            lambda s: "unittest discover -s .github/scripts/tests" in (s.get("run") or ""),
            "runs the discover command of the image job",
        )

        self.assertLess(install, discover, "test_verify_sbom.py imports jsonschema, so it must be installed first")


if __name__ == "__main__":
    unittest.main()
