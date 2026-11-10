"""Golden renders of the defaults.

Three inputs are rendered with `helm template`: the all-in-one default with no values, the split mode and the staging
fixture with the migration overrides. Each render is reduced to its parsed manifests sorted by kind and name, and the
result must equal the stored golden under fixtures/golden/. A change that alters a default fails here, so it has to be
made on purpose by regenerating the golden in the same commit.

No cluster, no network, no clock: each test runs helm in a subprocess and reads one file.

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
GOLDEN_DIR = os.path.join(TESTS_DIR, "fixtures", "golden")
STAGING_VALUES = os.path.join(TESTS_DIR, "fixtures", "staging-values.yaml")
MIGRATION_OVERRIDES = ["--set", "nameOverride=reposilite", "--set", "fullnameOverride=reposilite"]


def canonical(manifests):
    """Sorts the parsed manifests by kind and name, so that the order of helm output does not matter."""
    return sorted(manifests, key=lambda doc: (doc.get("kind", ""), doc["metadata"]["name"]))


def render(args):
    """Runs helm template with the given arguments and returns the canonical manifests."""
    command = [HELM, "template"] + args
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise AssertionError(f"helm template failed with exit code {result.returncode}: {result.stderr}")
    return canonical([doc for doc in yaml.safe_load_all(result.stdout) if doc])


def load_golden(name):
    with open(os.path.join(GOLDEN_DIR, name), encoding="utf-8") as handle:
        return list(yaml.safe_load_all(handle))


class GoldenRenderTest(unittest.TestCase):

    def test_all_in_one_defaults_match_the_golden(self):
        rendered = render(["ingot", CHART_DIR, "--namespace", "reposilite-staging"])
        self.assertEqual(
            load_golden("allinone.yaml"), rendered,
            "the all-in-one defaults changed; regenerate the golden only when the change is intended",
        )

    def test_split_mode_defaults_match_the_golden(self):
        rendered = render(["ingot", CHART_DIR, "--namespace", "reposilite-staging", "--set", "mode=split"])
        self.assertEqual(
            load_golden("split.yaml"), rendered,
            "the split defaults changed; regenerate the golden only when the change is intended",
        )

    def test_staging_migration_render_matches_the_golden(self):
        rendered = render([
            "reposilite", CHART_DIR, "--namespace", "reposilite-staging",
            "-f", STAGING_VALUES, "--set", "persistence.enabled=true",
        ] + MIGRATION_OVERRIDES)
        self.assertEqual(
            load_golden("staging-migration.yaml"), rendered,
            "the staging migration render changed; regenerate the golden only when the change is intended",
        )


if __name__ == "__main__":
    unittest.main()
