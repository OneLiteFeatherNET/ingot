---
description: Move Maven artifacts from Nexus, Artifactory or another Reposilite into an Ingot repository, either by copying the storage or by re-deploying, and what each path does to the search index and to maven-metadata.xml. Also covers the bundled Nexus importer, its switch, flow, conflict policy, TLS and limits.
---

# Import from other repository managers

You can move the artifacts of Nexus, Artifactory or another Reposilite into Ingot in two ways. Both need an export of the files in the Maven layout first. The export tool belongs to the source system, and this page does not verify its steps.

| Path | When to use it | What Ingot does |
| --- | --- | --- |
| Copy the storage | Large imports, when the layout already matches. | Nothing. Ingot does not see the files until you rebuild the search index, and it does not change any metadata. |
| Re-deploy with HTTP `PUT` | Smaller imports, or when the source metadata should be kept as uploaded. | Stores each file as a deployment, emits the deploy events, and applies the snapshot and redeployment rules. |

For Nexus Repository 3 there is a bundled importer, see [Sonatype Nexus importer](#sonatype-nexus-importer), and a guided import in the dashboard that runs the same steps through the import API. See [Use the Nexus import wizard](use-the-nexus-import-wizard.md).

## Before you begin

* A repository in Ingot that will hold the artifacts. Create it first, see [Create hosted repositories](../../configure/repositories/create-hosted-repositories.md). Do not use a `PROXY` or a `GROUP` as the target, because they refuse uploads.
* An export of the source repository in the Maven layout, with paths such as `com/example/app/1.0/app-1.0.jar`. Check the export before you copy anything. An export that adds an extra top-level folder must be corrected first.
* For the re-deploy path, a token with the deploy node for the repository, `ingot.repository.<repository>.deploy`. See [Grant permissions with nodes](../../manage/access/grant-permissions-with-nodes.md).
* Settings for the import. Read the rules below first.

{% hint style="info" %}
Use `redeployment` and `preserveSnapshots` deliberately. `redeployment` is `false` by default, so Ingot refuses to replace a file that already exists. `preserveSnapshots` is `false` by default, so Ingot deletes older snapshot builds when it receives snapshot metadata.
{% endhint %}

## Path A: copy the storage

Filesystem repositories keep their files under the working directory. By default a repository named `releases` lives in `<working directory>/repositories/releases/`. In the container that is `/app/data/repositories/releases/`. A repository with a custom `mount` uses that directory instead.

1. Create the target repository and let Ingot create its directory. Look for the directory in the working directory.
2. Copy the export into that directory, keeping the Maven paths. The repository root is the first directory level under the repository name, so `com/example/app/1.0/app-1.0.jar` goes directly into `releases/`.
3. Make the files owned by the service user of the instance. In the container, that is the user set by `PUID` and `PGID`.
4. Rebuild the search index. The search index only sees files that Ingot wrote itself, so copied files are missing from search until the rebuild. See [Rebuild the search index](../../configure/search/rebuild-search-index.md).

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

* [Rebuild and verify the search index](../../configure/search/rebuild-search-index.md) after a copy.
* [Create hosted repositories](../../configure/repositories/create-hosted-repositories.md) for the target repository.
* [Create proxy repositories](../../configure/repositories/create-proxy-repositories.md) if you want to keep the upstream instead of importing its files.
* [Environment variables](../../references/configuration/environment-variables.md)
* [HTTP API](../../references/http-api/http-api.md)
* [Audit events](../../references/identity/audit-events.md)

## Sonatype Nexus importer

The Nexus importer copies Maven repositories from Sonatype Nexus Repository 3 into Ingot. It reads Nexus through its REST API and never writes to Nexus. It is bundled in the image and off until you switch it on.

> **Status.** The routes are exposed when `INGOT_NEXUS_IMPORT_ENABLED` is `true`: discovery, the dry run, the import, the verify pass, the job and report routes, and the operator controls. The steps below are the flow. The importer is tested against a fake Nexus that replays documented responses; it has not been run against a licensed Nexus instance, so start with a dry run and a small repository.

### Before you start

* Use a Nexus credential with read access to the repositories you want to copy and, for the repository settings, the settings read permission. Without that permission the importer still copies the content and records the definitions as manual work.
* Freeze Nexus maintenance tasks, such as cleanup and compaction, while a copy runs. The importer lists a repository while it copies it, and a listing that changes under it is only caught by the verify pass.

### Switch it on

Set the switch in the environment of the Ingot container or process:

```yaml
environment:
  INGOT_NEXUS_IMPORT_ENABLED: "true"
```

`REPOSILITE_NEXUS_IMPORT_ENABLED` is read when the `INGOT_` name is unset. The tuning variables, `INGOT_NEXUS_IMPORT_CONCURRENCY`, `INGOT_NEXUS_IMPORT_MAX_REQUESTS_PER_SECOND`, `INGOT_NEXUS_IMPORT_AUDIT` and `INGOT_NEXUS_IMPORT_ALLOWED_HOSTS`, are listed in the [environment variables reference](../../references/configuration/environment-variables.md#nexus-import-variables).

Nothing else changes. The configuration file, the volumes and the entrypoint stay as they are.

### The flow

1. **Connection test.** `POST /api/migration/nexus/connection/test` with the Nexus URL, a user name and a password. The answer tells you whether Nexus is reachable, its version and edition, whether the credential is accepted, and which areas it can read. Only instance administrators may call it.
2. **Dry run.** Lists the repositories and writes a loss report: one row per object with a status (`MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED` or `EXCLUDED`), the reason, the consequence and the action. The dry run changes nothing.
3. **Repository definitions.** Maven hosted, proxy and group repositories are created in the `maven` settings. A name that is taken is never changed: the importer proposes `<name>-nexus`. Access is always private.
4. **Copy.** Every file is downloaded, checked against the checksums Nexus reports and a locally computed sha256, and written through the normal deploy path.
5. **Verify.** Compares the file lists, sizes and sha1 values with Nexus for each repository. A repository with a conflict is never reported as verified.

### Three decisions

**Conflict policy.** A file that already exists in Ingot with a different checksum is a conflict.

* `refuse` (default): nothing is overwritten, the conflict is recorded, and the repository does not become verified.
* `abort`: the first conflict stops that repository and the job continues with the next one.
* `overwrite`: the existing file is replaced. Both the deletion and the write are audited. Use it only after the dry run has shown how many conflicts there are.

**Audit mode.** `per-file` (default) writes one deploy event per file with the actor `migration`. `summary` writes only the run event, `NEXUS_IMPORT_RUN`, which carries the counts. Use `summary` for very large repositories. See the [audit events reference](../../references/identity/audit-events.md).

**Credential form.** Enter the password as a literal or as a secret reference. A reference is resolved only when the operator has allowed it, with `INGOT_SETTINGS_SECRET_REFERENCES=true` and the variable listed in `INGOT_SETTINGS_SECRET_ENV` or the directory in `INGOT_SETTINGS_SECRET_FILES`:

```yaml
environment:
  INGOT_SETTINGS_SECRET_REFERENCES: "true"
  INGOT_SETTINGS_SECRET_ENV: "NEXUS_PASSWORD"
  NEXUS_PASSWORD: "..."
```

Then send the password as `${env:NEXUS_PASSWORD}`. A literal password is held in memory only. It is never written to the database, the audit log, a log line or a report. A URL that contains a user name and password is refused.

### TLS

Nexus is verified against the JVM trust store. There is no setting that turns certificate verification off. If Nexus uses a certificate from a private certificate authority, add that authority to the image with `INGOT_EXTRA_CA_CERTS`, see the environment variables reference. The importer then trusts it like any other certificate. A plain `http` URL works, but the importer warns, because the password crosses the network in the clear.

### What a cluster does when a replica drops out

A job runs on one replica at a time under a lease stored in the database. If that replica stops or stalls, another replica takes the job over and continues from the files already committed. No file is written twice.

A job whose password was typed in stops with the reason `CREDENTIALS_NEEDED` when the replica that holds it is lost, because the password lives only in memory. Enter it again on any replica:

```text
PUT /api/migration/nexus/jobs/{id}/credentials
```

Progress is kept. A job paused for read-only mode resumes by itself when read-only mode is switched off.

### Known limits

* Only Maven (`maven2`) repositories are copied. Other formats are listed as `UNSUPPORTED` with their component count and size.
* Proxy caches are not copied. The proxy definition is created and its remote is fetched again.
* Users, roles, tokens, cleanup policies, routing rules and tasks are not imported. They appear in the loss report as `MANUAL` or `UNSUPPORTED`.
* Metadata is copied as it is. `lastModified` times are not carried over.
* The copy runs one file at a time in this version. `INGOT_NEXUS_IMPORT_CONCURRENCY` is accepted and reserved for parallel copying.
