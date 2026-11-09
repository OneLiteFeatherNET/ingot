"""Unit tests for the identity comparison of the upgrade rehearsal.

The rehearsal records the claim UID, the sha1 of an artifact and the status of a token before and after each
step. `rehearsal.compare.compare_identity` is a pure function of the two records: it returns the list of differences,
and an empty list when nothing changed. No cluster, no network, no clock and no files are involved.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import unittest

from rehearsal.compare import compare_identity

CLAIM_UID = "6f1c2a4e-0000-4000-8000-000000000001"
OTHER_CLAIM_UID = "9b7d3e51-0000-4000-8000-000000000002"
SHA1 = "3a7bd3e2360a3d80c3e4b8d4b1f1f2e0c9a8b7d6"
OTHER_SHA1 = "0000000000000000000000000000000000000001"


def identity(claim_uid=CLAIM_UID, artifact_sha1=SHA1, token_status="valid"):
    return {"claim_uid": claim_uid, "artifact_sha1": artifact_sha1, "token_status": token_status}


class CompareIdentityTest(unittest.TestCase):

    def test_equal_records_report_no_difference(self):
        problems = compare_identity(identity(), identity())
        self.assertEqual([], problems, f"identical records must report no difference, found {problems}")

    def test_new_claim_uid_is_reported_as_a_renamed_claim(self):
        problems = compare_identity(identity(), identity(claim_uid=OTHER_CLAIM_UID))
        self.assertEqual(1, len(problems), f"a changed claim UID must be one difference, found {problems}")
        self.assertIn("claim", problems[0], "the difference must name the claim")

    def test_changed_artifact_sha1_is_reported(self):
        problems = compare_identity(identity(), identity(artifact_sha1=OTHER_SHA1))
        self.assertEqual(1, len(problems), f"a changed sha1 must be one difference, found {problems}")
        self.assertIn("sha1", problems[0], "the difference must name the artifact sha1")

    def test_revoked_token_is_reported(self):
        problems = compare_identity(identity(), identity(token_status="revoked"))
        self.assertEqual(1, len(problems), f"a revoked token must be one difference, found {problems}")
        self.assertIn("token", problems[0], "the difference must name the token")


if __name__ == "__main__":
    unittest.main()
