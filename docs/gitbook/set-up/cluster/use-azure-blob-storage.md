---
description: How to keep the files of a repository in an Azure Blob container. Install the Azure storage plugin, grant the role, configure the repository, and the limits of the provider.
---

# Use Azure Blob storage

The `azure-blob` storage provider keeps the files of a repository in one container of an Azure storage account. It is an optional plugin: the server image does not contain it, and a repository that names `azure-blob` without the plugin is unavailable until the plugin is installed.

## Install the plugin

Copy the jar `azure-storage-plugin.jar` into the `plugins` directory of the data directory, `/app/data/plugins` in the container image, and restart the server. The plugin shows up as `azure-storage` in the plugin list.

Until the jar is installed, a repository of type `azure-blob` is not loaded. The server reports `Unknown storage provider 'azure-blob'` for it, its settings are kept unchanged, and the other repositories keep working.

## Grant access

The identity of the server needs a data role on the container. The role `Storage Blob Data Contributor` covers reading, writing and deleting. A read-only mirror needs `Storage Blob Data Reader` instead.

Without the role, the readiness check of the server reports `authorization` with the role name. A file request that is refused is answered with `500`, not `404`, so that an outage is never mistaken for a missing file.

## Configure a repository

The settings go in the `storageProvider` block of the repository, with `type` set to `azure-blob`.

```json
{
  "type": "azure-blob",
  "accountName": "mystorageaccount",
  "container": "repositories",
  "prefix": "maven",
  "authentication": "default-credential"
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `type` | `azure-blob` | Fixed value. |
| `accountName` | empty | The storage account. Required unless `endpoint` names the service. |
| `container` | empty | The container that holds the repository. It must exist. |
| `prefix` | empty | Optional prefix of every blob name, so that several repositories can share one container. |
| `endpoint` | empty | The Blob endpoint. Empty means `https://<accountName>.blob.core.windows.net`. Set it for sovereign clouds and for local emulators. |
| `authentication` | `default-credential` | One of `default-credential`, `account-key`, `sas` or `connection-string`. |
| `blockSize` | `8388608` | The size in bytes of one block of an upload. |
| `accountKey` | empty | The account key, for `account-key`. Sensitive: masked in the dashboard. |
| `sasToken` | empty | A SAS token for the container, for `sas`. Sensitive. A user delegation SAS can be revoked with the identity. |
| `connectionString` | empty | The connection string of the account, for `connection-string`. Sensitive. |

`default-credential` uses the identity of the environment: a managed identity, a workload identity in AKS, environment variables or a service principal. Prefer it. A repository whose mode lacks its secret is refused at start-up, and the message names the field, never its value.

## Limits

- **No quota.** The provider does not report usage or a quota. The dashboard shows the repository as unlimited.
- **Removal is not atomic.** Removing a directory deletes its entries one by one. A failure part way leaves some of them, and running the removal again completes it.
- **Last writer wins.** Two writers of the same file in different replicas are not coordinated by the storage. The last commit is the file.
- **Uncommitted blocks.** A failed upload leaves uncommitted blocks. They are never visible to readers, and Azure removes them by itself after its retention period.
- **Archive tier is not read.** Blobs in the archive tier are not rehydrated and cannot be read.
- **No presigned redirects.** Every file is streamed through the server, as it is for S3.
- **Hierarchical namespace accounts are not claimed.** The provider is verified against flat namespace accounts through the Blob endpoint. An account with a hierarchical namespace is supported only after the manual checklist below has passed against it.

## Local tests with Azurite

Azurite is the official emulator of the Blob service. Its well-known account is `devstoreaccount1`, and it authenticates with the public development key of the emulator. Use it for tests only.

```json
{
  "type": "azure-blob",
  "endpoint": "http://127.0.0.1:10000/devstoreaccount1",
  "accountName": "devstoreaccount1",
  "container": "repositories",
  "authentication": "account-key",
  "accountKey": "<the development key of Azurite>"
}
```

## Manual checklist before production

Azurite is not Azure. Before you rely on the provider, run these checks against a real account:

1. A flat namespace account: upload, read, range read, list and remove a repository.
2. Two replicas of the server on one container, with different prefixes, and with one prefix shared on purpose.
3. A denied identity: remove the role and check that the readiness reports `authorization` and the file requests answer `500`.
4. A hierarchical namespace account, only if the account type is used. Until this check passes, the provider is not claimed for that account type.
