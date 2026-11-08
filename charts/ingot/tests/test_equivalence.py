"""Migration equivalence: the staging values of the upstream reposilite chart 1.4.5 render the same identity here.

The fixtures hold the staging values (fixtures/staging-values.yaml) and the identity fields that the upstream chart
renders from them (fixtures/upstream-1.4.5-identity.yaml). Each test renders this chart with the migration overrides
and compares one identity field. Nothing is read from a cluster, the clock or the network.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import os
import subprocess
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
CHART_DIR = os.path.dirname(TESTS_DIR)
HELM = os.environ.get("HELM", "helm")
STAGING_VALUES = os.path.join(TESTS_DIR, "fixtures", "staging-values.yaml")
IDENTITY = os.path.join(TESTS_DIR, "fixtures", "upstream-1.4.5-identity.yaml")
MIGRATION_OVERRIDES = ["--set", "nameOverride=reposilite", "--set", "fullnameOverride=reposilite"]


def load_identity():
    with open(IDENTITY, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def render(identity, extra_args):
    """Renders this chart with the staging values as release `reposilite` in the staging namespace."""
    command = [
        HELM, "template", identity["release"], CHART_DIR,
        "--namespace", identity["namespace"],
        "-f", STAGING_VALUES,
        "--set", "persistence.enabled=true",
    ]
    command.extend(extra_args)
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise AssertionError(f"helm template failed: {result.stderr}")
    return [doc for doc in yaml.safe_load_all(result.stdout) if doc]


def named(manifests, kind, name):
    matches = [doc for doc in manifests if doc.get("kind") == kind and doc["metadata"]["name"] == name]
    if len(matches) != 1:
        raise AssertionError(f"expected one {kind} named {name}, found {len(matches)}")
    return matches[0]


def server_deployment(manifests, identity):
    return named(manifests, "Deployment", identity["deploymentName"])


class MigrationEquivalenceTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.identity = load_identity()
        cls.manifests = render(cls.identity, MIGRATION_OVERRIDES)

    def test_selector_labels_equal_the_upstream_selector(self):
        selector = server_deployment(self.manifests, self.identity)["spec"]["selector"]["matchLabels"]
        self.assertEqual(
            self.identity["selectorLabels"],
            selector,
            "the Deployment selector is immutable, so it must equal the upstream selector",
        )

    def test_deployment_name_equals_the_upstream_name(self):
        name = server_deployment(self.manifests, self.identity)["metadata"]["name"]
        self.assertEqual(self.identity["deploymentName"], name, "the Deployment must keep the upstream name")

    def test_service_name_equals_the_upstream_name(self):
        name = named(self.manifests, "Service", self.identity["serviceName"])["metadata"]["name"]
        self.assertEqual(self.identity["serviceName"], name, "the Service must keep the upstream name")

    def test_service_selector_equals_the_upstream_selector(self):
        selector = named(self.manifests, "Service", self.identity["serviceName"])["spec"]["selector"]
        self.assertEqual(
            self.identity["selectorLabels"], selector, "the Service must select the pods of the upstream selector"
        )

    def test_persistent_volume_claim_name_equals_the_upstream_name(self):
        claim = named(self.manifests, "PersistentVolumeClaim", self.identity["persistentVolumeClaimName"])
        self.assertEqual(
            self.identity["persistentVolumeClaimName"],
            claim["metadata"]["name"],
            "the claim must keep the upstream name, so an existing volume is adopted",
        )

    def test_data_volume_mounts_the_upstream_claim(self):
        volumes = server_deployment(self.manifests, self.identity)["spec"]["template"]["spec"]["volumes"]
        data = [volume for volume in volumes if volume["name"] == "data"]
        self.assertEqual(1, len(data), "the pod must have exactly one data volume")
        self.assertEqual(
            self.identity["persistentVolumeClaimName"],
            data[0]["persistentVolumeClaim"]["claimName"],
            "the data volume must mount the upstream claim",
        )

    def test_container_port_equals_the_upstream_port(self):
        container = server_deployment(self.manifests, self.identity)["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual(
            self.identity["containerPort"],
            container["ports"][0]["containerPort"],
            "the container must listen on the upstream port",
        )

    def test_without_name_overrides_the_selector_differs(self):
        manifests = render(self.identity, [])
        deployments = [doc for doc in manifests if doc.get("kind") == "Deployment"]
        self.assertEqual(1, len(deployments), "the server must render one Deployment")
        selector = deployments[0]["spec"]["selector"]["matchLabels"]
        self.assertNotEqual(
            self.identity["selectorLabels"],
            selector,
            "without nameOverride the chart name ingot replaces reposilite, which is why the migration sets it",
        )


if __name__ == "__main__":
    unittest.main()
