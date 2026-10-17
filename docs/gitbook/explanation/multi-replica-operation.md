---
description: Explains how several Ingot replicas share one database and what each replica keeps for itself. Covers shared settings with revision checks, job claims, the search outbox leases, single-run locks, and the state that is not shared, such as the local configuration, the setup code and the console stream.
---

# Multi-replica operation

Several Ingot replicas can serve the same instance. They share one database, and the database holds nearly all state that matters. This page explains which state is shared, how replicas avoid doing the same work twice, and what each replica keeps for itself. It describes the behaviour of the current release. It does not describe a finished Kubernetes setup.

## What a replica needs

Every replica must reach the same database server. SQLite is a single file on one machine, so it suits one instance only. For more than one replica, use a database server. MariaDB and PostgreSQL are the supported choices. MySQL and H2 are experimental.

Each replica should have its own data directory. The local configuration file and the health check file are written there at each start, so replicas that share one directory overwrite each other's files. See [Shared and local state](#shared-and-local-state) below.

## What is shared

Everything in this list lives in the database, so every replica reads the same value.

* **Identity data**: users, machine accounts, tokens, teams, grants and permission nodes. Permission decisions read these tables from the database. A change made on one replica is therefore seen by the others without a delay, with one exception described under [Shared and local state](#shared-and-local-state).
* **Browser sessions**: the refresh tokens and their families. A login on one replica can be renewed on another.
* **The signing key of session tokens**. Every replica verifies session tokens with the same key.
* **Login failures**. A lockout set by one replica holds on the others.
* **Shared settings**: the settings domains that the dashboard edits. See the next section.
* **The artifact search index** and the work queue that keeps it current.
* **Job claims**, which decide which replica runs a scheduled job.

## Shared settings and revisions

The dashboard edits settings in domains, such as the login and search settings. When the settings live in the database, each save goes through a revision check.

1. The writing replica first reads the stored document, so it does not work from a copy that is out of date.
2. A request can send an `If-Match` header with the version it read. If that version is no longer current, the save is refused with `412 Precondition Failed`. The client must reload the domain and try again.
3. The replica writes the new document only if the stored revision is still the one it read. The check and the write happen in one statement, so two replicas that read the same revision cannot both win.
4. If another replica saved first, the replica starts again from step 1. After 10 attempts without success, the save is refused with `409 Conflict`.

Every replica also checks the stored document every 10 seconds. When the document has changed, the replica loads it. A setting therefore reaches all replicas within about 10 seconds of the save.

## Job claims

Scheduled work runs on every replica, but each job should run once. A job claims a key before it starts. The key contains the job name and the UTC date, for example a cleanup run for one policy on one day. The claim is an insert with a unique key. The replica whose insert succeeds runs the job, and the others skip it.

Claims are kept for 7 days. A claim older than that is removed the next time a replica makes a claim. A new key for each day means a job runs once per day, even when every replica is up.

## The search outbox

Writes to the repositories add rows to an outbox table. Background workers on each replica claim those rows and apply them to the search index.

* A worker claims a batch of rows with a lease of **60 seconds**. It extends the lease every 20 seconds while it still works on the batch.
* If a replica dies, its lease runs out and another replica takes the rows. The work is written so that doing it twice is harmless, so a takeover can only repeat work, never lose it.
* A failed row is released with a delay that grows with each attempt. After a set number of attempts, the row stops being retried.

A rebuild or a verify run of the index is a single run for the whole instance. Each run starts with a conditional update of a lock row. Exactly one replica wins. The run row has a lease too. If the replica that runs it dies, another replica continues from the saved position. See [How the search index stays current](how-the-search-index-stays-current.md).

## Shared and local state

Some state is kept by each replica for itself. Plan for it when you run more than one replica.

| State | Where it lives | What it means for several replicas |
| --- | --- | --- |
| Local configuration (`configuration.cdn`) | data directory, one file per replica | Each replica reads its own file. It is rewritten at each start. Keep the file identical on every replica, or give each one only the settings it needs. |
| Environment overrides (`INGOT_LOCAL_*`) | the environment of each process | Each replica reads its own environment. Set the same values everywhere. |
| Health check address (`.local/reposilite.address`) | data directory | Written at each start. Replicas that share a directory overwrite each other's address. |
| Setup code | memory of each replica | Only the code of the replica that wrote it is accepted. See [Sessions, login and CSRF](sessions-and-login.md). |
| Temporary tokens from `--token` | memory of each replica | Not shared. They exist only on the replica that was started with them. |
| Console stream | memory of each replica | A live console connection is held by one replica. |
| S3 metadata cache | memory of each replica | Each replica caches object metadata on its own, until the entry expires. It can show metadata that another replica has already changed. |
| Successful token authentication | memory of each replica, up to one minute | A token that is changed or revoked on one replica can still be accepted by another replica for up to one minute. |
| Failed Basic and token attempts (brute force protection) | memory of each replica | Each replica counts failures on its own. The dashboard login lockout is shared, see [Sessions, login and CSRF](sessions-and-login.md).

Repository content is outside the database. Its location decides what replicas can share. Filesystem storage needs a volume that every replica can reach, and S3 storage needs a bucket that every replica can reach.

## What is not provided yet

* **No readiness endpoint.** The health endpoint `/api/status/health` reports that the server is alive. Ingot has no separate endpoint that reports that a replica is ready to serve traffic. A load balancer cannot wait for a replica to finish its start-up steps, such as the database migrations.
* **No leader election.** Work is divided by claims and leases, not by a leader.
* **No shared console, cache or counters.** The items in the table above stay per replica.

## Related pages

* [How the search index stays current](how-the-search-index-stays-current.md) explains the outbox and the rebuild in detail.
* [Sessions, login and CSRF](sessions-and-login.md) explains the sessions that replicas share.
* [Identity model](identity-model.md) explains the data that every replica reads.
* [Back up before upgrading](../how-to/back-up-before-upgrading.md) describes what to save before a change to a shared database.
