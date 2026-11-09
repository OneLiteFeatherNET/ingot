# Proposal

## Why

Ingot is meant to run as several replicas behind one Service, and the Kubernetes HA design asks for a test that proves it.
Nothing runs that test today. The chart job in CI installs both deploy modes on kind with one pod and the default SQLite
database, so the failures that only appear with two pods never run: a rolling restart that drops reads, two pods creating
the schema at the same time, two uploads to the same version racing on `maven-metadata.xml`, a lease holder that dies in the
middle of a run, and a refresh token that moves between pods.

The multi-replica fixes of the foundation waves (schema lock, metadata lock, leases, graceful drain, chart probes) each
claim to make one of these failures go away. Without a test that runs two replicas together, those claims stay unproven.

## What Changes

- A smoke test on kind with two server replicas, one MariaDB server, one MinIO bucket and one NATS server as the cluster
  bus. The replicas are installed from `charts/ingot/ci/ha-values.yaml` (added by `add-chart-ha-values`). A Python driver
  in `charts/ingot/tests/` drives Helm and kubectl and runs five checks:
  - **Rolling restart.** Reads go through the Service while the server Deployment is restarted. No read fails.
  - **Lease takeover.** A cleanup run starts on replica A. A is force-deleted while the run is observed as running. Replica B
    completes the run within the lease time-to-live plus the poll interval, and the run is recorded once.
  - **Concurrent deploys.** Two different versions of one artifact are deployed at the same moment through A and B. Afterwards
    `maven-metadata.xml` lists both versions.
  - **Sessions across replicas.** A login on A, a refresh on B. After a logout on B, the refresh token is refused on A at once.
  - **Cluster view.** The status of either replica lists both instances in its cluster section.
- The decision logic of the driver (deadlines, the failed-read count, the metadata check, the takeover window, the status
  parser) is plain Python with an injected clock. It is unit-tested in the normal chart test step, without a cluster.
- The chart job in `.github/workflows/ci.yml` starts the three dependencies before `ct install`, because `ct install`
  installs every `ci/*-values.yaml`, including the HA one. A new `ha-smoke` job runs the driver on pull requests.
- Commit type and scope: every commit of this change is `test(chart)`. Pull request title:
  `test(chart): add a two-replica smoke test on kind`.
- **Breaking: no.** No configuration key, environment variable, default, manifest or data changes. Nothing for operators
  changes, so no page under `docs/gitbook/` is touched.

### Out of scope

- Galera. The job runs one MariaDB server. Galera certification and deadlock behaviour stays with the tests of the lock
  changes (`fix-cluster-metadata-lock`, `fix-job-leases`).
- Delivery of cluster bus invalidations between replicas. That is `add-cluster-bus`'s own test. The session check here uses
  the refresh token, which is looked up in the database on every use.
- Statistics counts across a rollout (`fix-statistics-shutdown-flush`), overload and load tests (`add-overload-protection`),
  the upgrade rehearsal from the upstream chart (`add-chart-upgrade-rehearsal`), and any read and write split.
- Changes to the server, the chart templates or the chart values. Only test code, test manifests and CI files change.

## Impact

Depends on these changes and reads their behaviour, so they must be merged first:

- `fix-entrypoint-signal-handling`: the JVM must receive SIGTERM, otherwise a restart kills it hard.
- `add-readiness-endpoint` (wave 1, PR #85) and `add-graceful-drain`: a draining replica leaves the Service before it stops.
- `add-chart-lifecycle-and-warnings`: the preStop delay and the grace period used by the rollout.
- `fix-schema-init-lock`: two pods start against one empty database at the same time.
- `fix-cluster-metadata-lock`: the concurrent deploys check.
- `fix-job-leases`: the takeover check.
- `add-chart-ha-values`: `ci/ha-values.yaml` with two replicas and the database, object storage and bus keys.
- `add-cluster-bus` (merged): the NATS transport behind the cluster view.

Ordering risk: if `add-chart-ha-values` lands before this change, the existing chart job runs `ct install` against
`ci/ha-values.yaml` without its dependencies and fails. Land this change's CI step together with it, or land this change first.

Code touched: `charts/ingot/tests/` (new driver, unit tests, dependency manifests), `.github/workflows/ci.yml` (dependency
step and `ha-smoke` job). No file under `reposilite-backend/`, `charts/ingot/templates/` or `docs/gitbook/`.
