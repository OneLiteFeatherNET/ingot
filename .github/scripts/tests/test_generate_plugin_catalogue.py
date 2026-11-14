"""Tests for .github/scripts/generate_plugin_catalogue.py.

The generator adds released plugin versions to ingot-plugin-catalogue.json and yanks versions. Every test builds its own
mirror of the Maven layout in a temporary directory, passes the generation time as an argument and checks the result
with assertions. No test reads the clock, uses the network or shares state with another test.

Run from the repository root:

    python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v
"""

import contextlib
import hashlib
import importlib.util
import io
import json
import os
import shutil
import socket
import tempfile
import unittest
from unittest import mock

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPTS_DIR = os.path.dirname(TESTS_DIR)
GENERATOR_PATH = os.path.join(SCRIPTS_DIR, "generate_plugin_catalogue.py")
FIXTURE_CATALOGUE = os.path.join(TESTS_DIR, "fixtures", "plugin_catalogue", "ingot-plugin-catalogue.json")

GROUP_PATH = os.path.join("net", "onelitefeather", "ingot")
CATALOGUE = "ingot-plugin-catalogue.json"
GENERATED = "2026-11-14T10:00:00Z"
FIXTURE_VERSION = "3.5.28"
FIXTURE_JAR_BYTES = b"fixture jar 3.5.28\n"
NEW_VERSION = "3.5.29"
NEW_JAR_BYTES = b"fixture jar 3.5.29\n"


def load_generator():
    """Loads the generator from its file, so the tests do not depend on sys.path."""
    spec = importlib.util.spec_from_file_location("generate_plugin_catalogue", GENERATOR_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


generator = load_generator()


def jar_path(repository, version, plugin="opensearch-plugin"):
    """The Maven path of a plugin jar inside a local mirror."""
    return os.path.join(repository, GROUP_PATH, plugin, version, f"{plugin}-{version}.jar")


def write_jar(repository, version, content, plugin="opensearch-plugin"):
    path = jar_path(repository, version, plugin)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(content)


def read_bytes(path):
    with open(path, "rb") as handle:
        return handle.read()


def read_catalogue(repository):
    with open(os.path.join(repository, CATALOGUE), encoding="utf-8") as handle:
        return json.load(handle)


def sha256_hex(content):
    return hashlib.sha256(content).hexdigest()


class GeneratorTestCase(unittest.TestCase):
    """Gives every test its own empty mirror and runs the generator's entry point against it."""

    def setUp(self):
        self.temp = tempfile.mkdtemp(prefix="plugin-catalogue-test-")
        self.addCleanup(shutil.rmtree, self.temp, True)
        self.repository = os.path.join(self.temp, "mirror")
        os.makedirs(self.repository)
        self.last_stderr = ""

    def seed_catalogue(self):
        """Copies the committed fixture catalogue, which lists 3.5.28, and its jar into the mirror."""
        shutil.copy(FIXTURE_CATALOGUE, os.path.join(self.repository, CATALOGUE))
        write_jar(self.repository, FIXTURE_VERSION, FIXTURE_JAR_BYTES)

    def publish(self, version=NEW_VERSION, generated=GENERATED):
        return self.run_generator("publish", "--repository", self.repository, "--version", version, "--generated", generated)

    def yank(self, version):
        return self.run_generator("yank", "--repository", self.repository, "--plugin", "opensearch-plugin", "--version", version)

    def run_generator(self, *argv):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr), contextlib.redirect_stdout(io.StringIO()):
            code = generator.main(list(argv))
        self.last_stderr = stderr.getvalue()
        return code

    def catalogue_bytes(self):
        return read_bytes(os.path.join(self.repository, CATALOGUE))


class AppendOnlyCatalogueTest(GeneratorTestCase):

    def test_appends_a_new_version_to_the_catalogue(self):
        self.seed_catalogue()
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(), self.last_stderr)

        coordinates = [entry["coordinates"] for entry in read_catalogue(self.repository)["plugins"]]
        self.assertEqual(
            [
                "net.onelitefeather.ingot:opensearch-plugin:3.5.28",
                "net.onelitefeather.ingot:opensearch-plugin:3.5.29",
            ],
            coordinates,
            "the new release is appended after the listed versions",
        )

    def test_keeps_the_listed_entry_unchanged_when_appending(self):
        self.seed_catalogue()
        listed = read_catalogue(self.repository)["plugins"][0]
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(), self.last_stderr)

        self.assertEqual(listed, read_catalogue(self.repository)["plugins"][0], "a listed entry is never rewritten")

    def test_repeated_release_with_the_same_digest_leaves_the_catalogue_unchanged(self):
        self.seed_catalogue()
        before = self.catalogue_bytes()

        self.assertEqual(0, self.publish(version=FIXTURE_VERSION), self.last_stderr)

        self.assertEqual(before, self.catalogue_bytes(), "a re-run with the same digest does not rewrite the file")

    def test_listed_version_with_another_digest_stops_the_run_and_writes_nothing(self):
        self.seed_catalogue()
        before = self.catalogue_bytes()
        write_jar(self.repository, FIXTURE_VERSION, b"rebuilt jar with other bytes\n")

        code = self.publish(version=FIXTURE_VERSION)

        self.assertNotEqual(0, code, "a changed digest of a listed version must stop the run")
        self.assertIn(FIXTURE_VERSION, self.last_stderr, "the message names the version whose digest differs")
        self.assertEqual(before, self.catalogue_bytes(), "nothing is written when the run stops")

    def test_version_listed_twice_in_the_catalogue_is_refused(self):
        self.seed_catalogue()
        document = read_catalogue(self.repository)
        document["plugins"].append(dict(document["plugins"][0]))
        with open(os.path.join(self.repository, CATALOGUE), "w", encoding="utf-8") as handle:
            json.dump(document, handle)
        before = self.catalogue_bytes()

        code = self.publish(version=FIXTURE_VERSION)

        self.assertNotEqual(0, code, "a catalogue that lists one version twice is refused, never repaired silently")
        self.assertEqual(before, self.catalogue_bytes())

    def test_missing_jar_stops_the_run(self):
        self.seed_catalogue()
        before = self.catalogue_bytes()

        code = self.publish()

        self.assertNotEqual(0, code, "a release without its jar in the mirror must not be listed")
        self.assertEqual(before, self.catalogue_bytes(), "nothing is written when the jar is missing")


class YankTest(GeneratorTestCase):

    def test_yank_changes_only_the_yanked_flag(self):
        self.seed_catalogue()
        before = read_catalogue(self.repository)

        self.assertEqual(0, self.yank(FIXTURE_VERSION), self.last_stderr)

        after = read_catalogue(self.repository)
        self.assertTrue(after["plugins"][0]["yanked"], "the version is marked yanked")
        after["plugins"][0]["yanked"] = before["plugins"][0]["yanked"]
        self.assertEqual(before, after, "no other field, entry or top-level value changes")

    def test_yank_of_a_version_that_is_not_listed_is_refused(self):
        self.seed_catalogue()
        before = self.catalogue_bytes()

        code = self.yank("9.9.9")

        self.assertNotEqual(0, code, "only listed versions can be yanked")
        self.assertEqual(before, self.catalogue_bytes())


class DeterminismTest(GeneratorTestCase):

    def test_two_runs_with_the_same_inputs_produce_byte_identical_output(self):
        self.seed_catalogue()
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)
        self.assertEqual(0, self.publish(), self.last_stderr)
        first = self.catalogue_bytes()

        second_temp = tempfile.mkdtemp(prefix="plugin-catalogue-second-")
        self.addCleanup(shutil.rmtree, second_temp, True)
        second = os.path.join(second_temp, "mirror")
        os.makedirs(second)
        shutil.copy(FIXTURE_CATALOGUE, os.path.join(second, CATALOGUE))
        write_jar(second, FIXTURE_VERSION, FIXTURE_JAR_BYTES)
        write_jar(second, NEW_VERSION, NEW_JAR_BYTES)
        self.assertEqual(0, self.run_generator(
            "publish", "--repository", second, "--version", NEW_VERSION, "--generated", GENERATED,
        ), self.last_stderr)

        self.assertEqual(first, read_bytes(os.path.join(second, CATALOGUE)))

    def test_generated_time_is_taken_from_the_argument(self):
        self.seed_catalogue()
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(generated="2026-12-01T06:30:00Z"), self.last_stderr)

        self.assertEqual("2026-12-01T06:30:00Z", read_catalogue(self.repository)["generated"])

    def test_generated_time_that_is_not_utc_iso_8601_is_refused(self):
        self.seed_catalogue()
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        code = self.publish(generated="14.11.2026 10:00")

        self.assertNotEqual(0, code, "a locale date would make the output depend on the machine that runs the job")


class EntryContentTest(GeneratorTestCase):

    def test_new_entry_carries_the_sha256_of_the_jar_bytes(self):
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(), self.last_stderr)

        entry = read_catalogue(self.repository)["plugins"][0]
        self.assertEqual(sha256_hex(NEW_JAR_BYTES), entry["sha256"], "the digest is computed from the bytes of the jar")
        self.assertEqual("net.onelitefeather.ingot:opensearch-plugin:3.5.29", entry["coordinates"])
        self.assertFalse(entry["yanked"])

    def test_compatibility_covers_the_release_version_up_to_the_next_patch(self):
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(), self.last_stderr)

        compatibility = read_catalogue(self.repository)["plugins"][0]["compatibility"]
        self.assertEqual({"min": "3.5.29", "below": "3.5.30"}, compatibility["ingot"])

    def test_new_entry_declares_plugin_api_one_and_publisher_metadata(self):
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(), self.last_stderr)

        entry = read_catalogue(self.repository)["plugins"][0]
        self.assertEqual(1, entry["compatibility"]["pluginApi"])
        self.assertEqual("opensearch-plugin", entry["id"])
        self.assertEqual("OneLiteFeather", entry["publisher"])
        self.assertTrue(entry["name"], "the store refuses an entry without a name")

    def test_catalogue_declares_schema_one(self):
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        self.assertEqual(0, self.publish(), self.last_stderr)

        self.assertEqual(1, read_catalogue(self.repository)["schema"])

    def test_version_that_is_not_three_dotted_numbers_is_refused(self):
        write_jar(self.repository, "3.6.0-rc1", NEW_JAR_BYTES)

        code = self.publish(version="3.6.0-rc1")

        self.assertNotEqual(0, code, "a pre-release has no single-version compatibility range")
        self.assertFalse(os.path.exists(os.path.join(self.repository, CATALOGUE)), "nothing is written for a refused version")


class NoNetworkTest(GeneratorTestCase):

    def test_run_opens_no_socket(self):
        self.seed_catalogue()
        write_jar(self.repository, NEW_VERSION, NEW_JAR_BYTES)

        with mock.patch.object(socket, "socket", side_effect=AssertionError("the generator must not open a socket")):
            self.assertEqual(0, self.publish(), self.last_stderr)


if __name__ == "__main__":
    unittest.main()
