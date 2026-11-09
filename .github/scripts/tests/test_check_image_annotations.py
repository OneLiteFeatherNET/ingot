"""Tests for .github/scripts/check-image-annotations.sh.

The script checks the OCI annotations of a pushed image index and of its platform manifests. Each test writes its own
fixtures into a temporary directory, shaped like `docker buildx imagetools inspect --raw` output, and runs the script as
a subprocess. No registry, no network, no clock. Each test names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import json
import os
import subprocess
import tempfile
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(os.path.dirname(TESTS_DIR), "check-image-annotations.sh")

TITLE = "org.opencontainers.image.title"
DESCRIPTION = "org.opencontainers.image.description"
VENDOR = "org.opencontainers.image.vendor"
LICENSES = "org.opencontainers.image.licenses"
VERSION = "org.opencontainers.image.version"
BASE_NAME = "org.opencontainers.image.base.name"
BASE_DIGEST = "org.opencontainers.image.base.digest"

ANNOTATIONS = {
    TITLE: "Ingot",
    DESCRIPTION: "Repository management software for Maven artifacts in the JVM ecosystem",
    VENDOR: "OneLiteFeather",
    LICENSES: "Apache-2.0",
    VERSION: "3.5.29",
    BASE_NAME: "docker.io/library/eclipse-temurin:21-jre-noble",
    BASE_DIGEST: "sha256:" + "0" * 63 + "1",
}

AMD64 = "sha256:" + "a" * 64
ARM64 = "sha256:" + "b" * 64
ATTESTATION = "sha256:" + "c" * 64
PLATFORMS = ((AMD64, "amd64"), (ARM64, "arm64"))

MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json"


def without(annotations, key):
    """Returns a copy of the annotations with one key removed."""
    copy = dict(annotations)
    del copy[key]
    return copy


def with_value(annotations, key, value):
    """Returns a copy of the annotations with one key set to a value."""
    copy = dict(annotations)
    copy[key] = value
    return copy


def platform_descriptor(digest, architecture):
    return {
        "mediaType": MANIFEST_MEDIA_TYPE,
        "digest": digest,
        "size": 1024,
        "platform": {"architecture": architecture, "os": "linux"},
    }


def attestation_descriptor():
    return {
        "mediaType": MANIFEST_MEDIA_TYPE,
        "digest": ATTESTATION,
        "size": 566,
        "annotations": {
            "vnd.docker.reference.digest": AMD64,
            "vnd.docker.reference.type": "attestation-manifest",
        },
        "platform": {"architecture": "unknown", "os": "unknown"},
    }


def index_document(annotations, attestation=True):
    manifests = [platform_descriptor(digest, arch) for digest, arch in PLATFORMS]
    if attestation:
        manifests.append(attestation_descriptor())
    return {
        "schemaVersion": 2,
        "mediaType": "application/vnd.oci.image.index.v1+json",
        "manifests": manifests,
        "annotations": annotations,
    }


def manifest_document(annotations):
    return {
        "schemaVersion": 2,
        "mediaType": MANIFEST_MEDIA_TYPE,
        "config": {"mediaType": "application/vnd.oci.image.config.v1+json", "digest": "sha256:" + "d" * 64, "size": 2048},
        "layers": [{"mediaType": "application/vnd.oci.image.layer.v1.tar+gzip", "digest": "sha256:" + "e" * 64, "size": 4096}],
        "annotations": annotations,
    }


class CheckImageAnnotationsTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(os.path.isfile(SCRIPT), msg=f"the check script does not exist yet: {SCRIPT}")
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.index_path = os.path.join(directory.name, "index.json")
        self.manifest_dir = os.path.join(directory.name, "manifests")
        os.mkdir(self.manifest_dir)

    def manifest_path(self, digest):
        return os.path.join(self.manifest_dir, digest.replace(":", "-") + ".json")

    def write_json(self, path, document):
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(document, handle)

    def write_fixture(self, index_annotations=ANNOTATIONS, manifest_annotations=ANNOTATIONS, attestation=False):
        """Writes a raw index and one raw manifest file per platform manifest it lists."""
        self.write_json(self.index_path, index_document(index_annotations, attestation=attestation))
        for digest, _ in PLATFORMS:
            self.write_json(self.manifest_path(digest), manifest_document(manifest_annotations))

    def run_check(self):
        return subprocess.run(
            [SCRIPT, self.index_path, self.manifest_dir],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_complete_index_and_manifests_pass(self):
        self.write_fixture()

        result = self.run_check()

        self.assertEqual(
            result.returncode, 0,
            msg=f"all seven annotations are present, so the check must pass; stderr: {result.stderr}",
        )

    def test_missing_title_on_index_fails_and_names_key(self):
        self.write_fixture(index_annotations=without(ANNOTATIONS, TITLE))

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="an index without a title must fail the check")
        self.assertIn(TITLE, result.stderr, msg=f"the failure must name the missing key; stderr: {result.stderr}")

    def test_empty_version_on_index_fails_and_names_key(self):
        self.write_fixture(index_annotations=with_value(ANNOTATIONS, VERSION, ""))

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="an empty version annotation must fail the check")
        self.assertIn(VERSION, result.stderr, msg=f"the failure must name the empty key; stderr: {result.stderr}")

    def test_missing_base_digest_on_index_fails_and_names_key(self):
        self.write_fixture(index_annotations=without(ANNOTATIONS, BASE_DIGEST))

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="an index without base.digest must fail the check")
        self.assertIn(BASE_DIGEST, result.stderr, msg=f"the failure must name base.digest; stderr: {result.stderr}")

    def test_missing_base_name_on_platform_manifest_fails_and_names_key(self):
        self.write_fixture(manifest_annotations=without(ANNOTATIONS, BASE_NAME))

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="a platform manifest without base.name must fail the check")
        self.assertIn(BASE_NAME, result.stderr, msg=f"the failure must name base.name; stderr: {result.stderr}")

    def test_platform_manifest_without_file_fails_and_names_digest(self):
        self.write_fixture()
        os.remove(self.manifest_path(ARM64))

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="a platform manifest that was not fetched must fail the check")
        self.assertIn(ARM64, result.stderr, msg=f"the failure must name the digest; stderr: {result.stderr}")

    def test_attestation_manifest_without_file_is_skipped(self):
        self.write_fixture(attestation=True)

        result = self.run_check()

        self.assertEqual(
            result.returncode, 0,
            msg=f"attestation manifests carry no annotations and must be skipped; stderr: {result.stderr}",
        )

    def test_index_that_is_not_json_fails_with_message(self):
        with open(self.index_path, "w", encoding="utf-8") as handle:
            handle.write("<html>registry error</html>")

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="an index that is not JSON must fail the check")
        self.assertIn("not valid JSON", result.stderr, msg=f"the failure must say why; stderr: {result.stderr}")

    def test_index_without_platform_manifests_fails(self):
        document = index_document(ANNOTATIONS)
        document["manifests"] = [attestation_descriptor()]
        self.write_json(self.index_path, document)

        result = self.run_check()

        self.assertNotEqual(result.returncode, 0, msg="an index with no platform manifest must fail the check")
        self.assertIn("no platform manifest", result.stderr, msg=f"stderr: {result.stderr}")

    def test_unknown_argument_prints_usage(self):
        result = subprocess.run(
            [SCRIPT, "--bogus", self.index_path, self.manifest_dir],
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertNotEqual(result.returncode, 0, msg="an unknown argument must fail")
        self.assertIn("usage:", result.stderr, msg=f"an unknown argument must print usage; stderr: {result.stderr}")


if __name__ == "__main__":
    unittest.main()
