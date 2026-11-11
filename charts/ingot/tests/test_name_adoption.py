"""Render tests for the names of a release without cluster access.

The chart adopts the names of the upstream reposilite chart only when the cluster holds an object of the release that
carries the upstream name. `helm template` has no cluster, so the lookup returns nothing and these tests pin the
fallback: the Ingot names of today, the explicit override pair, and a NOTES.txt without the adoption line. The adoption
itself needs a cluster and is covered by the upgrade rehearsal.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import unittest

import yaml

from test_chart import CHART_DIR, helm, named, of_kind

NAMESPACE = "reposilite-staging"
OVERRIDE_PAIR = {"nameOverride": "reposilite", "fullnameOverride": "reposilite"}


def render_release(release, values=None):
    code, stdout, stderr = helm(["template", release, CHART_DIR, "--namespace", NAMESPACE], values)
    if code != 0:
        raise AssertionError(f"helm template {release} failed with exit code {code}: {stderr}")
    return [doc for doc in yaml.safe_load_all(stdout) if doc]


def server_selector_name(manifests):
    deployment = named(manifests, "Deployment", named_deployment_name(manifests))
    return deployment["spec"]["selector"]["matchLabels"]["app.kubernetes.io/name"]


def named_deployment_name(manifests):
    names = [doc["metadata"]["name"] for doc in of_kind(manifests, "Deployment")]
    if len(names) != 1:
        raise AssertionError(f"expected one Deployment, found {names}")
    return names[0]


class FallbackNamesTest(unittest.TestCase):
    def test_release_reposilite_without_overrides_renders_the_ingot_names(self):
        manifests = render_release("reposilite")
        self.assertEqual(
            "reposilite-ingot",
            named_deployment_name(manifests),
            "without cluster access the chart keeps the Ingot name of the release reposilite",
        )

    def test_release_reposilite_without_overrides_renders_the_ingot_claim_name(self):
        manifests = render_release("reposilite")
        self.assertEqual(
            "reposilite-ingot",
            named(manifests, "PersistentVolumeClaim", "reposilite-ingot")["metadata"]["name"],
            "the claim must carry the same name as the Deployment that mounts it",
        )

    def test_release_reposilite_without_overrides_keeps_the_ingot_selector_name(self):
        manifests = render_release("reposilite")
        self.assertEqual(
            "ingot",
            server_selector_name(manifests),
            "the server selector stays app.kubernetes.io/name: ingot when no upstream object is found",
        )

    def test_existing_ingot_release_mirror_renders_mirror_ingot(self):
        manifests = render_release("mirror")
        self.assertEqual(
            "mirror-ingot",
            named_deployment_name(manifests),
            "a release without any upstream name keeps the <release>-ingot name",
        )

    def test_existing_ingot_release_mirror_keeps_the_ingot_selector_name(self):
        manifests = render_release("mirror")
        self.assertEqual("ingot", server_selector_name(manifests), "the selector name is ingot for an Ingot release")


class OverridePairTest(unittest.TestCase):
    def test_override_pair_renders_the_upstream_deployment_name(self):
        manifests = render_release("reposilite", OVERRIDE_PAIR)
        self.assertEqual(
            "reposilite",
            named_deployment_name(manifests),
            "nameOverride and fullnameOverride keep working as the documented way to choose the upstream names",
        )

    def test_override_pair_renders_the_upstream_selector_name(self):
        manifests = render_release("reposilite", OVERRIDE_PAIR)
        self.assertEqual("reposilite", server_selector_name(manifests), "the override pair selects the upstream name")


class NotesWithoutAdoptionTest(unittest.TestCase):
    def test_notes_print_no_adoption_line_without_cluster_access(self):
        code, stdout, stderr = helm(
            ["install", "reposilite", CHART_DIR, "--namespace", NAMESPACE, "--dry-run=client"]
        )
        self.assertEqual(0, code, f"helm install --dry-run=client failed: {stderr}")
        self.assertNotIn(
            "upstream names",
            stdout,
            "NOTES.txt must not announce an adoption that the render did not take",
        )


if __name__ == "__main__":
    unittest.main()
