"""Render tests for the typed HA values: external database, object storage, cluster bus, topology spread and digest.

Each test renders the chart with `helm template` (through the helpers of test_chart) and names one behaviour. The values
are opt-in: a test that sets nothing is covered by the golden renders in test_golden.py.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import os
import unittest

import yaml
from test_chart import CHART_DIR, DASHBOARD, RELEASE, SERVER, container, helm, named, of_kind, pod_spec, render, \
    split_values

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
HA_FIXTURE = os.path.join(TESTS_DIR, "fixtures", "ha-values.yaml")
CI_DIR = os.path.join(CHART_DIR, "ci")
SERVER_IMAGE = "ghcr.io/onelitefeathernet/ingot"


def env_entry(deployment, name):
    matches = [entry for entry in container(deployment).get("env", []) if entry.get("name") == name]
    if len(matches) > 1:
        raise AssertionError(f"the variable {name} is set {len(matches)} times, expected at most once")
    return matches[0] if matches else None


def database_values(secret="ingot-database", key=None):
    database = {"existingSecret": secret}
    if key is not None:
        database["secretKey"] = key
    return {"database": database}


class DatabaseSecretTest(unittest.TestCase):

    def test_database_variable_is_read_from_the_secret_key(self):
        deployment = named(render(database_values()), "Deployment", SERVER)
        self.assertEqual(
            {"secretKeyRef": {"name": "ingot-database", "key": "database"}},
            env_entry(deployment, "INGOT_LOCAL_DATABASE")["valueFrom"],
            "the default key of the database Secret is `database`",
        )

    def test_database_variable_has_no_literal_value(self):
        deployment = named(render(database_values()), "Deployment", SERVER)
        self.assertNotIn(
            "value", env_entry(deployment, "INGOT_LOCAL_DATABASE"),
            "the database connection string must stay in the Secret, never in the rendered manifest",
        )

    def test_database_secret_key_can_be_renamed(self):
        deployment = named(render(database_values(key="connection")), "Deployment", SERVER)
        self.assertEqual(
            {"secretKeyRef": {"name": "ingot-database", "key": "connection"}},
            env_entry(deployment, "INGOT_LOCAL_DATABASE")["valueFrom"],
            "database.secretKey must select the key of the Secret",
        )

    def test_database_variable_set_in_env_too_fails_naming_both_keys(self):
        values = database_values()
        values["env"] = [{"name": "INGOT_LOCAL_DATABASE", "value": "sqlite"}]
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], values)
        self.assertNotEqual(0, code, "a variable set by the chart and in env must fail the render")
        self.assertIn("database.existingSecret", stderr, "the message must name database.existingSecret")
        self.assertIn("INGOT_LOCAL_DATABASE", stderr, "the message must name INGOT_LOCAL_DATABASE")


class ObjectStorageSecretTest(unittest.TestCase):

    def setUp(self):
        self.deployment = named(render({"objectStorage": {"existingSecret": "s3-keys"}}), "Deployment", SERVER)

    def test_access_key_id_is_read_from_the_key_of_the_same_name(self):
        self.assertEqual(
            {"secretKeyRef": {"name": "s3-keys", "key": "AWS_ACCESS_KEY_ID"}},
            env_entry(self.deployment, "AWS_ACCESS_KEY_ID")["valueFrom"],
            "AWS_ACCESS_KEY_ID must come from the key of the same name",
        )

    def test_secret_access_key_is_read_from_the_key_of_the_same_name(self):
        self.assertEqual(
            {"secretKeyRef": {"name": "s3-keys", "key": "AWS_SECRET_ACCESS_KEY"}},
            env_entry(self.deployment, "AWS_SECRET_ACCESS_KEY")["valueFrom"],
            "AWS_SECRET_ACCESS_KEY must come from the key of the same name",
        )

    def test_region_is_read_as_an_optional_key(self):
        self.assertEqual(
            {"secretKeyRef": {"name": "s3-keys", "key": "AWS_REGION", "optional": True}},
            env_entry(self.deployment, "AWS_REGION")["valueFrom"],
            "AWS_REGION is optional, so the default credential chain keeps working without it",
        )

    def test_credentials_have_no_literal_value(self):
        for name in ("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY"):
            self.assertNotIn("value", env_entry(self.deployment, name),
                             f"{name} must stay in the Secret, never in the rendered manifest")


class ClusterBusTest(unittest.TestCase):

    def test_cluster_url_is_rendered_as_a_value(self):
        deployment = named(render({"clusterBus": {"url": "nats://nats:4222"}}), "Deployment", SERVER)
        self.assertEqual(
            "nats://nats:4222", env_entry(deployment, "INGOT_CLUSTER_URL")["value"],
            "clusterBus.url must set INGOT_CLUSTER_URL",
        )

    def test_password_is_read_from_the_password_key_of_the_secret(self):
        values = {"clusterBus": {"url": "nats://nats:4222", "passwordSecret": "bus-password"}}
        deployment = named(render(values), "Deployment", SERVER)
        self.assertEqual(
            {"secretKeyRef": {"name": "bus-password", "key": "password"}},
            env_entry(deployment, "INGOT_CLUSTER_PASSWORD")["valueFrom"],
            "INGOT_CLUSTER_PASSWORD must come from the key `password` of the Secret",
        )

    def credentials_volume(self, deployment):
        volumes = [volume for volume in pod_spec(deployment)["volumes"]
                   if volume.get("secret", {}).get("secretName") == "nats-creds"]
        self.assertEqual(1, len(volumes), "the NATS credentials Secret must be mounted exactly once")
        return volumes[0]

    def test_credentials_secret_is_mounted_read_only(self):
        values = {"clusterBus": {"url": "nats://nats:4222", "credentialsSecret": "nats-creds"}}
        deployment = named(render(values), "Deployment", SERVER)
        volume = self.credentials_volume(deployment)
        mounts = [mount for mount in container(deployment)["volumeMounts"] if mount["name"] == volume["name"]]
        self.assertEqual(1, len(mounts), "the NATS credentials volume must have exactly one mount")
        self.assertTrue(mounts[0].get("readOnly"), "the NATS credentials must be mounted read-only")

    def test_credentials_variable_points_at_the_mounted_file(self):
        values = {"clusterBus": {"url": "nats://nats:4222", "credentialsSecret": "nats-creds"}}
        deployment = named(render(values), "Deployment", SERVER)
        volume = self.credentials_volume(deployment)
        mount_path = [mount for mount in container(deployment)["volumeMounts"]
                      if mount["name"] == volume["name"]][0]["mountPath"]
        self.assertEqual(
            f"{mount_path}/nats.creds", env_entry(deployment, "INGOT_CLUSTER_CREDENTIALS")["value"],
            "INGOT_CLUSTER_CREDENTIALS must name the nats.creds file inside the mount",
        )

    def test_unset_bus_renders_no_bus_variable_and_no_bus_volume(self):
        deployment = named(render(), "Deployment", SERVER)
        for name in ("INGOT_CLUSTER_URL", "INGOT_CLUSTER_PASSWORD", "INGOT_CLUSTER_CREDENTIALS"):
            self.assertIsNone(env_entry(deployment, name), f"{name} must not render without clusterBus values")
        secret_volumes = [volume for volume in pod_spec(deployment)["volumes"] if "secret" in volume]
        self.assertEqual([], secret_volumes, "no Secret volume may render without clusterBus values")

    def test_cluster_url_set_in_env_too_fails_naming_both_keys(self):
        values = {"clusterBus": {"url": "nats://nats:4222"},
                  "env": [{"name": "INGOT_CLUSTER_URL", "value": "nats://other:4222"}]}
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], values)
        self.assertNotEqual(0, code, "INGOT_CLUSTER_URL set twice must fail the render")
        self.assertIn("clusterBus.url", stderr, "the message must name clusterBus.url")
        self.assertIn("INGOT_CLUSTER_URL", stderr, "the message must name INGOT_CLUSTER_URL")


class TopologySpreadTest(unittest.TestCase):

    CONSTRAINT = {
        "maxSkew": 1,
        "topologyKey": "topology.kubernetes.io/zone",
        "whenUnsatisfiable": "DoNotSchedule",
        "labelSelector": {"matchLabels": {"app.kubernetes.io/name": "ingot"}},
    }

    def test_server_constraints_are_passed_through(self):
        values = {"deployment": {"topologySpreadConstraints": [self.CONSTRAINT]}}
        spec = pod_spec(named(render(values), "Deployment", SERVER))
        self.assertEqual([self.CONSTRAINT], spec["topologySpreadConstraints"],
                         "the server constraints must reach the pod spec unchanged")

    def test_dashboard_constraints_are_passed_through(self):
        values = dict(split_values(), dashboard={"topologySpreadConstraints": [self.CONSTRAINT]})
        spec = pod_spec(named(render(values), "Deployment", DASHBOARD))
        self.assertEqual([self.CONSTRAINT], spec["topologySpreadConstraints"],
                         "the dashboard constraints must reach the dashboard pod spec unchanged")

    def test_empty_constraints_render_no_field(self):
        manifests = render(split_values())
        for name in (SERVER, DASHBOARD):
            spec = pod_spec(named(manifests, "Deployment", name))
            self.assertNotIn("topologySpreadConstraints", spec,
                             f"{name} must render no topologySpreadConstraints by default")

    def test_server_constraints_do_not_leak_into_the_dashboard(self):
        values = dict(split_values(), deployment={"topologySpreadConstraints": [self.CONSTRAINT]})
        spec = pod_spec(named(render(values), "Deployment", DASHBOARD))
        self.assertNotIn("topologySpreadConstraints", spec,
                         "deployment.topologySpreadConstraints must not apply to the dashboard")


class ImageDigestTest(unittest.TestCase):

    def test_digest_renders_name_at_digest(self):
        values = {"image": {"digest": "sha256:abc", "tag": "1.2.0"}}
        self.assertEqual(
            f"{SERVER_IMAGE}@sha256:abc", container(named(render(values), "Deployment", SERVER))["image"],
            "image.digest must render name@digest",
        )

    def test_digest_replaces_the_tag(self):
        values = {"image": {"digest": "sha256:abc", "tag": "1.2.0"}}
        image = container(named(render(values), "Deployment", SERVER))["image"]
        self.assertNotIn("1.2.0", image, "the tag must not be rendered when a digest is set")

    def test_unset_digest_keeps_the_tag(self):
        image = container(named(render({"image": {"tag": "1.2.0"}}), "Deployment", SERVER))["image"]
        self.assertEqual(f"{SERVER_IMAGE}:1.2.0", image, "without a digest the image stays name:tag")


class SchemaTest(unittest.TestCase):

    def test_numeric_digest_is_rejected(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], {"image": {"digest": 12}})
        self.assertNotEqual(0, code, "a numeric digest must fail schema validation")
        self.assertIn("digest", stderr, "the schema error must name the digest key")

    def test_unknown_root_key_is_accepted(self):
        self.assertTrue(render({"somethingNew": True}), "the root object must stay open to unknown keys")

    def test_unknown_deployment_key_is_accepted(self):
        self.assertTrue(render({"deployment": {"somethingNew": True}}),
                        "the deployment object must stay open to unknown keys")

    def test_wrong_type_of_a_typed_bus_value_is_rejected(self):
        code, _, _ = helm(["template", RELEASE, CHART_DIR], {"clusterBus": {"url": 4222}})
        self.assertNotEqual(0, code, "clusterBus.url must be a string")


class NoLiteralSecretTest(unittest.TestCase):

    def test_every_secret_reference_renders_without_a_literal_value(self):
        values = {
            "database": {"existingSecret": "ingot-database"},
            "objectStorage": {"existingSecret": "s3-keys"},
            "clusterBus": {"url": "nats://nats:4222", "passwordSecret": "bus-password",
                           "credentialsSecret": "nats-creds"},
        }
        deployment = named(render(values), "Deployment", SERVER)
        for name in ("INGOT_LOCAL_DATABASE", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "INGOT_CLUSTER_PASSWORD"):
            self.assertNotIn("value", env_entry(deployment, name),
                             f"{name} must reference a Secret and render no literal value")


class HaFixtureTest(unittest.TestCase):

    def setUp(self):
        with open(HA_FIXTURE, encoding="utf-8") as handle:
            self.values = yaml.safe_load(handle)

    def test_fixture_renders_two_server_replicas(self):
        deployment = named(render(self.values), "Deployment", SERVER)
        self.assertEqual(2, deployment["spec"]["replicas"], "the HA fixture must render two server replicas")

    def test_fixture_renders_a_topology_spread_constraint(self):
        spec = pod_spec(named(render(self.values), "Deployment", SERVER))
        self.assertTrue(spec.get("topologySpreadConstraints"), "the HA fixture must spread the replicas")

    def test_fixture_renders_the_secret_references_and_the_digest(self):
        deployment = named(render(self.values), "Deployment", SERVER)
        self.assertIn("secretKeyRef", env_entry(deployment, "INGOT_LOCAL_DATABASE")["valueFrom"],
                      "the HA fixture must read the database from a Secret")
        self.assertIn("@sha256:", container(deployment)["image"], "the HA fixture must pin the image by digest")

    def test_fixture_passes_helm_lint_strict(self):
        code, stdout, stderr = helm(["lint", CHART_DIR, "--strict", "-f", HA_FIXTURE])
        self.assertEqual(0, code, f"helm lint --strict must pass with the HA fixture: {stdout}{stderr}")

    def test_fixture_stays_out_of_the_ci_directory(self):
        self.assertFalse(os.path.exists(os.path.join(CI_DIR, "ha-values.yaml")),
                         "the HA fixture must stay out of ci/, because kind cannot run its services")


if __name__ == "__main__":
    unittest.main()
