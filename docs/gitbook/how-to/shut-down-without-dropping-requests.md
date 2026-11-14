---
description: Stop an Ingot replica in an orderly way with INGOT_SHUTDOWN_DELAY and INGOT_SHUTDOWN_TIMEOUT, size the stop timeout of Docker or Kubernetes to match, point the probes at the status endpoints, and confirm the stop in the log.
---

# Shut down without dropping requests

When an Ingot replica stops, a request that is still running can be cut off, and a client that is downloading or uploading gets a broken connection. Two settings let the replica leave in an orderly way. `INGOT_SHUTDOWN_DELAY` keeps it serving for a few seconds after it reports `draining` on readiness, so that a load balancer takes it out of rotation first. `INGOT_SHUTDOWN_TIMEOUT` lets the requests in flight finish, up to a limit, while new requests get `503`. Both default to `0`, which keeps the immediate stop of earlier versions.

The order of the steps is described in [Rollout sequence](../explanation/multi-replica-operation.md#rollout-sequence). This page sets the values and checks them.

## Before you begin

* The longest request that must finish, for example an upload of the largest artifact your users send.
* The stop timeout of your runtime. Docker and Kubernetes both kill the process when it ends, and the process gets no time after that.

## Step 1: Choose the two values

| Setting | Meaning | How to choose |
|---------|---------|---------------|
| `INGOT_SHUTDOWN_DELAY` | Seconds the replica keeps serving after it reports `draining`. | Use it when a load balancer needs time to see the `503` on readiness. Leave it at `0` when a `preStop` sleep already gives that time, see step 3. |
| `INGOT_SHUTDOWN_TIMEOUT` | Seconds the replica waits for requests in flight, then stops whatever is still running. | The longest request you want to let finish. |

The delay plus the timeout must fit into the stop timeout of the runtime, with margin for the rest of the stop, which closes the executors and the database connection.

## Step 2: Set the stop timeout with Docker Compose

Docker sends `SIGTERM` to the container, and when it has not stopped after the stop timeout, it sends `SIGKILL`. The default timeout of `docker stop` is 10 seconds, which is shorter than a drain. Set the timeout of the service above the total:

```yaml
services:
  ingot:
    image: ghcr.io/onelitefeathernet/ingot
    environment:
      INGOT_SHUTDOWN_DELAY: "5"
      INGOT_SHUTDOWN_TIMEOUT: "30"
    stop_grace_period: 45s
```

Here the total is 35 seconds, and the 10 seconds of margin are for the rest of the stop.

## Step 3: Set the budget with the Helm chart

The chart renders the budget from `deployment.shutdown`:

```yaml
deployment:
  shutdown:
    preStopSeconds: 15
    drainSeconds: 30
```

With `preStopSeconds` above `0`, the chart runs a `preStop` sleep before Kubernetes sends `SIGTERM`. During the sleep the replica serves normally while the endpoints are removed, and then `SIGTERM` starts the drain. The sleep therefore does the work of `INGOT_SHUTDOWN_DELAY`, which the chart does not set. The chart sets `INGOT_SHUTDOWN_TIMEOUT` from `drainSeconds`, and it sets `terminationGracePeriodSeconds` to `preStopSeconds + drainSeconds + 10`. The details, including what happens when you set `deployment.lifecycle` yourself, are in [Shutdown budget](run-on-kubernetes.md#shutdown-budget).

## Step 4: Probe the status paths

Readiness and liveness must not fail because of a drain. In the all-in-one mode, the chart sends both probes to `/` by default. A draining replica answers `/` with `503`, and so does a replica at its request limit. A liveness probe that fails for its whole threshold makes the kubelet restart the container, which ends the drain early.

The status endpoints are the ones that stay answered. Point liveness at `/api/status/health`, which answers `200` during a drain, and readiness at `/api/status/ready`, which answers `503` during a drain:

```yaml
probes:
  path: /api/status/health
  readinessPath: /api/status/ready
```

`probes.path` sets liveness in the all-in-one mode, and `probes.readinessPath` sets readiness only. Set both. In split mode the chart already uses these paths, see [Run the dashboard split from the server with Helm](run-split-with-helm.md#probes-and-shutdown).

## Step 5: Check the stop in the log

Stop the replica the way your runtime does it, then read the log:

```bash
docker compose stop ingot
docker compose logs ingot | tail -n 20
```

The log shows these lines, in this order:

* `Shutting down <host>::<port>...` when the stop begins. If this line does not appear, the stop signal did not reach the Ingot process, so no drain happens. Check that the main process of the container is the Ingot JVM, or a process that passes `SIGTERM` on to it.
* `Drain timeout of <n>s ended with <m> request(s) still in flight, they are cut off` when the timeout ran out first. `<m>` counts the requests that the stop cut off. If you see this line during normal stops, raise the timeout, and check that the grace period is still above the total.

## What the status endpoints answer during a stop

| Phase | `/api/status/ready` | `/api/status/health` | Other paths |
|-------|---------------------|----------------------|-------------|
| Before the stop | `200` when every check passes | `200` | Served |
| During the delay | `503`, with `draining` first in `failing` | `200` | Served |
| During the drain wait | `503`, with `draining` first in `failing` | `200` | `503` while `INGOT_SHUTDOWN_TIMEOUT` is above `0`. Served until the stop when it is `0`. |

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| The replica restarts while it drains | A liveness probe on `/` fails during the drain | Point liveness at `/api/status/health`, see step 4 |
| The log warns that requests were cut off | The timeout is shorter than the longest transfer | Raise `INGOT_SHUTDOWN_TIMEOUT` and the grace period together |
| The process is killed before the stop finishes | The stop timeout is shorter than delay plus timeout | Raise `stop_grace_period`, or `terminationGracePeriodSeconds` through the chart values |
| No `Shutting down` line after a stop | The stop signal did not reach the Ingot process | See step 5 |
| Readiness answers `503` with `draining` first | The replica is stopping, which is what the check is for | Nothing to fix. The check returns to `200` only on a new start. |

## Related

* [Multi-replica operation](../explanation/multi-replica-operation.md) describes the sequence and what it does not provide.
* [Limit concurrent requests](limit-concurrent-requests.md) describes the refusals that the limit gives, which also spare the status endpoints.
* [Monitor status and failures](monitor-status-and-failures.md) describes the health and readiness answers in full.
