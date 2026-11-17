---
description: Store a repository in a Google Cloud Storage bucket through the optional gcs-storage plugin, with Application Default Credentials, GKE workload identity or a service account key, and the limits of the plugin.
---

# Use Google Cloud Storage

A repository can keep its files in a Google Cloud Storage bucket. The provider is not part of the Ingot image. It is the optional `gcs-storage` plugin, which registers the storage type `gcs`. Without the plugin, a repository of type `gcs` does not load, and the other repositories are not affected.

Use the S3 provider with HMAC keys when the bucket is reachable through the XML interoperability API and you do not need workload identity. See [Use S3 storage](use-s3-storage.md#limits) for its limits. This plugin uses the native JSON API, so the credentials come from Google Cloud and no HMAC key is stored.

## Before you begin

* A bucket in Google Cloud, and a project for it.
* An identity that holds the role `roles/storage.objectUser` on the bucket, granted at bucket scope. The role has not yet been confirmed against a real bucket. If the first write is refused with a 403 that names the role, grant `roles/storage.objectAdmin` on the bucket instead and report it.
* The plugin jar `gcs-storage-plugin.jar` of the release, or the Maven artifact `net.onelitefeather.ingot:gcs-storage-plugin` of the same version.

## Steps

1. Copy the jar into the `plugins` directory that the server reads at start.

2. Create the repository with `type` set to `gcs` and the settings of the next section. The keys are stored with the repository settings, as the [storage provider reference](../../references/repositories/storage-providers.md#google-cloud-storage-provider) describes.

3. Start the server and check the repository on the dashboard. The repository is ready when its probe answers. A probe that reports a missing permission names the bucket, and a bucket that does not exist is reported as missing.

## Settings example

Application Default Credentials, which is the default:

```json
{
  "type": "gcs",
  "projectId": "my-project",
  "bucket": "ingot-artifacts",
  "prefix": "releases",
  "authentication": "adc",
  "chunkSize": 8388608
}
```

A service account key, for a server outside Google Cloud:

```json
{
  "type": "gcs",
  "bucket": "ingot-artifacts",
  "authentication": "service-account-key",
  "credentialsJson": "<the service account key as JSON>"
}
```

{% hint style="warning" %}
The key is stored with the repository settings in the database, as the S3 keys are. The secret references of the S3 page are not tested for this key yet, so check them on your installation before you rely on them. Limit who can read the database.
{% endhint %}

## Settings

| Key | Default | Meaning |
|:----|:--------|:--------|
| `type` | `gcs` | Fixed value, always written. |
| `projectId` | empty | Project for billing and quota. Empty leaves the client default. |
| `bucket` | empty | Required. The bucket that holds the repository. |
| `prefix` | empty | Optional prefix of every object name. Leading and trailing slashes are removed. |
| `authentication` | `adc` | `adc` uses Application Default Credentials, which include GKE workload identity. `service-account-key` uses the key of `credentialsJson`. |
| `credentialsJson` | empty | The service account key as JSON. Required with `service-account-key`, and refused with `adc`. It is a secret and is never shown in logs or responses. |
| `chunkSize` | `8388608` (8 MiB) | Bytes per resumable upload chunk. A multiple of 256 KiB between 256 KiB and 64 MiB. |

A repository that fails one of these rules does not load, and the failure names the key.

## GKE workload identity

Bind a Google service account to the Kubernetes service account of the Ingot pod, and give that Google service account the role on the bucket.

```bash
gcloud iam service-accounts add-iam-policy-binding \
  ingot@my-project.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:my-project.svc.id.goog[ingot/ingot]"

gcloud storage buckets add-iam-policy-binding gs://ingot-artifacts \
  --member "serviceAccount:ingot@my-project.iam.gserviceaccount.com" \
  --role roles/storage.objectUser
```

Annotate the Kubernetes service account, then use `authentication: "adc"` with no key:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ingot
  namespace: ingot
  annotations:
    iam.gke.io/gcp-service-account: ingot@my-project.iam.gserviceaccount.com
```

No Ingot setting names the service account. The client finds it through the metadata server of the node.

## Limits

* **Removal is not atomic.** Removing a directory deletes its objects one by one. A failure part way leaves the rest in place and is reported. Run the removal again to finish it.
* **Abandoned uploads expire.** An upload that fails before it is finished creates no object. Its resumable session is left to expire on the service side, and the plugin does not cancel it.
* **Archived and cold objects** are read as they are. A read of an object in an archive class fails with the error of the service. Restore the object in Google Cloud first.
* **No lifecycle, autoclass or retention management** is done by the plugin. Configure them on the bucket.
* **No metadata cache.** Every request reads the object metadata from the bucket.
* **No conditional create.** Two instances that write the same file at the same time leave the content of the later write, as the S3 provider does.
* **No quota.** The plugin reports usage as unknown and never refuses an upload for lack of space. The `quota` setting applies to the file system provider only.
* **Shared buckets.** Two repositories that use the same bucket and overlapping prefixes are refused at start, because they would see each other's files.

## Measurements

The shaded jar of the release that these figures describe:

| Build | Size |
|:------|:-----|
| With the relocation of Guava, protobuf and gax | 48,484,081 bytes |
| Without the relocation | 47,705,380 bytes |

The size is high because the Google client brings its gRPC transport classes. The HTTP path needs part of them, so they stay in the jar. Pruning the rest is a follow-up.

## Verification status

| Check | Status |
|:------|:-------|
| Interoperability run of the S3 tests against a real bucket | Not run yet. |
| Role `roles/storage.objectUser` on a bucket, bound at bucket scope | Not confirmed on a real bucket. See Before you begin. |
| Service account key and GKE workload identity against a real bucket | Not run yet. Planned once a throwaway bucket exists. |
| Behaviour against the emulator `fake-gcs-server` | Run in the continuous integration with every change of the plugin. Permissions are not modelled by the emulator. |

## Result

Each file of the repository exists as an object under `<prefix>/` in the bucket. A download through the repository URL returns the same bytes that the bucket holds.
