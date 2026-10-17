---
description: Move Maven artifacts from Nexus, Artifactory or another Reposilite into an Ingot repository, either by copying the storage or by re-deploying, and what each path does to the search index and to maven-metadata.xml.
---

# Import from other repository managers

You can move the artifacts of Nexus, Artifactory or another Reposilite into Ingot in two ways. Both need an export of the files in the Maven layout first. The export tool belongs to the source system, and this page does not verify its steps.

| Path | When to use it | What Ingot does |
| --- | --- | --- |
| Copy the storage | Large imports, when the layout already matches. | Nothing. Ingot does not see the files until you rebuild the search index, and it does not change any metadata. |
| Re-deploy with HTTP `PUT` | Smaller imports, or when the source metadata should be kept as uploaded. | Stores each file as a deployment, emits the deploy events, and applies the snapshot and redeployment rules. |

## Before you begin

* A repository in Ingot that will hold the artifacts. Create it first, see [Create hosted repositories](create-hosted-repositories.md). Do not use a `PROXY` or a `GROUP` as the target, because they refuse uploads.
* An export of the source repository in the Maven layout, with paths such as `com/example/app/1.0/app-1.0.jar`. Check the export before you copy anything. An export that adds an extra top-level folder must be corrected first.
* For the re-deploy path, a token with the deploy node for the repository, `ingot.repository.<repository>.deploy`. See [Grant permissions with nodes](grant-permissions-with-nodes.md).
* Settings for the import. Read the rules below first.

{% hint style="info" %}
Use `redeployment` and `preserveSnapshots` deliberately. `redeployment` is `false` by default, so Ingot refuses to replace a file that already exists. `preserveSnapshots` is `false` by default, so Ingot deletes older snapshot builds when it receives snapshot metadata.
{% endhint %}

## Path A: copy the storage

Filesystem repositories keep their files under the working directory. By default a repository named `releases` lives in `<working directory>/repositories/releases/`. In the container that is `/app/data/repositories/releases/`. A repository with a custom `mount` uses that directory instead.

1. Create the target repository and let Ingot create its directory. Look for the directory in the working directory.
2. Copy the export into that directory, keeping the Maven paths. The repository root is the first directory level under the repository name, so `com/example/app/1.0/app-1.0.jar` goes directly into `releases/`.
3. Make the files owned by the service user of the instance. In the container, that is the user set by `PUID` and `PGID`.
4. Rebuild the search index. The search index only sees files that Ingot wrote itself, so copied files are missing from search until the rebuild. See [Rebuild and verify the search index](rebuild-search-index.md).

{% hint style="warning" %}
This page does not verify copying into a running instance. Copy into a repository that receives no traffic, or stop the instance before the copy.
{% endhint %}

### What the copy does not change

* **maven-metadata.xml.** The copied metadata files are kept as they are. Ingot does not scan the storage to rebuild them. Metadata changes only when a deployment or a delete touches a version, and when a snapshot version is deployed.
* **Snapshot builds.** Copying does not run the cleanup of older snapshot builds. That cleanup runs when a snapshot metadata file is deployed and `preserveSnapshots` is `false`.
* **Search.** Ingot updates the search index when it writes a file itself. Files copied in from outside are missing from search until a rebuild runs.

{% hint style="warning" %}
Do not use `POST /api/maven/generate/pom/{repository}/{gav}` to fill in missing POMs of an import. It writes a new stub POM at the given path and replaces any POM that is already there. It also appends the version to the parent metadata.
{% endhint %}

## Path B: re-deploy with HTTP PUT

Each file is sent to the repository as a deployment. Use the same paths as in the export:

```bash
curl -u deployer:secret -X PUT --data-binary @app-1.0.jar \
  https://repo.example.org/releases/com/example/app/1.0/app-1.0.jar

curl -u deployer:secret -X PUT --data-binary @app-1.0.pom \
  https://repo.example.org/releases/com/example/app/1.0/app-1.0.pom
```

Upload the checksum files and the `maven-metadata.xml` files from the export as well. Ingot stores each file as it arrives. A Maven build that deploys to Ingot uploads its own metadata, so re-running a deployment from a build also brings the metadata up to date.

### Rules that apply to every file

* A new file is accepted. An existing file is refused unless the repository has `redeployment` set to `true`. Metadata files are always accepted.
* A deployment to a `PROXY` or a `GROUP` is refused with `405`.
* Every accepted deployment updates the search index for that file, so the file is searchable without a rebuild.
* A deployment of snapshot metadata triggers the snapshot cleanup. If `preserveSnapshots` is `false`, older builds are removed from the snapshot directory.

To keep every build of an imported snapshot, set `preserveSnapshots` to `true` before you deploy the snapshot metadata.

## Verify the import

1. Ask for the versions of an artifact. Ingot answers from the metadata file of the artifact:

   ```bash
   curl -fsS https://repo.example.org/api/maven/versions/releases/com/example/app
   ```

   The versions must match the export.

2. Check that a file that you copied is searchable. A copied file must appear after the rebuild. A re-deployed file must appear at once.
3. Download one file with its checksum and compare the checksum with the export.

## Source-specific steps

The export and import steps for each source are the responsibility of the source. Two notes from older guides apply:

* The older Artifactory guide describes an export with an `.m2 compatible export` option. That option is not verified for this page. Artifactory build metadata folders (`*.artifactory-metadata`) are not Maven files. List them before you delete them:

  ```bash
  find . -type d -name '*.artifactory-metadata' -print
  ```

* Nexus 3 export is done with a third-party tool, which is not verified for this page. Check its output against the layout above before you copy or deploy.

## Related

* [Rebuild and verify the search index](rebuild-search-index.md) after a copy.
* [Create hosted repositories](create-hosted-repositories.md) for the target repository.
* [Create proxy repositories](create-proxy-repositories.md) if you want to keep the upstream instead of importing its files.
