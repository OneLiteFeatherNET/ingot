# Design

## Context

The chart renders one or two workloads from the same values. Its allInOne render must stay identical to the upstream chart
1.4.5 for every value nobody sets, so every new behaviour is either a split-only default or an opt-in value. The server's
readiness endpoint and its draining check come from two other changes.

## Decisions

### Readiness moves to `/api/status/ready` in split only

Liveness stays on `/api/status/health` in both modes. A database outage must mark a pod not ready, and it must never restart
the pod, because a restart does not fix an unreachable database. In all-in-one the probe stays on `/` because the root path
is the dashboard and upstream behaviour, and the readiness endpoint is not yet the contract there.

### Split readiness gets a wider budget

The readiness check of the server runs a database query and a storage listing, each bounded at two seconds. A one second
timeout cannot hold that bound, so split uses 3 seconds and two failures. The numbers live under `probes.split` so that
the all-in-one values in `probes.readiness` keep their upstream meaning.

### Shutdown is opt-in and the grace period is derived

`terminationGracePeriodSeconds` keeps its upstream default of 60, so an operator who sets nothing sees no change. When an
operator sets the preStop or drain value, the grace period is their sum plus 10 seconds, so the kubelet never sends SIGKILL
during the budget the operator asked for.

`preStopSeconds` maps to a `preStop` sleep, which keeps the pod in the endpoint list while the proxy removes it. The image
is built on Ubuntu noble with coreutils, so `exec` with `sleep` works on every supported cluster version. The `sleep` lifecycle
action would need Kubernetes 1.30 or later.

`drainSeconds` maps to `INGOT_SHUTDOWN_TIMEOUT`, which bounds the wait for in-flight requests in the server. It does not map to
`INGOT_SHUTDOWN_DELAY`, because the preStop sleep already gives the load balancer the time that the delay would give it.

### Warnings, not failures

The conflict between a strict check and the drop-in promise is decided here: an upstream user who changes only the image line
must still install. A warning in the notes reaches an operator who reads the install output, and it does not block the install
of a user who has a working but unusual setup. Every check reads only `env` and the values, so a value that arrives through
`envFrom` is not inspected, and the warnings say so in the same way as the existing cluster bus warning.

## Risks

- The split readiness probe fails when the database is unavailable. This is intended, and it is the behaviour that
  `add-readiness-endpoint` provides. A split release pinned to an older image would fail readiness with a 404. The release
  notes must name the minimum image for split readiness.
- The `preStop` sleep and the grace period add up to the rollout time. A large `preStopSeconds` slows every rollout, so the
  docs must say that the values are per pod and multiply with the replica count of a rolling update.
