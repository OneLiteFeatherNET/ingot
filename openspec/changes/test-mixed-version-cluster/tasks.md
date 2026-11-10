# Tasks

Every commit of this change has the type `test` and the scope `cluster`. Tests are written first and seen to fail before the
code they check exists. Every test follows F.I.R.S.T.: no sleeps, no system-time reads (an injected `java.time.Clock` where
time matters), no fixed ports, no shared mutable state, a temporary directory per test, bounded waits through latches, and
assertions with messages. Container and Gradle runs go through `heavy`, with `--max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose test-mixed-version-cluster`: this change only, branched from the remote main branch.
  Verify: `openspec validate test-mixed-version-cluster --strict` passes and the dash grep prints nothing.

## 2. Envelope wire format

- [ ] 2.1 `test(cluster)`: write `ClusterEnvelopeCodecGoldenTest` in `reposilite-backend/src/test/`: one fixed version 1 string
  per event type decodes to the stored type, origin and payload; an extra payload field is accepted; `"v":2` and an unknown
  type are `Ignored`. Verify: the test passes on the current codec. To see it fail once, change a wire name locally and run it.

## 3. Mixed-version container test

- [ ] 3.1 `test(cluster)`: write a failing `MixedVersionClusterTest` case "deploys cross between the releases": previous
  release first, then the current build, one MariaDB and one bind-mounted directory per test, deploy on one and read on the
  other in both directions, two different artifacts concurrently. Verify: it fails before the helper of 3.2 exists.
- [ ] 3.2 `test(cluster)`: add the helper that starts a replica with a shared repository directory and `PUID`/`PGID`, in
  `reposilite-backend/src/containerTest/`. Verify: the case of 3.1 passes.
- [ ] 3.3 `test(cluster)`: add the case "the old replica restarts after the schema changed". Verify: it passes, and it fails
  when the restart is made against a database in which a column that the old release writes was dropped by hand.
- [ ] 3.4 `test(cluster)`: add the schema case: list `(table, column, nullable, default, extra, type)` through
  `information_schema` after each start and fail on a new `NOT NULL` column without a default, a removed column or a changed
  type. Verify: it fails when a throw-away `NOT NULL` column is added by hand between the two listings, and passes without it.
- [ ] 3.5 `test(cluster)`: add the bus case on two current replicas and one NATS broker: a `"v":2` message and a version 1 message
  with an unknown type are ignored, no failure is recorded, and a known event sent afterwards is applied. Verify: the case
  fails when the known event is sent with an unknown type name.
- [ ] 3.6 `test(cluster)`: add the settings case: a change saved on the current replica is served by the previous release, and the
  reverse direction is observed and printed in the failure message only, never asserted, because its behaviour is recorded in
  the documentation. Verify: the first direction passes.

## 4. Previous image follows the latest release

- [ ] 4.1 `test(cluster)`: raise the default of `ingot.previous.image` to `1.1.1` in `reposilite-backend/build.gradle.kts`, and
  have the container job in `.github/workflows/ci.yml` pass the highest `v*` tag as `-Dingot.previous.image`. Verify: the job log
  shows the tag it passed, and the existing `PreviousIngotMigrationTest` still runs against it.

## 5. Documentation

No task. This change adds no configuration key, environment variable, manifest or data, so no page under `docs/gitbook/`
changes. The rule is documented by `document-ha-support`.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `test(cluster): run the previous release beside the current build`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the upgrade story: what an operator could
  not rely on during a rolling upgrade and what the test now proves. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts it and every commit, and the body has
  no em or en dash.
