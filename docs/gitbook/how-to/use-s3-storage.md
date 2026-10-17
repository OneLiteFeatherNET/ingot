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
The S3 keys are stored with the repository settings in the database. Anyone who can read the database can read them. The settings API hides them only when `hideSettingsSecrets` is enabled.
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
| Local Metadata Cache | `metadataCacheSettings` | off | Caches object metadata inside one instance. Other instances do not see the cache. |

For AWS, set the region of your bucket.

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
