# Proposal

## Why

A load balancer or an orchestrator decides where to send traffic with a probe. Ingot answers the only probe it has,
`/api/status/health`, with `UP` as soon as the web server accepts connections. That is true before the instance can serve
anything useful: the database is unreachable, a storage root is gone, or the cluster bus has not connected yet. A replica
in that state keeps receiving requests and answers them with errors.

An operator who runs several replicas behind a load balancer sees the effect. A replica that lost its database keeps its
place in the rotation, and clients get `500` responses until someone looks at the logs. The documentation says so
plainly: the monitoring page and the multi-replica page both state that Ingot has no readiness endpoint.

This change adds one. `GET /api/status/ready` answers `200` when the instance can serve requests and `503` with the names
of the failing checks when it cannot. It is additive. The liveness answer of `/api/status/health`, the Docker `HEALTHCHECK`
of the image and every existing route keep their behaviour, so a Reposilite installation pointed at Ingot needs no change.

## What Changes

- **R1 Readiness endpoint.** `GET /api/status/ready` is added without authentication, in the same style as
  `/api/status/health`. It runs four checks:
  - `plugins`: the server has emitted `ReposiliteStartedEvent`, so every plugin initialised and the web server is bound.
  - `database`: a connection can be taken from the pool and answers a validation query within two seconds.
  - `storage`: every repository's storage root lists its contents within two seconds. An empty root answers as ready,
    so a fresh instance with no artifacts is ready.
  - `cluster`: checked only when a cluster bus URL is configured. The bus must report `connected`. Without a bus URL the
    check is not run and does not count.
- **R2 Response shape.** `200` with `{"status":"UP","failing":[]}` when every check passes. `503` with
  `{"status":"DOWN","failing":["database"]}` otherwise, listing the names of the failing checks in the order above. The body
  never carries a repository name, a connection string or an exception message. A check that does not answer within its
  bound counts as failing.
- **R3 Unchanged endpoints.** `/api/status/health`, the `HEALTHCHECK` of the container image and the manager endpoints under
  `/api/status/` keep their paths, bodies and status codes.
- **Documentation.** `docs/gitbook/reference/web-routes.md`, `docs/gitbook/how-to/monitor-status-and-failures.md` and
  `docs/gitbook/explanation/multi-replica-operation.md` lose the statements that there is no readiness endpoint, and describe
  the new route and its checks.

### Roadmap corrections

The roadmap item asked for a `state` field on `status.snapshot`. That name is the cluster bus event
(`ClusterEventType.kt:33`), not an HTTP field. Readiness is a property of one instance, and the other replicas do not need
it. Changing the bus envelope would widen a shared contract for no consumer, so the change does not touch the bus. The
state is carried in the response of the new endpoint only.

### Out of scope

- The Helm chart in `charts/ingot`. Whether split mode uses `/api/status/ready` for its readiness probe is a separate,
  later change. It belongs with the probe work of the deploy scope and needs the chart maintainer's decision. This change
  adds no chart values and no template.
- Graceful shutdown (roadmap N7). It depends on this change and follows it in wave 2.
- Caching of the check results, and per-check configuration switches. Each probe runs the checks; the cost is discussed in
  `design.md`.
- A readiness signal for the web server itself. The endpoint is served by the web server, so a reply is already proof that
  it runs.
- Changing `/api/status/health`, the Docker `HEALTHCHECK`, the console `status` command, or the `status.snapshot` bus event.
- New configuration settings. The checks use the existing database, storage and cluster settings.

## Capabilities

### New Capabilities

- `instance-readiness`: the readiness endpoint, its four checks, the response shape and the rule that its body names no
  internal detail.

## Impact

- **Type and scope.** The change ships under `feat(status)`. Its tests and docs are separate commits of their own types
  (`test(status)`, `docs(status)`). The pull request title, squash-merged onto main, is:

  `feat(status): add readiness endpoint at /api/status/ready`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, plugin or data
  changes. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer. A new route under `/api/status/` is
  additive: a client that does not call it sees nothing new.
- **Behaviour that changes by default.** None for existing clients. The new route answers only when it is requested.
- **Opt-in.** An operator who wants readiness uses the new path in a probe or load balancer. Nothing has to be set.
- **Code.** `reposilite-backend` (status, maven plugin dependency of the status plugin, cluster status interface),
  `docs/gitbook/`.

## Verification

- Each commit runs its targeted tests through `heavy` with `--max-workers=2`. The pure evaluation of the checks runs as
  unit tests without a server. The route and the database, storage and cluster wiring run as integration tests with
  `:reposilite-backend:integration --tests '<pattern>'`.
- `openspec validate add-readiness-endpoint --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-readiness-endpoint` prints nothing.
