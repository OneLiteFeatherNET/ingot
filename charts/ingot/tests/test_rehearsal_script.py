"""Unit tests for the pure steps of the upgrade rehearsal script.

The script runs Helm, kubectl and the HTTP API on a kind cluster, which these tests never touch. They cover the steps
that decide things from text: the checksum of the upstream chart, the revision of the upstream chart in the release
history and the local port that kubectl port-forward prints.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import hashlib
import os
import tempfile
import unittest

from rehearsal.rehearse import RehearsalFailure, forwarded_port, upstream_revision, verify_sha256

UPSTREAM_CHART = "reposilite-1.4.5"


class UpstreamRevisionTest(unittest.TestCase):

    def test_revision_of_the_upstream_chart_is_returned(self):
        history = [
            {"revision": 1, "chart": UPSTREAM_CHART},
            {"revision": 2, "chart": "ingot-1.1.1"},
        ]
        self.assertEqual(1, upstream_revision(history), "the upstream revision must be the one of chart reposilite-1.4.5")

    def test_missing_upstream_revision_is_a_failure(self):
        history = [{"revision": 1, "chart": "ingot-1.1.1"}]
        with self.assertRaises(RehearsalFailure, msg="a history without the upstream chart cannot be rolled back to"):
            upstream_revision(history)


class ForwardedPortTest(unittest.TestCase):

    def test_local_port_is_read_from_the_forwarding_line(self):
        line = "Forwarding from 127.0.0.1:41234 -> 8080\n"
        self.assertEqual(41234, forwarded_port(line), "the port must be the one kubectl bound on loopback")

    def test_other_output_has_no_forwarded_port(self):
        self.assertIsNone(forwarded_port("Handling connection for 41234\n"), "a line without a forwarding has no port")


class VerifySha256Test(unittest.TestCase):

    def test_file_with_the_expected_digest_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "chart.tgz")
            with open(path, "wb") as handle:
                handle.write(b"chart bytes")
            verify_sha256(path, hashlib.sha256(b"chart bytes").hexdigest())

    def test_file_with_another_digest_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "chart.tgz")
            with open(path, "wb") as handle:
                handle.write(b"other bytes")
            with self.assertRaises(RehearsalFailure, msg="a chart that does not match its pinned digest must not be used"):
                verify_sha256(path, hashlib.sha256(b"chart bytes").hexdigest())


if __name__ == "__main__":
    unittest.main()
