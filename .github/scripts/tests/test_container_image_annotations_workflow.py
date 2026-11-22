"""Shape tests for the image annotations in the container job of .github/workflows/release-please.yml.

The job pushes every image and then runs check-image-annotations.sh against each pushed reference, which demands the
base image name and digest on the index and on every platform manifest. An image whose metadata step leaves them out
is pushed and then fails the run, on every nightly. These tests catch that before a push does.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import os
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
GITHUB_DIR = os.path.dirname(os.path.dirname(TESTS_DIR))
REPOSITORY_ROOT = os.path.dirname(GITHUB_DIR)
RELEASE_WORKFLOW = os.path.join(GITHUB_DIR, "workflows", "release-please.yml")

BASE_KEYS = ("org.opencontainers.image.base.name", "org.opencontainers.image.base.digest")


def container_steps():
    """Returns the steps of the container job, read fresh for every test."""
    with open(RELEASE_WORKFLOW, encoding="utf-8") as handle:
        return yaml.safe_load(handle)["jobs"]["container"]["steps"]


def metadata_steps():
    return [step for step in container_steps() if "docker/metadata-action" in (step.get("uses") or "")]


def annotation_keys(step):
    lines = (step.get("with") or {}).get("annotations") or ""
    return {line.split("=", 1)[0].strip() for line in lines.splitlines() if "=" in line}


class ContainerImageAnnotationsTest(unittest.TestCase):
    def test_every_image_annotates_its_base_image(self):
        steps = metadata_steps()
        self.assertTrue(steps, msg="the container job must compute image metadata with docker/metadata-action")
        for step in steps:
            missing = [key for key in BASE_KEYS if key not in annotation_keys(step)]
            self.assertEqual(missing, [], msg=f"step '{step.get('name')}' must annotate {missing}, "
                                              "or the annotation check fails after the push")

    def test_every_image_annotates_index_and_manifests(self):
        for step in metadata_steps():
            levels = (step.get("env") or {}).get("DOCKER_METADATA_ANNOTATIONS_LEVELS", "")
            self.assertEqual(set(levels.split(",")), {"manifest", "index"},
                             msg=f"step '{step.get('name')}' must annotate the index and every platform manifest")

    def test_every_built_dockerfile_is_read_for_its_base_image(self):
        base_step = next((step for step in container_steps() if step.get("id") == "base"), None)
        self.assertIsNotNone(base_step, msg="the container job must read the base images in a step with id 'base'")
        for step in container_steps():
            if "docker/build-push-action" not in (step.get("uses") or ""):
                continue
            dockerfile = (step.get("with") or {}).get("file", "Dockerfile")
            self.assertTrue(os.path.isfile(os.path.join(REPOSITORY_ROOT, dockerfile)),
                            msg=f"step '{step.get('name')}' builds {dockerfile}, which does not exist")
            self.assertIn(f"run_stage_reference {dockerfile})", base_step["run"],
                          msg=f"the base step must read the run stage of {dockerfile}")


if __name__ == "__main__":
    unittest.main()
