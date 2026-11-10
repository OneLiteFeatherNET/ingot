"""The dependencies of the smoke test and the values that point at them, checked without a cluster.

charts/ingot/tests/ha/dependencies.yaml holds MariaDB, Floci (the S3 emulator) and NATS, the bucket job and the
throwaway Secrets. charts/ingot/tests/ha/values.yaml is the values file of the two replicas. A renamed Service or a
host name that no longer matches fails here, not in the kind job.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import os
import unittest

import yaml
from test_chart import CHART_DIR, SERVER, helm, named, of_kind, render

import ha_smoke

HA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ha")
DEPENDENCIES = os.path.join(HA_DIR, "dependencies.yaml")
VALUES = os.path.join(HA_DIR, "values.yaml")
NAMESPACE = "ingot-ha"


def load_documents(path):
    with open(path, encoding="utf-8") as handle:
        return [document for document in yaml.safe_load_all(handle) if document]


def load_values(path=VALUES):
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def images_of(documents):
    images = []
    for document in documents:
        if document.get("kind") in ("Deployment", "Job"):
            pod = document["spec"]["template"]["spec"]
            images += [entry["image"] for entry in pod.get("containers", []) + pod.get("initContainers", [])]
    return images


class DependencyManifestTest(unittest.TestCase):

    def setUp(self):
        self.documents = load_documents(DEPENDENCIES)

    def service_named(self, name):
        matches = [doc for doc in of_kind(self.documents, "Service") if doc["metadata"]["name"] == name]
        self.assertEqual(1, len(matches), f"the manifest must define exactly one Service named {name}")
        return matches[0]

    def test_mariadb_service_is_defined(self):
        self.assertEqual(3306, self.service_named("mariadb")["spec"]["ports"][0]["port"], "MariaDB listens on 3306")

    def test_floci_service_is_defined_on_the_s3_port(self):
        self.assertEqual(4566, self.service_named("floci")["spec"]["ports"][0]["port"], "Floci serves S3 on 4566")

    def test_nats_service_is_defined_on_the_client_port(self):
        self.assertEqual(4222, self.service_named("nats")["spec"]["ports"][0]["port"], "NATS serves clients on 4222")

    def test_the_bucket_job_creates_the_smoke_bucket(self):
        jobs = [doc for doc in of_kind(self.documents, "Job") if doc["metadata"]["name"] == "ingot-smoke-bucket"]
        self.assertEqual(1, len(jobs), "the manifest must define the bucket job")
        command = jobs[0]["spec"]["template"]["spec"]["containers"][0]["args"]
        self.assertIn("s3://ingot-smoke", command, "the job must create the bucket the server is configured with")

    def test_every_image_is_pinned_by_digest_and_not_latest(self):
        for image in images_of(self.documents):
            self.assertIn("@sha256:", image, f"the image {image} must be pinned by digest")
            self.assertNotIn(":latest", image, f"the image {image} must not use latest")

    def test_the_database_and_object_storage_secrets_are_defined(self):
        secrets = {doc["metadata"]["name"] for doc in of_kind(self.documents, "Secret")}
        self.assertTrue({"ingot-database", "ingot-s3-keys"} <= secrets,
                        "the values file names these two Secrets, so the manifest must create them")


class ValuesFileTest(unittest.TestCase):

    def setUp(self):
        self.values = load_values()
        self.documents = load_documents(DEPENDENCIES)

    def test_the_values_render_two_server_replicas(self):
        deployment = named(render(self.values), "Deployment", SERVER)
        self.assertEqual(2, deployment["spec"]["replicas"], "the smoke test runs two replicas")

    def test_the_image_is_the_one_the_job_loads_into_kind_by_tag(self):
        self.assertEqual({"name": "ingot", "tag": "ci"}, {"name": self.values["image"]["name"], "tag": self.values["image"]["tag"]},
                         "kind has the image under this tag, so no digest may be set")
        self.assertNotIn("digest", self.values["image"], "a locally loaded image has no registry digest")

    def test_the_database_secret_is_the_one_the_manifest_defines(self):
        secret = self.values["database"]["existingSecret"]
        self.assertIn(secret, {doc["metadata"]["name"] for doc in of_kind(self.documents, "Secret")},
                      "the values file must reference a Secret that the dependency manifest creates")

    def test_the_database_connection_names_the_mariadb_service(self):
        secret = next(doc for doc in of_kind(self.documents, "Secret") if doc["metadata"]["name"] == "ingot-database")
        host = secret["stringData"]["database"].split()[1].split(":")[0]
        self.assertEqual(f"mariadb.{NAMESPACE}.svc.cluster.local", host,
                         "the connection string must name the MariaDB Service in the smoke namespace")

    def test_the_cluster_bus_url_names_the_nats_service(self):
        host = self.values["clusterBus"]["url"].split("://", 1)[1].split(":")[0]
        self.assertEqual(f"nats.{NAMESPACE}.svc.cluster.local", host,
                         "the bus URL must name the NATS Service in the smoke namespace")

    def test_the_driver_endpoint_for_floci_names_the_floci_service(self):
        host = ha_smoke.S3_ENDPOINT.split("://", 1)[1].split(":")[0]
        self.assertEqual(f"floci.{NAMESPACE}.svc.cluster.local", host,
                         "the storage endpoint the driver writes must name the Floci Service")

    def test_the_values_pass_helm_lint_strict(self):
        code, stdout, stderr = helm(["lint", CHART_DIR, "--strict", "-f", VALUES])
        self.assertEqual(0, code, f"helm lint --strict must pass with the smoke values: {stdout}{stderr}")

    def test_the_values_stay_out_of_the_ci_directory(self):
        self.assertFalse(os.path.exists(os.path.join(CHART_DIR, "ci", "ha-values.yaml")),
                         "the chart job installs ci/ values, so the smoke values must not appear there")


if __name__ == "__main__":
    unittest.main()
