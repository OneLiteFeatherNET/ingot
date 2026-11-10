"""Render tests for the opt-in restricted Pod Security profile of the server.

The profile is `deployment.securityProfile`: "" (default), "restricted" or "restricted-openshift". Each test renders the
chart with `helm template` through the helpers of test_chart and names one behaviour. Nothing runs on a cluster here.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import unittest

from test_chart import CHART_DIR, RELEASE, SERVER, container, helm, named, pod_spec, render

RESTRICTED_UID = 977
SECCOMP = {"type": "RuntimeDefault"}


def profile(name, **extra):
    values = {"deployment": {"securityProfile": name}}
    values["deployment"].update(extra)
    return values


def server(values=None):
    return named(render(values), "Deployment", SERVER)


def volume(deployment, name):
    matches = [item for item in pod_spec(deployment)["volumes"] if item["name"] == name]
    if len(matches) != 1:
        raise AssertionError(f"expected one volume named {name}, found {len(matches)}")
    return matches[0]


def mount(deployment, name):
    matches = [item for item in container(deployment)["volumeMounts"] if item["name"] == name]
    if len(matches) != 1:
        raise AssertionError(f"expected one volume mount named {name}, found {len(matches)}")
    return matches[0]


class UnsetProfileTest(unittest.TestCase):

    def test_empty_profile_renders_the_same_manifests_as_no_profile(self):
        self.assertEqual(
            render(), render(profile("")),
            "an empty securityProfile must render exactly the default manifests",
        )

    def test_empty_profile_renders_no_pod_security_context(self):
        self.assertNotIn("securityContext", pod_spec(server(profile(""))),
                         "no profile means no pod security context")

    def test_empty_profile_renders_no_container_security_context(self):
        self.assertNotIn("securityContext", container(server(profile(""))),
                         "no profile means no container security context")


class RestrictedProfileTest(unittest.TestCase):

    def setUp(self):
        self.deployment = server(profile("restricted"))

    def test_pod_security_context_runs_as_the_fixed_uid_and_gid(self):
        context = pod_spec(self.deployment)["securityContext"]
        self.assertEqual(RESTRICTED_UID, context["runAsUser"], "the pod must run as uid 977")
        self.assertEqual(RESTRICTED_UID, context["runAsGroup"], "the pod must run with gid 977")
        self.assertEqual(RESTRICTED_UID, context["fsGroup"], "the pod must use 977 as the fsGroup")

    def test_pod_security_context_requires_non_root_and_runtime_default_seccomp(self):
        context = pod_spec(self.deployment)["securityContext"]
        self.assertTrue(context["runAsNonRoot"], "the restricted level requires runAsNonRoot")
        self.assertEqual(SECCOMP, context["seccompProfile"], "the pod must use the RuntimeDefault seccomp profile")

    def test_container_security_context_drops_every_capability_and_blocks_escalation(self):
        context = container(self.deployment)["securityContext"]
        self.assertFalse(context["allowPrivilegeEscalation"], "privilege escalation must be disabled")
        self.assertEqual(["ALL"], context["capabilities"]["drop"], "every capability must be dropped")

    def test_container_security_context_has_a_read_only_root_filesystem(self):
        self.assertTrue(
            container(self.deployment)["securityContext"]["readOnlyRootFilesystem"],
            "the root filesystem must be read-only",
        )

    def test_container_security_context_runs_as_the_fixed_uid_and_gid(self):
        context = container(self.deployment)["securityContext"]
        self.assertEqual(RESTRICTED_UID, context["runAsUser"], "the container must run as uid 977")
        self.assertEqual(RESTRICTED_UID, context["runAsGroup"], "the container must run with gid 977")
        self.assertTrue(context["runAsNonRoot"], "the container must require a non-root user")
        self.assertEqual(SECCOMP, context["seccompProfile"], "the container must use RuntimeDefault seccomp")

    def test_tmp_is_an_emptydir_mounted_at_tmp(self):
        self.assertIn("emptyDir", volume(self.deployment, "ingot-tmp"), "ingot-tmp must be an emptyDir volume")
        self.assertEqual("/tmp", mount(self.deployment, "ingot-tmp")["mountPath"],
                         "ingot-tmp must be mounted at /tmp")

    def test_logs_are_an_emptydir_mounted_at_the_reposilite_log_path(self):
        self.assertIn("emptyDir", volume(self.deployment, "ingot-logs"), "ingot-logs must be an emptyDir volume")
        self.assertEqual("/var/log/reposilite", mount(self.deployment, "ingot-logs")["mountPath"],
                         "ingot-logs must be mounted at /var/log/reposilite")

    def test_data_volume_is_unchanged(self):
        default = server()
        self.assertEqual(
            volume(default, "data"), volume(self.deployment, "data"),
            "the data volume must stay as it is under the restricted profile",
        )
        self.assertEqual(
            mount(default, "data"), mount(self.deployment, "data"),
            "the data mount must stay as it is under the restricted profile",
        )

    def test_profile_adds_no_volume_beyond_the_two_writable_paths(self):
        names = sorted(item["name"] for item in pod_spec(self.deployment)["volumes"])
        default = sorted(item["name"] for item in pod_spec(server())["volumes"])
        self.assertEqual(sorted(default + ["ingot-logs", "ingot-tmp"]), names,
                         "the profile must add exactly the two writable emptyDir volumes")


class RestrictedOpenShiftProfileTest(unittest.TestCase):

    def setUp(self):
        self.deployment = server(profile("restricted-openshift"))

    def test_pod_security_context_omits_the_fixed_ids(self):
        context = pod_spec(self.deployment)["securityContext"]
        for field in ("runAsUser", "runAsGroup", "fsGroup"):
            self.assertNotIn(field, context, f"restricted-openshift must not set {field}, the cluster assigns it")

    def test_container_security_context_omits_the_fixed_ids(self):
        context = container(self.deployment)["securityContext"]
        for field in ("runAsUser", "runAsGroup"):
            self.assertNotIn(field, context, f"restricted-openshift must not set {field} on the container")

    def test_security_context_still_requires_non_root_and_seccomp(self):
        pod = pod_spec(self.deployment)["securityContext"]
        context = container(self.deployment)["securityContext"]
        self.assertTrue(pod["runAsNonRoot"], "the pod must require a non-root user")
        self.assertTrue(context["runAsNonRoot"], "the container must require a non-root user")
        self.assertEqual(SECCOMP, pod["seccompProfile"], "the pod must use RuntimeDefault seccomp")

    def test_container_drops_every_capability_and_has_a_read_only_root(self):
        context = container(self.deployment)["securityContext"]
        self.assertEqual(["ALL"], context["capabilities"]["drop"], "every capability must be dropped")
        self.assertTrue(context["readOnlyRootFilesystem"], "the root filesystem must be read-only")
        self.assertFalse(context["allowPrivilegeEscalation"], "privilege escalation must be disabled")

    def test_writable_paths_are_mounted_as_under_restricted(self):
        self.assertEqual("/tmp", mount(self.deployment, "ingot-tmp")["mountPath"])
        self.assertEqual("/var/log/reposilite", mount(self.deployment, "ingot-logs")["mountPath"])


class ConflictTest(unittest.TestCase):

    def test_profile_with_pod_security_context_fails_naming_both_keys(self):
        code, _, stderr = helm(
            ["template", RELEASE, CHART_DIR],
            {"deployment": {"securityProfile": "restricted", "podSecurityContext": {"enabled": True}}},
        )
        self.assertNotEqual(0, code, "a profile and podSecurityContext.enabled must not render together")
        self.assertIn("deployment.securityProfile", stderr, "the message must name deployment.securityProfile")
        self.assertIn("deployment.podSecurityContext.enabled", stderr,
                      "the message must name deployment.podSecurityContext.enabled")

    def test_profile_with_container_security_context_fails_naming_both_keys(self):
        code, _, stderr = helm(
            ["template", RELEASE, CHART_DIR],
            {"deployment": {"securityProfile": "restricted-openshift",
                            "containerSecurityContext": {"enabled": True}}},
        )
        self.assertNotEqual(0, code, "a profile and containerSecurityContext.enabled must not render together")
        self.assertIn("deployment.securityProfile", stderr, "the message must name deployment.securityProfile")
        self.assertIn("deployment.containerSecurityContext.enabled", stderr,
                      "the message must name deployment.containerSecurityContext.enabled")


class SchemaTest(unittest.TestCase):

    def test_privileged_profile_is_rejected_by_the_schema(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], profile("privileged"))
        self.assertNotEqual(0, code, "a profile outside the enum must fail schema validation")
        self.assertIn("securityProfile", stderr, "the schema error must name the profile key")


if __name__ == "__main__":
    unittest.main()
