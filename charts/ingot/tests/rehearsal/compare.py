"""Identity comparison of the upgrade rehearsal, as a pure function of two records.

A record holds the claim UID, the sha1 of the artifact and the status of the token, taken before or after a step of
the rehearsal. The function returns one description per difference, and an empty list when nothing changed.
"""


def compare_identity(before, after):
    problems = []
    if before["claim_uid"] != after["claim_uid"]:
        problems.append(
            f"claim UID changed from {before['claim_uid']} to {after['claim_uid']}: the claim was renamed or recreated"
        )
    if before["artifact_sha1"] != after["artifact_sha1"]:
        problems.append(
            f"artifact sha1 changed from {before['artifact_sha1']} to {after['artifact_sha1']}"
        )
    if before["token_status"] != after["token_status"]:
        problems.append(
            f"token status changed from {before['token_status']} to {after['token_status']}"
        )
    return problems
