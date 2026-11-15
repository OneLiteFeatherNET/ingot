# Proposal

## Why

An operator who runs several Ingot replicas and wants to upgrade has one question: may the old release and the new
release serve side by side while the pods are replaced one after the other? Today the answer on the upgrade page is "not
tested, keep one replica or freeze token changes". That sentence forces a short outage or a manual freeze on every
upgrade, and it is the reason a buyer who compares Ingot with a product that promises zero-downtime upgrades does not
count Ingot as highly available.

Parts of the answer already exist. Schema changes are additive (every start runs `createMissingTablesAndColumns`), the
cluster bus ignores a message of an unknown version or type, and one container test starts the previous release, then the
current image, on one MariaDB database and checks that both still answer. That test proves the two processes start. It
does not prove that a deploy on one is readable on the other, that the old replica can be restarted after the new one has
changed the schema, or that the bus and the shared settings of the new release leave the old one alone. Without those
checks, nothing can say which versions may run together, and a later change can break the rule without any test noticing.

## What Changes

- **A written N/N+1 rule**, kept in the design of this change and copied to the documentation by
  `document-ha-support`: replicas of the release line N and of the next release N+1 may run together against one database
  during a rolling upgrade, and nothing else may. Four constraints make it true, each found in the code:
  - **Schema.** Only additive changes: new tables, and new columns that are nullable or have a default. No drop, rename
    or type change while N may still run.
  - **One-shot data migrations.** A migration that converts or deletes rows (the identity and permission node migrations)
    runs once, when the first N+1 replica starts. What N writes afterwards is picked up by the next start of N+1, not at
    once.
  - **Cluster bus.** The envelope version stays `1` inside the pair. New event types and new payload fields are allowed,
    because a receiver ignores what it does not know. A new envelope version needs a release in which both versions are
    understood first.
  - **Shared settings.** A new settings domain is allowed. A release before N+1 does not write the revision of a document.
- **A mixed-version container test** `MixedVersionClusterTest` in `reposilite-backend/src/containerTest/`, on one MariaDB
  database and one shared repository volume, with the previous release image and the current build side by side. It checks
  five things: deploys on one are readable on the other, in both directions; the old replica is restarted after the new
  one has changed the schema and serves again; the schema the new release added holds no column the old release cannot
  write; a bus message of an unknown version and an unknown type is ignored by the new replica and does not disturb it;
  a settings change on the new replica is served by the old one, and the observed behaviour for the reverse direction is
  recorded in the page of `document-ha-support`.
- **A golden wire test** for the envelope: a fixed version 1 JSON string for each event type must decode, and a message
  with an extra payload field must still decode. It runs in milliseconds and fails when a later change touches the wire
  format.
- **A CI job input**: the previous release image is taken from the latest release tag instead of the fixed `1.1.0` in
  `reposilite-backend/build.gradle.kts`, so the pair is always N and N+1.
- Commit type and scope: every commit of this change is `test(cluster)`. Pull request title:
  `test(cluster): run the previous release beside the current build`.
- **Breaking: no.** No configuration key, environment variable, default, manifest or data changes. The change adds tests
  and the choice of the image they start. No page under `docs/gitbook/` is touched here. The page that states the rule is
  the change `document-ha-support`.

### Out of scope

- The HA support statement, the sizing targets and the page edits. They are `document-ha-support`, which depends on this
  change.
- The two-replica smoke test on kind (`add-ha-smoke-test`, merged as #124). This change adds no Kubernetes step. The
  mixed-version case runs in the container test suite, because it needs two different images on one database and no
  cluster.
- Mixed versions across more than one release. Only N and N+1 are promised and tested.
- A runtime guard that refuses to start when it sees an older replica (the "version skew" item of the HA preflight guard).
  It is a separate, opt-in change.
- Galera, PostgreSQL as the second database of the mixed test, and S3 as the shared storage. The suite runs MariaDB, and
  the PostgreSQL and S3 paths stay with `test-server-database-matrix` and the smoke test.
- Changing the behaviour of any migration. If the test finds an N/N+1 break, the fix is its own change, and the rule
  records the break until then.

## Impact

Depends on:

- `add-cluster-bus` (merged): the envelope, `ClusterEnvelopeCodec` and the NATS transport.
- `fix-schema-init-lock` (merged, #103): the schema lock that N+1 replicas take. The previous release takes none, which the
  test relies on to start the replicas one after the other.
- `add-ha-smoke-test` (merged, #124): the in-cluster checks that this change does not repeat.

Blocks `document-ha-support`, whose rule text is only allowed to say "tested" after this change has merged.

Open question: the first run may show that the permission node migration, which deletes converted grants, lets a still
running previous replica decide differently from an N+1 replica. The release before 1.2.0 has no permission nodes, so the
pair 1.1.x and 1.2.0 is not affected. The pair 1.2.x and 1.3.0 is the first one where it matters, and the maintainer
decides then whether the migration is deferred. It does not block this change.

Code touched: `reposilite-backend/src/containerTest/` (one new class, one helper), `reposilite-backend/src/test/` (one
golden test of the envelope), `reposilite-backend/build.gradle.kts` (the default of `ingot.previous.image`),
`.github/workflows/ci.yml` (the property passed to the container job). No production code.
