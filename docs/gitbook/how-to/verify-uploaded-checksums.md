---
description: Make Ingot compare an uploaded checksum file with the artifact it describes, choose between warn and reject, read the audit events, and understand the limits of the check.
---

# Verify uploaded checksums

A build that deploys to Ingot sends an artifact and then its checksum files. If the connection drops, a proxy rewrites the body or a client is buggy, the stored checksum can disagree with the stored artifact. Ingot stores both without a word by default, and the damage shows up later in a consumer's build. This guide shows how to make Ingot check each checksum file when it arrives, and what the check does and does not cover.

The check is off by default. Nothing changes for an instance until you set a policy.

## Prerequisites

* An instance you can configure, and a repository that takes uploads. A proxy or a group repository takes no uploads, so the policy has no effect on it.
* An account that can read the audit log, if you want to review the events. See [Review the audit log](review-audit-log.md).

## Choose a policy

The policy is set per repository with `uploadChecksumPolicy` in the [repository settings](../reference/repository-settings.md#upload-checksum-policy). A repository that leaves the key out takes the instance default, which is set with `INGOT_UPLOAD_CHECKSUM_POLICY` and is `ignore` when unset. A repository value always wins over the instance default.

```json
{
  "id": "releases",
  "redeployment": false,
  "uploadChecksumPolicy": "reject"
}
```

| Value | A checksum file that matches | A checksum file that does not match |
| --- | --- | --- |
| `ignore` | Stored | Stored, without a check. This is the default. |
| `warn` | Stored | Stored, and the mismatch is logged and written to the audit log with outcome `accepted`. |
| `reject` | Stored | Refused with `400`. Nothing is stored for the checksum file, the artifact stays as it is, and the refusal is written to the audit log with outcome `rejected`. |

Start with `warn` on an existing repository. It records every mismatch and changes no upload. Switch to `reject` when the audit log has shown no mismatch that you did not expect.

To turn the check on for every repository that does not set its own value, set the variable in the environment of the instance:

```bash
INGOT_UPLOAD_CHECKSUM_POLICY=warn
```

The variable is read at start. The older `REPOSILITE_UPLOAD_CHECKSUM_POLICY` is read when the `INGOT_` name is not set. An unknown value stops the start with a message that names the variable. See [Environment variables](../reference/environment-variables.md#upload-checksum-policy).

## What is checked

A deploy of a file whose name ends in `.md5`, `.sha1`, `.sha256` or `.sha512` is a checksum file. Ingot finds the file it describes, which is the same path without the suffix, and digests the stored copy with the algorithm of the suffix. The digest in the upload must equal that digest. The upload may hold the bare digest, or the form that `sha1sum` writes, which is the digest, two spaces and a file name. Upper and lower case are the same.

Other files are never digested. A `.jar`, a `.pom`, a `.asc` or a `maven-metadata.xml` upload is stored as before. A `maven-metadata.xml.sha1` is checked against the stored `maven-metadata.xml`, because a metadata file is redeployed on every release and its checksum follows it.

## The order that is checked

The check compares a checksum file with an artifact that is already stored. So it only covers the order artifact first, checksum second. Maven and Gradle deploy in this order.

A checksum file that arrives before its artifact is stored without a check, under every policy, because there is nothing to compare it with yet. A client that deploys its checksums first is therefore not protected by this check. The check can be extended to that order later, but it needs a change in how the storage commits files, so it is not part of this policy.

An artifact that never gets a checksum file is never checked either. The policy protects the pair of artifact and checksum file that is deployed, not every artifact on its own. Ingot does not require checksum files, and a client that sends none is not affected.

Artifacts that were stored before the policy was turned on are not checked. Ingot does not scan or backfill existing files.

## Refused uploads

Under `reject` the answer to a refused checksum file is `400` with a message that names the algorithm, for example `SHA1 checksum file lib-1.0.0.jar.sha1 does not match the stored artifact, so it was refused`. The problem type is `urn:ingot:problem:upload-checksum-mismatch`, which tells the refusal apart from other `400` answers. See [API errors](../reference/api-errors.md#checksum-file-refused).

A refused redeployment still answers `409`, as it does without the policy. When a checksum file already exists and redeployment is off, the conflict is reported first and the checksum is not compared.

Ingot writes the file of the refused upload nowhere, not even to a temporary location that stays behind. The stored artifact is never replaced by a checksum refusal.

## Reading the audit events

Every mismatch under `warn` and `reject` is written to the audit log as `UPLOAD_CHECKSUM_MISMATCH`. The row names the repository, the path of the checksum file and the uploader. Its details hold `algorithm` (`md5`, `sha1`, `sha256` or `sha512`) and `outcome` (`accepted` or `rejected`). Digests are never written.

To list the mismatches of one repository in the dashboard, choose the checksum mismatch action in the **Action** select of the audit log and enter the repository name in the filter form. See [Review the audit log](review-audit-log.md). A match writes no event, so an empty result under `warn` means that every checksum file that was checked matched.

Refusals are also counted in the deploy metric `ingot_deploy_results_total` with the result `checksum_mismatch`. That series appears after the first refusal, so an instance that never refuses a file shows no change in its metrics.

## Cost on S3

On a repository with S3 storage, the check reads the stored artifact once for each checksum file that is deployed. A client that deploys `.md5`, `.sha1`, `.sha256` and `.sha512` reads a large artifact four times. The read is egress from the bucket and it takes time that grows with the artifact size. The ETag of an object is not used instead, because for a multipart upload it is not the digest of the content.

If the egress matters for your bill, keep `ignore` on the large repositories, or configure the client to deploy only one checksum algorithm. A filesystem repository reads the artifact from its own disk, which costs much less.

## Known limits

* A checksum file that arrives before its artifact is not checked. See [the order that is checked](#the-order-that-is-checked).
* With `redeployment` on, a second upload of an artifact can replace it after the digest was read and before the checksum file is stored. The stored checksum then describes the older bytes. Ingot does not serialise writers across replicas, so this is the same last writer wins behaviour as for the artifact itself, and the check does not close it. Keep `redeployment` off on repositories where this matters.
* Files stored before the policy was turned on are not checked, and nothing is scanned.
* Signatures (`.asc`), `maven-metadata.xml` content, and checksums sent in request headers are not checked.

## Related

* [Repository settings](../reference/repository-settings.md#upload-checksum-policy)
* [Environment variables](../reference/environment-variables.md#upload-checksum-policy)
* [Audit events](../reference/audit-events.md)
* [Check checksums of proxied files](create-proxy-repositories.md#check-checksums), which does the same check for the copies a proxy stores
