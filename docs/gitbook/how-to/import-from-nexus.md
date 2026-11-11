# Import from Nexus

The Nexus importer copies Maven repositories from Sonatype Nexus Repository 3 into Ingot. It reads Nexus through its REST API and never writes to Nexus. It is bundled in the image and off until you switch it on.

> **Status.** The routes are exposed when `INGOT_NEXUS_IMPORT_ENABLED` is `true`: discovery, the dry run, the import, the verify pass, the job and report routes, and the operator controls. The steps below are the flow. The importer is tested against a fake Nexus that replays documented responses; it has not been run against a licensed Nexus instance, so start with a dry run and a small repository.

## Before you start

* Use a Nexus credential with read access to the repositories you want to copy and, for the repository settings, the settings read permission. Without that permission the importer still copies the content and records the definitions as manual work.
* Freeze Nexus maintenance tasks, such as cleanup and compaction, while a copy runs. The importer lists a repository while it copies it, and a listing that changes under it is only caught by the verify pass.
* Start with a dry run and a small repository. The importer is tested against a fake Nexus that replays documented responses, not against a licensed Nexus, so the first real run is the first run against your server.

## Switch it on

Set the switch in the environment of the Ingot container or process:

```yaml
environment:
  INGOT_NEXUS_IMPORT_ENABLED: "true"
```

`REPOSILITE_NEXUS_IMPORT_ENABLED` is read when the `INGOT_` name is unset. The tuning variables, `INGOT_NEXUS_IMPORT_CONCURRENCY`, `INGOT_NEXUS_IMPORT_MAX_REQUESTS_PER_SECOND`, `INGOT_NEXUS_IMPORT_AUDIT` and `INGOT_NEXUS_IMPORT_ALLOWED_HOSTS`, are listed in the [environment variables reference](../reference/environment-variables.md#nexus-import-variables).

Nothing else changes. The configuration file, the volumes and the entrypoint stay as they are.

## The flow

1. **Connection test.** `POST /api/migration/nexus/connection/test` with the Nexus URL, a user name and a password. The answer tells you whether Nexus is reachable, its version and edition, whether the credential is accepted, and which areas it can read. Only instance administrators may call it.
2. **Dry run.** Lists the repositories and writes a loss report: one row per object with a status (`MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED` or `EXCLUDED`), the reason, the consequence and the action. The dry run changes nothing.
3. **Repository definitions.** Maven hosted, proxy and group repositories are created in the `maven` settings. A name that is taken is never changed: the importer proposes `<name>-nexus`. Access is always private.
4. **Copy.** Every file is downloaded, checked against the checksums Nexus reports and a locally computed sha256, and written through the normal deploy path.
5. **Verify.** Compares the file lists, sizes and sha1 values with Nexus for each repository. A repository with a conflict is never reported as verified.

## Three decisions

**Conflict policy.** A file that already exists in Ingot with a different checksum is a conflict.

* `refuse` (default): nothing is overwritten, the conflict is recorded, and the repository does not become verified.
* `abort`: the first conflict stops that repository and the job continues with the next one.
* `overwrite`: the existing file is replaced. Both the deletion and the write are audited. Use it only after the dry run has shown how many conflicts there are.

**Audit mode.** `per-file` (default) writes one deploy event per file with the actor `migration`. `summary` writes only the run event, `NEXUS_IMPORT_RUN`, which carries the counts. Use `summary` for very large repositories. See the [audit events reference](../reference/audit-events.md).

**Credential form.** Enter the password as a literal or as a secret reference. A reference is resolved only when the operator has allowed it, with `INGOT_SETTINGS_SECRET_REFERENCES=true` and the variable listed in `INGOT_SETTINGS_SECRET_ENV` or the directory in `INGOT_SETTINGS_SECRET_FILES`:

```yaml
environment:
  INGOT_SETTINGS_SECRET_REFERENCES: "true"
  INGOT_SETTINGS_SECRET_ENV: "NEXUS_PASSWORD"
  NEXUS_PASSWORD: "..."
```

Then send the password as `${env:NEXUS_PASSWORD}`. A literal password is held in memory only. It is never written to the database, the audit log, a log line or a report. A URL that contains a user name and password is refused.

## TLS

Nexus is verified against the JVM trust store. There is no setting that turns certificate verification off. If Nexus uses a certificate from a private certificate authority, add that authority to the image with `INGOT_EXTRA_CA_CERTS`, see the environment variables reference. The importer then trusts it like any other certificate. A plain `http` URL works, but the importer warns, because the password crosses the network in the clear.

## What a cluster does when a replica drops out

A job runs on one replica at a time under a lease stored in the database. If that replica stops or stalls, another replica takes the job over and continues from the files already committed. No file is written twice.

A job whose password was typed in stops with the reason `CREDENTIALS_NEEDED` when the replica that holds it is lost, because the password lives only in memory. Enter it again on any replica:

```text
PUT /api/migration/nexus/jobs/{id}/credentials
```

Progress is kept. A job paused for read-only mode resumes by itself when read-only mode is switched off.

## Known limits

* Only Maven (`maven2`) repositories are copied. Other formats are listed as `UNSUPPORTED` with their component count and size.
* Proxy caches are not copied. The proxy definition is created and its remote is fetched again.
* Users, roles, tokens, cleanup policies, routing rules and tasks are not imported. They appear in the loss report as `MANUAL` or `UNSUPPORTED`.
* Metadata is copied as it is. `lastModified` times are not carried over.
* The copy runs one file at a time in this version. `INGOT_NEXUS_IMPORT_CONCURRENCY` is accepted and reserved for parallel copying.

## Related

* [Import from other repository managers](import-from-other-repository-managers.md)
* [Environment variables](../reference/environment-variables.md)
* [HTTP API](../reference/http-api.md)
* [Audit events](../reference/audit-events.md)
