# Design

## Context

Verified on origin/main:

- `charts/ingot/values.yaml` defaults to `replicas: 1` and the chart has no database or object storage keys beyond raw `env`.
  `ci/allinone-values.yaml` and `ci/split-values.yaml` set neither, so the chart job runs one pod on SQLite.
- `.github/workflows/ci.yml` runs `ct install --config ct.yaml --all` on kind. chart-testing installs every `ci/*-values.yaml`
  it finds in the chart directory.
- `ct.yaml` sets `helm-extra-args: --timeout 300s`.
- The HA design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`, section Testing) asks for two replicas on one
  database and one bucket, a roll with no failed request, and a NATS container for stage 3.

## Decisions

### 1. The dependencies are applied by CI, not by the chart

`ci/ha-values.yaml` needs MariaDB, MinIO and NATS to exist. Two places could create them: the chart, or a CI step.

The chart stays free of test infrastructure, because a production install must never pull in a MinIO or a NATS that the
chart itself created. So the three workloads are plain manifests under `charts/ingot/tests/ha/`, applied by a CI step before
`ct install`. The values file names their Services, and a unit test checks that the names match, so a renamed Service
fails without a cluster.

Consequence: `ct install` installs the HA values in the existing chart job too. That job therefore needs the step as well.

### 2. Python driver, not a Kotlin integration test

The checks drive Helm, kubectl and the public HTTP API of real pods. The chart tests already use Python with `unittest`, and
the driver needs no Gradle build. The Kotlin integration task would start an in-process server, which cannot show pod
restarts, force deletes or two pods sharing one database.

The decisions that can be wrong (when a deadline has passed, whether two versions are both listed, whether a takeover came
within its window) are pure functions that take an injected clock. They are unit-tested in the normal chart test step.

### 3. Addressing: reads through the Service, writes and takeover by pod IP

Reads during the rolling restart go through the Service, because readiness and draining are what keep them successful.
The concurrent deploys and the force-delete go to named pods, because the test must know which replica received which
request. The driver picks the pod IPs from the endpoints of the Service at the start of each check.

### 4. Session check narrowed to the refresh token

Roadmap item P1 asks that a revoked session is rejected on replica A "within the documented window". The identity model
(`docs/superpowers/specs/2026-10-15-identity-model.md`, around line 889) says a session token is not looked up on each request.
It keeps working until its `exp`, at most the session lifetime (15 minutes by default) after the revocation.

The smoke test therefore asserts the immediate part only: a refresh on replica A after logout on replica B is refused with
401. Rejection of the session token itself belongs to the cluster bus invalidation, which `add-cluster-bus` tests.

### 5. A run that is never observed as running fails the check

The takeover check kills the holder only after it has seen the run as running. If the run finishes first, the check reports
that it could not observe the run and fails. It does not pass silently, and it does not sleep and hope.

### 6. Single MariaDB server, not Galera

Staging runs Galera. The smoke test runs one MariaDB server, because a Galera cluster on kind is heavy and the lock behaviour
that depends on Galera is covered by the lock changes. The gap is named in the proposal rather than hidden.
