"""Shape tests for the plugin catalogue job of .github/workflows/release-please.yml.

The job publishes signed plugin jars and their catalogue. It must run only when a release is created, hold the signing key
only in the `plugin-release` environment, request the least permissions it needs, pin every action by commit SHA and upload
the catalogue last. A dry run cannot exercise the environment or the secrets, so these tests read the workflow and the script
fresh in every test and check the rules that a release would otherwise be the first to exercise.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import re
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")
PUBLISH_SCRIPT = os.path.join(GITHUB_DIR, "scripts", "publish_plugins.sh")
JOB = "plugins"
SHA_PIN = re.compile(r"@[0-9a-f]{40}(\s|$)")
ALLOWED_SECRETS = {
    "PLUGIN_SIGNING_KEY",
    "PLUGIN_SIGNING_PASSPHRASE",
    "PLUGIN_REPOSITORY_TOKEN",
    "GITHUB_TOKEN",
}


def load_workflow():
    """Reads and parses the release workflow."""
    with open(RELEASE_WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def job():
    """The plugin catalogue job."""
    return load_workflow()["jobs"][JOB]


def steps():
    return job().get("steps", [])


def step_names():
    return [step.get("name", "") for step in steps()]


def index_of(fragment):
    """The position of the first step whose name contains the fragment."""
    for index, name in enumerate(step_names()):
        if fragment in name:
            return index
    raise AssertionError(f"no step of the {JOB} job is named like {fragment!r}; steps are {step_names()}")


def step_named(fragment):
    return steps()[index_of(fragment)]


def run_texts():
    return [step.get("run") or "" for step in steps()]


class PluginJobTriggerTest(unittest.TestCase):

    def test_job_exists(self):
        self.assertIn(JOB, load_workflow()["jobs"], "the release workflow has a plugin catalogue job")

    def test_job_runs_only_when_a_release_is_created(self):
        condition = job().get("if", "")
        self.assertIn("needs.release-please.outputs.release_created == 'true'", condition,
                      "a nightly or a pull request must not start the plugin job")

    def test_job_does_not_run_on_manual_dispatch(self):
        condition = job().get("if", "")
        self.assertNotIn("workflow_dispatch", condition, "a manual container rebuild must not publish plugins")

    def test_job_waits_for_the_release_image_so_the_bundled_jar_can_be_compared(self):
        needs = job().get("needs", [])
        self.assertIn("release-please", needs)
        self.assertIn("container", needs)

    def test_job_serialises_catalogue_writes(self):
        concurrency = job().get("concurrency", {})
        self.assertEqual("plugin-catalogue", concurrency.get("group"),
                         "two releases must not read and write the catalogue at the same time")
        self.assertFalse(concurrency.get("cancel-in-progress", True), "a cancelled upload can leave a half-written catalogue")

    def test_workflow_has_no_pull_request_trigger(self):
        workflow = load_workflow()
        # PyYAML reads the bare key `on` as the boolean True.
        triggers = workflow.get(True) or workflow.get("on") or {}
        self.assertNotIn("pull_request", triggers, "pull requests never reach the release workflow")


class PluginJobEnvironmentTest(unittest.TestCase):

    def test_job_uses_the_plugin_release_environment(self):
        self.assertEqual("plugin-release", job().get("environment"),
                         "the signing key is readable only through the plugin-release environment")

    def test_permissions_are_the_minimum_the_job_needs(self):
        self.assertEqual(
            {
                "contents": "read",
                "packages": "read",
                "id-token": "write",
                "attestations": "write",
            },
            job().get("permissions"),
            "no write access to contents or packages, only the provenance identity",
        )

    def test_every_action_is_pinned_by_commit_sha(self):
        for step in steps():
            uses = step.get("uses")
            if uses:
                self.assertRegex(uses, SHA_PIN, f"{step.get('name')} must pin its action by commit SHA")

    def test_only_existing_or_planned_secret_names_are_referenced(self):
        referenced = set()
        for step in steps():
            text = yaml.safe_dump(step)
            referenced.update(re.findall(r"secrets\.([A-Z_]+)", text))
        self.assertTrue(referenced <= ALLOWED_SECRETS, f"unexpected secret names: {referenced - ALLOWED_SECRETS}")

    def test_no_step_turns_on_shell_tracing(self):
        for text in run_texts():
            self.assertNotRegex(text, r"(^|\s)set -[a-z]*x", "shell tracing would print the key material")


class PluginJobOrderTest(unittest.TestCase):

    def test_signing_precedes_the_attestation_and_the_upload(self):
        self.assertLess(index_of("Sign the plugin jars"), index_of("Attest the plugin jars"))
        self.assertLess(index_of("Sign the plugin jars"), index_of("Upload"))

    def test_catalogue_is_generated_before_it_is_signed(self):
        self.assertLess(index_of("Generate the plugin catalogue"), index_of("Sign the plugin catalogue"))

    def test_upload_follows_attestation_and_verification_follows_upload(self):
        self.assertLess(index_of("Attest the plugin jars"), index_of("Upload the plugin jars and the catalogue"))
        self.assertLess(index_of("Upload the plugin jars and the catalogue"), index_of("Verify the published plugins"))

    def test_key_directory_is_removed_even_when_the_job_fails(self):
        cleanup = step_named("Remove the signing keyring")
        self.assertEqual("always()", cleanup.get("if"))

    def test_bundled_jar_is_compared_before_any_upload(self):
        self.assertLess(index_of("Compare the bundled plugin"), index_of("Upload the plugin jars and the catalogue"))


class PluginJobGuardTest(unittest.TestCase):

    def test_missing_key_stops_the_job_before_any_upload(self):
        self.assertLess(index_of("Check the signing configuration"), index_of("Upload the plugin jars and the catalogue"))

    def test_upload_is_skipped_when_the_repository_url_is_not_configured(self):
        condition = step_named("Upload the plugin jars and the catalogue").get("if", "")
        self.assertIn("vars.PLUGIN_REPOSITORY_URL != ''", condition,
                      "a missing repository is a warning, not an upload and not a failed release")

    def test_missing_repository_url_writes_a_warning_to_the_summary(self):
        self.assertTrue(
            any("::warning" in text and "GITHUB_STEP_SUMMARY" in text for text in run_texts()),
            "the job summary names the missing repository",
        )

    def test_script_never_prints_a_secret(self):
        # Whole words only: PLUGIN_SIGNING_KEY_ID is a variable, not the key.
        secret = re.compile(r"\b(PLUGIN_SIGNING_KEY|PLUGIN_SIGNING_PASSPHRASE|PLUGIN_REPOSITORY_TOKEN)\b")
        with open(PUBLISH_SCRIPT, encoding="utf-8") as handle:
            script = handle.read()
        for line in script.splitlines():
            if not re.search(r"\b(echo|printf)\b", line):
                continue
            # The two sanctioned flows: the key and the passphrase go into gpg through a pipe, and the bearer token goes
            # into curl through a config pipe. Neither reaches stdout, a file in the checkout or an argument list.
            if "| gpg" in line or "Authorization: Bearer" in line:
                continue
            self.assertIsNone(secret.search(line), f"a secret must not be printed: {line}")


if __name__ == "__main__":
    unittest.main()
