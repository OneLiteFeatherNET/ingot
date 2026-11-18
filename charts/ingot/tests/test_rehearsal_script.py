"""Unit tests for the pure steps of the upgrade rehearsal script.

The script runs Helm, kubectl and the HTTP API on a kind cluster, which these tests never touch. They cover the steps
that decide things from text: the checksum of the upstream chart, the revision of the upstream chart in the release
history and the local port that kubectl port-forward prints.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import hashlib
import contextlib
import io
import os
import tempfile
import unittest
from unittest import mock

from rehearsal import rehearse as rehearsal_script
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


CLAIM_UID = "6f1c2a4e-0000-4000-8000-000000000001"
SHA1 = "3a7bd3e2360a3d80c3e4b8d4b1f1f2e0c9a8b7d6"


def record(token_status="valid"):
    return {"claim_uid": CLAIM_UID, "artifact_sha1": SHA1, "token_status": token_status, "mounted_claims": ["reposilite"]}


class SuccessReportTest(unittest.TestCase):

    def test_passing_rehearsal_prints_the_three_stages_in_order(self):
        # given: a rehearsal that reports no difference and observed the same values at the three stages
        observed = [("before the upgrade", record()), ("after the upgrade", record()), ("after the rollback", record())]
        out = io.StringIO()

        # when: the script finishes, with the cluster calls replaced by no-ops
        with mock.patch.object(rehearsal_script, "rehearse", return_value=([], observed)), \
                mock.patch.object(rehearsal_script, "run", return_value=""), \
                contextlib.redirect_stdout(out):
            code = rehearsal_script.main(["--ingot-image", "ingot:ci"])

        # then: the exit code is 0 and each stage prints its claim UID, artifact sha1 and token status
        printed = out.getvalue().splitlines()
        self.assertEqual(0, code)
        self.assertEqual(
            [
                f"before the upgrade: claim UID {CLAIM_UID}, artifact sha1 {SHA1}, token status valid",
                f"after the upgrade: claim UID {CLAIM_UID}, artifact sha1 {SHA1}, token status valid",
                f"after the rollback: claim UID {CLAIM_UID}, artifact sha1 {SHA1}, token status valid",
            ],
            printed[:3],
            f"the report must list the three stages in order, found {printed}",
        )
