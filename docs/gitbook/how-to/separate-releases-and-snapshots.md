---
description: Keep release builds and snapshot builds in separate repositories with a version policy, freeze a repository on its own with the read-only flag, and check what a refused deploy means.
---

# Separate releases and snapshots

Release builds and snapshot builds often end up in the same repository, or a snapshot lands in the release repository by mistake. Ingot can refuse the wrong kind of version at deploy time, and it can freeze a repository on its own. This guide sets up a release repository and a snapshot repository, checks the setup with a deploy, and freezes a repository that must not change.

## Before you begin

* Be a manager of the instance, so that you can change the maven settings.
* Know which repositories hold your release builds and which hold snapshots, for example `releases` and `snapshots`.
* Read the [repository settings reference](../reference/repository-settings.md#version-policy-and-read-only) once. It lists every status code and the write policies.

Nothing is rewritten by this change. A repository that sets neither key keeps accepting both kinds of version, and no stored file is deleted when you set a policy.

## Set the version policy

Open **Settings**, tab **Maven**, and choose the repository. Under **Writes**, set **Version policy**:

* `releases` gets **Release only**. A deploy into a `-SNAPSHOT` version directory is refused.
* `snapshots` gets **Snapshot only**. A deploy of a release version is refused. The artifact metadata, `maven-metadata.xml` and its checksums, is still accepted, so Maven can update the metadata of the artifact there.

In the settings document the same choice is the key `versionPolicy`:

```json
{
  "repositories": [
    { "id": "releases", "versionPolicy": "RELEASE" },
    { "id": "snapshots", "versionPolicy": "SNAPSHOT" }
  ]
}
```

Choose **Not set, accepts both** to remove the key again. A proxy and a group take no version policy, so the choice is not offered for them, see [Repository types](../reference/repository-types.md).

## Check the setup with a deploy

Deploy one release and one snapshot to each repository, with the tool your builds use. For Maven, the `distributionManagement` of your project points at the repository. Two of the four deploys are expected to fail:

| Deploy | Repository | Answer |
| --- | --- | --- |
| `com/acme/app/1.0/app-1.0.jar` | `releases` | Accepted |
| `com/acme/app/1.1-SNAPSHOT/app-1.1-20260101.jar` | `releases` | `400` |
| `com/acme/app/1.1-SNAPSHOT/app-1.1-20260101.jar` | `snapshots` | Accepted |
| `com/acme/app/1.0/app-1.0.jar` | `snapshots` | `400` |

The `400` message names the repository, the policy and the version, for example `Repository 'releases' has version policy RELEASE and does not accept the snapshot version 1.1-SNAPSHOT`. Nothing is stored for a refused deploy, so the file does not appear in the repository afterwards.

A repository that does not allow redeployment still answers `409` when the same file is deployed twice, as it did before the policy existed.

## Freeze a repository

A repository that has reached its final state can be frozen on its own, without the instance-wide read-only mode. Under **Writes**, set **Write policy** to **Read-only**, or set `"readOnly": true` in the settings document.

A frozen repository refuses every deploy, every delete and every POM generation with `405`, and the message names the repository. Downloads and searches keep working. Scheduled cleanup skips the repository, and the cleanup preview lists it as skipped.

The instance-wide read-only mode is a different switch. While it is on, every write answers `503`, in every repository, and that answer comes first. See [Freeze writes for maintenance](freeze-writes-for-maintenance.md).

To unfreeze, choose **Allow redeploy** or **Allow once** again. Choosing **Read-only** keeps the redeploy choice of the repository, so switching back restores it.

## Read the write policy in the list

The repository list shows the write policy of every repository that takes uploads: **Allow redeploy**, **Allow once** or **Read-only**. The same value is in the API as `writePolicy`, see [HTTP API](../reference/http-api.md). A repository without an upload policy is a proxy or a group.

## Know what each answer means

| Answer | When it happens | What to do |
| --- | --- | --- |
| `400` with the policy in the message | A deploy names a version the policy refuses | Deploy the version to the repository that accepts it, or change the policy |
| `405` naming the repository as read-only | A deploy or delete reaches a frozen repository | Unfreeze the repository, or deploy to another one |
| `409` | The same file is deployed again, and redeployment is off | Deploy a new version, or allow redeployment |
| `503` | The instance-wide read-only mode is on | Wait until the mode is turned off |

## Roll back

A settings document that contains `versionPolicy` or `readOnly` is read by an older version of Ingot or Reposilite with those keys ignored. After a rollback the policies are no longer enforced, and a frozen repository accepts writes again until the settings are saved by a version that knows the keys. Remove the keys before a rollback if the policies must stay off, see [Roll back an upgrade](roll-back-an-upgrade.md).
