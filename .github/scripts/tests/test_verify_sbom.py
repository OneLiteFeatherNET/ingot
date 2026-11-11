"""Tests for .github/scripts/verify_sbom.py.

The validation of the release SBOMs runs before a release is attested and on every pull request. Each test copies the
hand-written CycloneDX 1.6 fixture into its own temporary directory, changes one thing, and checks one behaviour of the
validation. No network, no clock, no generator and no Gradle or npm run is needed.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import json
import os
import sys
import tempfile
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
FIXTURE = os.path.join(TESTS_DIR, "fixtures", "sbom", "valid.cdx.json")

sys.path.insert(0, SCRIPTS_DIR)
import verify_sbom  # noqa: E402

ALPHA = "pkg:maven/org.example/alpha@1.0.0"
BETA = "pkg:maven/org.example/beta@2.0.0"
APP = "pkg:maven/org.example/app@1.1.1?type=jar"


def component_named(document, name):
    """Returns the component of the fixture with the given name."""
    for component in document["components"]:
        if component["name"] == name:
            return component
    raise AssertionError(f"the fixture has no component named '{name}'")


class SbomFixtureTest(unittest.TestCase):
    """Writes one variant of the fixture per call into a directory that only this test uses."""

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = directory.name

    def check(self, change=None, **options):
        """Applies the change to a copy of the fixture and returns the report of the validation."""
        with open(FIXTURE, encoding="utf-8") as handle:
            document = json.load(handle)
        if change is not None:
            change(document)
        path = os.path.join(self.directory, "sbom.cdx.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(document, handle)
        return verify_sbom.check_file(path, **options)


class ValidDocumentTest(SbomFixtureTest):

    def test_valid_document_passes(self):
        report = self.check()
        self.assertTrue(report.ok, f"the valid fixture must pass, found: {report.errors}")


class SpecVersionTest(SbomFixtureTest):

    def test_spec_version_other_than_1_6_fails(self):
        def use_spec_1_5(document):
            document["specVersion"] = "1.5"

        report = self.check(use_spec_1_5)

        self.assertFalse(report.ok)
        self.assertTrue(
            any("specVersion" in error for error in report.errors),
            f"the error must name specVersion, found: {report.errors}",
        )


class MetadataTest(SbomFixtureTest):

    def test_missing_timestamp_fails(self):
        def drop_timestamp(document):
            del document["metadata"]["timestamp"]

        report = self.check(drop_timestamp)

        self.assertFalse(report.ok)
        self.assertTrue(
            any("metadata.timestamp" in error for error in report.errors),
            f"the error must name metadata.timestamp, found: {report.errors}",
        )


class PurlTest(SbomFixtureTest):

    def test_component_without_purl_fails_and_names_the_component(self):
        def drop_purl(document):
            del component_named(document, "beta")["purl"]

        report = self.check(drop_purl)

        self.assertFalse(report.ok)
        self.assertTrue(
            any("beta" in error and "purl" in error for error in report.errors),
            f"the error must name the component and purl, found: {report.errors}",
        )


class BomRefTest(SbomFixtureTest):

    def test_duplicate_bom_ref_fails(self):
        def reuse_alpha_ref(document):
            component_named(document, "beta")["bom-ref"] = ALPHA

        report = self.check(reuse_alpha_ref)

        self.assertFalse(report.ok)
        self.assertTrue(
            any("duplicate" in error and ALPHA in error for error in report.errors),
            f"the error must say the bom-ref is duplicated, found: {report.errors}",
        )


class DependencyTest(SbomFixtureTest):

    def test_dangling_dependency_reference_fails_and_names_the_reference(self):
        missing = "pkg:maven/org.example/missing@0.1.0"

        def depend_on_missing(document):
            document["dependencies"][1]["dependsOn"] = [missing]

        report = self.check(depend_on_missing)

        self.assertFalse(report.ok)
        self.assertTrue(
            any(missing in error for error in report.errors),
            f"the error must name the dangling reference, found: {report.errors}",
        )


class LicenceShareTest(SbomFixtureTest):

    def test_licence_share_below_threshold_fails(self):
        def drop_beta_licence(document):
            del component_named(document, "beta")["licenses"]

        report = self.check(drop_beta_licence)

        self.assertFalse(report.ok)
        self.assertEqual(report.licence_share, 0.5)
        self.assertTrue(
            any("licence" in error for error in report.errors),
            f"the error must name the licence share, found: {report.errors}",
        )


class HashAndSupplierShareTest(SbomFixtureTest):

    def test_missing_hashes_and_suppliers_do_not_fail_the_report(self):
        def drop_hashes_and_suppliers(document):
            for component in document["components"]:
                component.pop("hashes", None)
                component.pop("supplier", None)

        report = self.check(drop_hashes_and_suppliers)

        self.assertTrue(report.ok, f"hash and supplier gaps are reported, not enforced: {report.errors}")
        self.assertEqual(report.hash_share, 0.0)
        self.assertEqual(report.supplier_share, 0.0)


class SchemaTest(SbomFixtureTest):

    def test_schema_violation_fails_and_names_the_path(self):
        def unknown_component_type(document):
            component_named(document, "alpha")["type"] = "not-a-type"

        report = self.check(unknown_component_type)

        self.assertFalse(report.ok)
        self.assertTrue(
            any("components" in error for error in report.errors),
            f"the schema error must name the path of the component, found: {report.errors}",
        )


if __name__ == "__main__":
    unittest.main()
