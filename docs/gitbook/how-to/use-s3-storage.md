---
description: Store a repository in an S3 bucket with AWS, MinIO or Ceph RGW (Rook), including path-style access, region, and a bucket shared by several repositories.
---

# Use S3 storage

Each repository has its own storage provider. A repository can keep its files on the server disk or in an S3 bucket, so one instance can mix both.

## Before you begin

* A bucket that Ingot can use. Ingot creates the bucket on start when it is missing, unless you turn that off in step 2.
* An access key and a secret key with read, write and delete rights on the bucket. If you leave both empty, Ingot uses the default credential chain of the AWS SDK, for example the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables of the Ingot process.
* The endpoint URL if the service is not Amazon S3. It must include the scheme, for example `http://minio.storage.svc:9000`.

{% hint style="warning" %}
Without references, the S3 keys are stored as plain text with the repository settings in the database. Anyone who can read the database can read them. The settings API hides them only when `hideSettingsSecrets` is enabled.

To keep a key out of the database, turn on settings secret references with `INGOT_SETTINGS_SECRET_REFERENCES=true`, list the file directory in `INGOT_SETTINGS_SECRET_FILES`, and set the secret key to a reference such as `${file:/run/secrets/s3-secret-key}`. The database then stores the reference and not the key. See [Settings secret references](../reference/configuration-and-startup.md#settings-secret-references).
{% endhint %}

## Steps

1. Create the bucket, or confirm that it exists.

2. Only if the bucket must not be created by Ingot, pass this system property to the Java process. Docker users set it in `JAVA_OPTS`.

   ```bash
   -Dreposilite.s3.skip-bucket-creation=true
   ```

   With this property the bucket must exist before Ingot starts.

3. Only for path-style services, such as MinIO and Ceph RGW, pass this system property. Docker users set it in `JAVA_OPTS`.

   ```bash
   -Dreposilite.s3.pathStyleAccessEnabled=true
   ```

   Path style puts the bucket name in the URL path, for example `http://minio:9000/my-bucket/com/example/lib.jar`. The default is virtual-host style, which needs a DNS name per bucket.

4. Open the repository in the dashboard at `Settings`, then `Maven`, then the repository. Select the S3 option of the storage provider and fill in the fields. The table below lists them.

5. Save. Ingot rebuilds its repositories when the settings change.

6. Check the result. Deploy a small file and download it again. For a private repository, add the token credentials to both requests.

   ```bash
   curl -u "<token-name>:<token-secret>" -T test.txt \
     https://repo.example.org/releases/tests/test.txt

   curl -u "<token-name>:<token-secret>" \
     https://repo.example.org/releases/tests/test.txt
   ```

   The upload needs the `deploy` permission on the repository. Then list the bucket with your S3 tools. The object key is `tests/test.txt`, with the key prefix and the repository name in front of it if you set them (see [Share a bucket](#share-a-bucket-between-repositories)).

## Settings

| Dashboard label | Key | Default | Notes |
|:----------------|:----|:--------|:------|
| Bucket | `bucketName` | empty | The bucket name. |
| Endpoint | `endpoint` | empty | Empty uses AWS. Otherwise a full URL. |
| Access key | `accessKey` | empty | Used together with the secret key. |
| Secret key | `secretKey` | empty | Used together with the access key. |
| Region | `region` | empty | Empty uses the `AWS_REGION` environment variable of the Ingot process, and `reposilite` if that is not set. |
| Key Prefix | `prefix` | empty | Added in front of every object key. |
| Shared Bucket | `sharedBucket` | off | Puts each repository under its own name inside the bucket. |
| Local Metadata Cache | `metadataCacheSettings` | off | Caches object metadata inside one instance. Other instances do not see the cache. Mutable metadata is never cached, and with a cluster bus an entry is used for at most 300 seconds. |

For AWS, set the region of your bucket.

## Credentials from the workload identity

Ingot passes credentials to the S3 client only when both `accessKey` and `secretKey` are set. When either one is empty, Ingot passes no static credentials, and the default credential chain of the AWS SDK applies. A key that is set alone is not used. To run without static keys, leave both empty and give the pod an identity that the chain reads.

### IAM roles for service accounts (IRSA)

Annotate the service account of Ingot with the ARN of the IAM role. The chart renders the annotation from `serviceAccount.annotations`, and only when the chart creates the service account (`serviceAccount.create: true`, the default):

```yaml
serviceAccount:
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::<account-id>:role/<role-name>
```

The role must allow the S3 actions on the bucket, and its trust policy must name the service account of the release. Keep `accessKey` and `secretKey` empty in the repository settings.

If you point `serviceAccount.name` at a service account that already exists, add the annotation to that service account yourself. The chart does not change it.

### EKS Pod Identity

Pod Identity needs no annotation on the service account. Create the association between the role and the service account of the release in AWS. Keep `accessKey` and `secretKey` empty, as above.

### Region

The region of the repository is used first. When it is empty, Ingot reads `AWS_REGION` from the environment of the pod. The chart can supply it with `objectStorage.existingSecret`, see [Run on Kubernetes](run-on-kubernetes.md#use-s3-for-repositories).

For the fields of a repository, see [Storage providers](../reference/storage-providers.md).

## Large uploads

Ingot does not copy an upload to its temporary directory first. It sends the bytes to the bucket as they arrive:

* A file that ends within its first 8 MiB is sent in one request, as before.
* A larger file is sent as a multipart upload. Ingot sends parts of 8 MiB and holds one part in memory at a time. The last part may be smaller.

The object is the same in both cases: the same key, the same content type and no extra metadata. Other tools read it like any other object.

A multipart upload has at most 10,000 parts, so one file can be about 78 GiB at most. A larger file fails with the generic write error, and its upload is aborted.

Each upload above 8 MiB holds one 8 MiB part in the heap while the part is sent. When many large uploads run at once, count them in the heap size.

If a part or the completion fails, Ingot aborts the multipart upload, so the bucket keeps no incomplete upload. A server that is killed in the middle of an upload cannot abort it. Its parts stay in the bucket, unlisted, and cost storage until a lifecycle rule removes them. This rule removes incomplete uploads that are older than 7 days and no completed object:

```json
{
  "Rules": [
    {
      "ID": "abort-incomplete-multipart-uploads",
      "Status": "Enabled",
      "Filter": {"Prefix": ""},
      "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 7}
    }
  ]
}
```

```bash
aws s3api put-bucket-lifecycle-configuration --bucket <bucket> --lifecycle-configuration file://lifecycle.json
```

This call replaces all lifecycle rules of the bucket. If the bucket has rules already, add this one to them instead.

### Upload through the temporary file

Set `INGOT_S3_BUFFER_TO_DISK=true` to write every upload to a temporary file first and send it in one request, as before. Ingot reads `REPOSILITE_S3_BUFFER_TO_DISK` when the variable is not set. The setting is read at start, so restart Ingot after changing it, and it applies to every S3 repository of the instance. Any value other than `true` or `false` stops the start and names the variable.

Use it when the storage service does not accept multipart uploads, or when the heap is too small for one 8 MiB part per concurrent upload. The temporary directory then needs room for the largest upload again.

Google Cloud Storage through its S3 interoperability API was not checked for multipart uploads. If uploads above 8 MiB fail there, set `INGOT_S3_BUFFER_TO_DISK=true`.

## Ceph RGW and Rook

These steps were not tested against a live Rook cluster. Check the service name and port in your cluster.

1. Find the RGW service of the object store. Rook names it after the store, for example `rook-ceph-rgw-<store-name>`.

   ```bash
   kubectl get svc -n <namespace>
   ```

2. Set the endpoint to the in-cluster address of that service, with the port from step 1, for example `http://rook-ceph-rgw-<store-name>.<namespace>.svc:<port>`.

3. Turn on path style with the property from step 3 of the steps above. A cluster service name cannot serve the per-bucket DNS names that virtual-host style needs.

4. Create the bucket and an access key for it with your Ceph tooling, or let Ingot create the bucket if the key may create buckets.

{% hint style="warning" %}
Test one upload, one download and one delete against the Ceph endpoint before you move production data.
{% endhint %}

## Share a bucket between repositories

By default each S3 repository writes to the root of its bucket, so each repository needs its own bucket. Two settings let several repositories, or other services, use one bucket.

* `prefix` is added in front of every object key. Use it to keep Ingot's objects apart from other data in the same bucket.
* `sharedBucket` puts each repository under its own name, so repositories do not see each other's files.

| `prefix` | `sharedBucket` | Object key of `com/example/lib.jar` in repository `releases` |
|:---------|:---------------|:-------------------------------------------------------------|
| empty | off | `com/example/lib.jar` (default) |
| `reposilite` | off | `reposilite/com/example/lib.jar` |
| empty | on | `releases/com/example/lib.jar` |
| `reposilite` | on | `reposilite/releases/com/example/lib.jar` |

Repositories that share a bucket must not write to overlapping key spaces. Enable `sharedBucket` on all of them, or give each one a different `prefix`. Ingot refuses to load a repository whose key space overlaps another one in the same bucket and logs an error. Two repositories with an empty prefix and `sharedBucket` off always overlap.

Ingot finds shared buckets by comparing the endpoint text and the bucket name. Use the same endpoint spelling in every repository that shares a bucket.

## Limits

* S3 repositories have no quota. The `quota` setting applies to filesystem repositories only. The S3 provider reports its usage as unknown (`-1`) and never refuses an upload for lack of space.
* The metadata cache is local to one instance, so instances do not share it. Leave it off when several instances serve the same repository.
* Back up the bucket together with the database. See [Back up and restore](back-up-and-restore.md).

## Result

Each deployed file exists as an object in the bucket under the key from the table above. A download through the repository URL returns the same bytes that the bucket holds.
