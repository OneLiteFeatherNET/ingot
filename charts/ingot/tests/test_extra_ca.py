"""Render tests for the opt-in extra CA bundle of the server.

`extraCaCerts` names exactly one ConfigMap or Secret with PEM files. When it is enabled, the chart mounts the source
read-only at /etc/ingot/extra-ca, sets INGOT_EXTRA_CA_CERTS to that path and mounts a writable emptyDir at /tmp. The
image merges the bundle into a private trust store; these tests only cover the chart side. Each test renders the chart
with `helm template` through the helpers of test_chart and names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import unittest

from test_chart import CHART_DIR, DASHBOARD, RELEASE, SERVER, container, helm, named, pod_spec, render, split_values

MOUNT_PATH = "/etc/ingot/extra-ca"
VOLUME = "ingot-extra-ca"
ENV = "INGOT_EXTRA_CA_CERTS"
PROFILE = {"deployment": {"securityProfile": "restricted"}}


def enabled(**source):
    return {"extraCaCerts": dict({"enabled": True}, **source)}


def server(values=None):
    return named(render(values), "Deployment", SERVER)


def env_entries(deployment, name):
    return [entry for entry in container(deployment).get("env", []) if entry.get("name") == name]


def volumes_named(deployment, name):
    return [volume for volume in pod_spec(deployment).get("volumes", []) if volume.get("name") == name]


def mounts_at(deployment, path):
    return [mount for mount in container(deployment).get("volumeMounts", []) if mount.get("mountPath") == path]


class DefaultRenderTest(unittest.TestCase):

    def test_default_render_sets_no_extra_ca_variable(self):
        self.assertEqual([], env_entries(server(), ENV),
                         "extraCaCerts.enabled defaults to false, so no INGOT_EXTRA_CA_CERTS may render")

    def test_default_render_has_no_extra_ca_volume(self):
        self.assertEqual([], volumes_named(server(), VOLUME),
                         "no volume for the extra CA bundle may render by default")

    def test_default_render_mounts_nothing_at_the_extra_ca_path(self):
        self.assertEqual([], mounts_at(server(), MOUNT_PATH),
                         "nothing may be mounted at the bundle path by default")

    def test_default_render_mounts_no_tmp_emptydir(self):
        self.assertEqual([], mounts_at(server(), "/tmp"),
                         "the /tmp emptyDir belongs to the security profile or the bundle, not to the default")


class ConfigMapSourceTest(unittest.TestCase):

    def setUp(self):
        self.deployment = server(enabled(configMapName="internal-ca"))

    def test_configmap_is_mounted_as_the_bundle_volume(self):
        volumes = volumes_named(self.deployment, VOLUME)
        self.assertEqual([{"name": VOLUME, "configMap": {"name": "internal-ca"}}], volumes,
                         "configMapName must back the bundle volume")

    def test_bundle_is_mounted_read_only_at_the_mount_path(self):
        self.assertEqual([{"name": VOLUME, "mountPath": MOUNT_PATH, "readOnly": True}],
                         mounts_at(self.deployment, MOUNT_PATH),
                         "the bundle must be mounted read-only at /etc/ingot/extra-ca")

    def test_variable_points_at_the_mount_path(self):
        self.assertEqual([{"name": ENV, "value": MOUNT_PATH}], env_entries(self.deployment, ENV),
                         "INGOT_EXTRA_CA_CERTS must name the mount path")


class SecretSourceTest(unittest.TestCase):

    def test_secret_is_mounted_as_the_bundle_volume(self):
        deployment = server(enabled(secretName="internal-ca"))
        self.assertEqual([{"name": VOLUME, "secret": {"secretName": "internal-ca"}}],
                         volumes_named(deployment, VOLUME),
                         "secretName must back the bundle volume")

    def test_secret_bundle_is_mounted_read_only_and_named_by_the_variable(self):
        deployment = server(enabled(secretName="internal-ca"))
        self.assertEqual([{"name": VOLUME, "mountPath": MOUNT_PATH, "readOnly": True}],
                         mounts_at(deployment, MOUNT_PATH),
                         "the secret must be mounted read-only at the bundle path")
        self.assertEqual([{"name": ENV, "value": MOUNT_PATH}], env_entries(deployment, ENV),
                         "INGOT_EXTRA_CA_CERTS must name the mount path for a Secret too")


class SourceChoiceTest(unittest.TestCase):

    def test_both_sources_fail_the_render_naming_extra_ca_certs(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR],
                               enabled(configMapName="internal-ca", secretName="internal-ca"))
        self.assertNotEqual(0, code, "a bundle with two sources must fail the render")
        self.assertIn("extraCaCerts", stderr, "the message must name extraCaCerts")

    def test_no_source_fails_the_render_naming_extra_ca_certs(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], enabled())
        self.assertNotEqual(0, code, "an enabled bundle without a source must fail the render")
        self.assertIn("extraCaCerts", stderr, "the message must name extraCaCerts")

    def test_variable_set_in_env_too_fails_naming_both_keys(self):
        values = enabled(configMapName="internal-ca")
        values["env"] = [{"name": ENV, "value": "/other"}]
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], values)
        self.assertNotEqual(0, code, "INGOT_EXTRA_CA_CERTS set by the chart and in env must fail the render")
        self.assertIn("extraCaCerts", stderr, "the message must name extraCaCerts")
        self.assertIn(ENV, stderr, "the message must name INGOT_EXTRA_CA_CERTS")


class WritablePathTest(unittest.TestCase):

    def test_enabled_bundle_mounts_a_writable_emptydir_at_tmp(self):
        deployment = server(enabled(configMapName="internal-ca"))
        mounts = mounts_at(deployment, "/tmp")
        self.assertEqual(1, len(mounts), "the bundle must come with exactly one /tmp mount")
        volumes = volumes_named(deployment, mounts[0]["name"])
        self.assertEqual(1, len(volumes), "the /tmp mount must name exactly one volume")
        self.assertIn("emptyDir", volumes[0], "the /tmp volume must be an emptyDir")
        self.assertFalse(mounts[0].get("readOnly", False), "/tmp must be writable")

    def test_tmp_is_mounted_once_when_the_profile_and_the_bundle_are_both_set(self):
        values = {"deployment": {"securityProfile": "restricted"}}
        values.update(enabled(configMapName="internal-ca"))
        deployment = server(values)
        self.assertEqual(1, len(mounts_at(deployment, "/tmp")),
                         "the profile and the bundle must share one /tmp mount")
        self.assertEqual(1, len([v for v in pod_spec(deployment)["volumes"] if v["name"] == "ingot-tmp"]),
                         "the profile and the bundle must share one ingot-tmp volume")

    def test_profile_alone_keeps_its_tmp_mount_without_the_bundle(self):
        deployment = server(PROFILE)
        self.assertEqual(1, len(mounts_at(deployment, "/tmp")),
                         "the restricted profile must keep its /tmp mount when no bundle is set")
        self.assertEqual([], volumes_named(deployment, VOLUME),
                         "the restricted profile must not add the bundle volume")


class SplitModeTest(unittest.TestCase):

    def test_dashboard_gets_no_bundle_mount_variable_or_tmp_volume(self):
        values = dict(split_values(), **enabled(configMapName="internal-ca"))
        dashboard = named(render(values), "Deployment", DASHBOARD)
        self.assertEqual([], env_entries(dashboard, ENV), "the dashboard must not read the bundle")
        self.assertEqual([], volumes_named(dashboard, VOLUME), "the dashboard must not get the bundle volume")
        self.assertEqual([], mounts_at(dashboard, "/tmp"), "the dashboard must not get the /tmp emptyDir")

    def test_server_still_reads_the_bundle_in_split_mode(self):
        values = dict(split_values(), **enabled(configMapName="internal-ca"))
        self.assertEqual([{"name": ENV, "value": MOUNT_PATH}], env_entries(server(values), ENV),
                         "the split server must read the bundle")


class TrustStoreTest(unittest.TestCase):

    def test_chart_sets_no_trust_store_variable_or_flag(self):
        deployment = server(enabled(configMapName="internal-ca"))
        rendered = repr(container(deployment).get("env", [])) + repr(container(deployment).get("args", []))
        self.assertNotIn("trustStore", rendered,
                         "the chart must not replace the trust store, so no trust store flag or variable may render")
        self.assertNotIn("javax.net.ssl", rendered,
                         "the chart must not set a JVM trust property; the image merges the bundle")


if __name__ == "__main__":
    unittest.main()
