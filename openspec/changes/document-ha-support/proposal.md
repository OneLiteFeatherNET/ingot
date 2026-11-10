# Proposal

## Why

An operator who evaluates Ingot for a production team asks three plain questions before anything else. Which setup do you
support when I run more than one replica? What is not supported? How big can it get? Today the answers are spread over five
pages and several of them contradict each other. The Kubernetes guide says "several replicas are not fully supported yet",
the explanation page says "it does not describe a finished Kubernetes setup", the upgrade page says a rolling upgrade "is not
tested", and the sizing page says no measurement exists. Each sentence was true when written. Together they read as "do not
run this", although the two-replica smoke test, the schema lock, the metadata lock, the job leases and the readiness endpoint
now exist.

A buyer who cannot find a statement treats the product as unsupported, whatever the code does. The review of the research report
named the missing scale targets as a gap: artifact counts, repository counts, concurrent users and large-file limits.

## What Changes

- **One page, "High availability support"**, under `docs/gitbook/explanation/` and linked from the summary, the Kubernetes guide
  and the multi-replica page. It states, for the 1.x line:
  - **Supported topology.** PostgreSQL or MariaDB as the shared database, S3 or a `ReadWriteMany` volume as the shared
    storage, N replicas of one release line behind one Service, in one region and one cluster. The optional NATS cluster bus.
  - **Not supported.** SQLite and H2 with more than one replica, MySQL (experimental), several regions or several database
    clusters, a read and write split, active-active replication between two sites, Galera as a tested target, a filesystem
    volume that is not shared, replicas on release lines that are not adjacent.
  - **Rolling upgrade rule N/N+1.** The four constraints of `test-mixed-version-cluster` (additive schema, one-shot data
    migrations, bus envelope version, shared settings), the order (one replica of the new release first, the old replicas after),
    the limits found by that change (no concurrent deploys of one artifact across the pair, no scheduled cleanup until every
    replica is upgraded, token and settings changes made on the old release during the window), and the statement "tested" only
    where the test exists.
  - **Failure behaviour.** What each failure looks like to a client and what recovers it: a replica loss (its lease runs out after
    60 seconds, the search outbox rows are taken over), a database outage (readiness answers 503, requests fail, no restart),
    a bus outage (convergence through the database on the next poll), a lease holder lost during a run.
  - **Scale and sizing.** A table of the dimensions a buyer asks for (artifact count, repository count, concurrent clients,
    largest upload, search index size, replica count) with the measured value and its conditions, or the words "not measured".
    No target is stated without a recorded run. The values that are known today are the ones in the code: the multipart part of
    8 MiB, the lease and poll times, the 10 second settings poll.
  - **Where support ends.** Each topology item names the test that backs it (smoke test, mixed-version test, database matrix,
    container tests) and the CI job that runs it.
- **Stale sentences are replaced** in `docs/gitbook/how-to/upgrade-ingot.md` (the "not tested" hint),
  `docs/gitbook/how-to/run-on-kubernetes.md` ("not fully supported yet"), `docs/gitbook/explanation/multi-replica-operation.md`
  ("not a finished Kubernetes setup") and `docs/gitbook/reference/upgrade-changes.md` (the mixed versions note): each points to
  the new page and keeps only the fact that belongs to its own topic.
- Commit type and scope: every commit of this change is `docs(gitbook)`. Pull request title:
  `docs(gitbook): add a high availability support statement`.
- **Breaking: no.** Documentation only. No configuration key, environment variable, default, manifest or data changes.

### Out of scope

- The N/N+1 test and the rule's verification. That is `test-mixed-version-cluster`, which this change depends on.
- Running the benchmarks. This change publishes numbers that exist. A benchmark run is its own change (the sizing profiles of
  the roadmap), and the page has the rows ready for it.
- A support window, a response-time commitment, a support offer and any commercial wording. Legal and business model
  questions stay out of every spec. The page describes technical support for a topology, not a service level.
- Replication to a disaster recovery site and the DR runbook with RPO and RTO.
- Changes to the chart, the server or the tests.

## Impact

Depends on:

- `test-mixed-version-cluster`: the rule may say "tested" only after it merged. Until then the page may be written but not
  merged.
- `add-ha-smoke-test` (merged, #124) and `add-chart-ha-values` (merged, #109): the topology the page names.
- `test-server-database-matrix` (merged, #121): the PostgreSQL and MariaDB claim.

Open question that blocks the scale table, not the rest: the maintainer decides whether the page may publish a target before a
benchmark exists. The proposal assumes it does not, so every row says "not measured" until a run is recorded.

Code touched: `docs/gitbook/` only (one new page, `SUMMARY.md`, and four edited pages). No file under `charts/` or
`reposilite-backend/`.
