---
description: Cap the requests that one Ingot replica works on at the same time, choose the value, and check that the status probes stay answered and that clients see the refusal as expected.
---

# Limit concurrent requests

Ingot can cap the number of requests that one replica works on at the same time. When the cap is reached, a new request is answered at once with `503 Service Unavailable` and `Retry-After: 1`. Nothing waits in a queue. The cap keeps a burst of large transfers from filling the memory and the threads of the replica and slowing every other client down. It is off by default.

This page sets the cap and checks it. The variables are listed in [Request limit variables](../reference/environment-variables.md#request-limit-variables), and the answer is described in [Overload answers](../reference/http-api.md#overload-answers).

## Before you begin

* A running Ingot. The limit is read once at start, so a change needs a restart.
* An estimate of how many transfers one replica serves at its busiest time. Downloads and uploads matter most, because a request keeps its slot until its last byte is written.

## What counts against the limit

* A request takes a slot when it is admitted and gives it back when its response is complete. A download that streams for ten minutes holds its slot for ten minutes.
* `/api/status/health` and `/api/status/ready` never take a slot and are never refused, whatever the load.
* A refused request takes no slot and does not reach the database.
* The count is kept per replica. Two replicas, each with a limit of 64, admit up to 128 requests together.
* Ingot writes no log line for a refusal, and the `ingot_http_server_*` metrics are recorded in route hooks that a refused request never reaches. Count the refusals at the proxy or the load balancer, where the status code is visible.

## Choose the value

No value suits every installation, so measure. Take the number of long transfers that one replica serves at the busiest time, add room for the short requests, and use the sum. `INGOT_HTTP_MAXCONCURRENT=64` in the examples below is a placeholder, not a recommendation.

## Steps

1. Set the variable in the environment of the server. With Docker Compose:

   ```yaml
   services:
     ingot:
       environment:
         INGOT_HTTP_MAXCONCURRENT: "64"
   ```

   With the Helm chart, add it to `env`:

   ```yaml
   env:
     - name: INGOT_HTTP_MAXCONCURRENT
       value: "64"
   ```

   The system property `-Dingot.http.maxConcurrent=64` in `JAVA_OPTS` does the same, and it wins when both are set.

2. Restart the server. A value that is not a positive whole number, such as `0`, `-1` or `many`, stops the start, and the message names `INGOT_HTTP_MAXCONCURRENT`. Read the start in the log before you assume the limit is in force.

3. Check the cap while transfers are running. Start more downloads in parallel than the limit, using an artifact of your own that takes a few seconds to transfer, and ask the health endpoint at the same time:

   ```bash
   for i in $(seq 1 80); do
     curl -s -o /dev/null -w '%{http_code}\n' "https://repo.example.org/releases/org/example/big/1.0/big-1.0.jar" &
   done
   curl -s -o /dev/null -w 'health %{http_code}\n' https://repo.example.org/api/status/health
   wait
   ```

   With 80 downloads and a limit of 64, the requests that arrive while all 64 slots are busy get `503`, and the rest get `200`. How many get which answer depends on how fast the transfers finish. The health line is `health 200` in every run.

## Read the result

* `200` means that the request got a slot.
* `503` with `Retry-After: 1` means that every slot was busy at the moment of the request. The body is an error response with the message `The server is busy with other requests, retry in a moment`. A client that sends `Accept: application/problem+json` gets the same content as problem details.

A `503` is a transient answer. Test the limit with the build tool that your builds use, because tools differ in how they handle a `503` and in whether they retry it.

## When the limit is too low

If normal traffic gets `503` answers, the value is below the number of requests that overlap at the busiest time. Raise it, then repeat step 3. The refusal does not show in the Ingot metrics, so compare the `503` count at the proxy with the request rate from `ingot_http_server_requests_total` when you judge the value.

## Keep the probes out of the limit

The status endpoints are exempt. A liveness probe on any other path, such as `/`, is refused while the cap is reached. If the refusals last long enough for the probe to fail its threshold, the orchestrator restarts the replica. In the all-in-one mode of the Helm chart, both probes use `/` by default. Point the liveness probe at `/api/status/health`. The values are in [Shut down without dropping requests](shut-down-without-dropping-requests.md#step-4-probe-the-status-paths).

## Related

* [Shut down without dropping requests](shut-down-without-dropping-requests.md) covers the drain, which refuses requests in the same way.
* [Environment variables](../reference/environment-variables.md#request-limit-variables) lists the variable, its property and its fallback.
* [HTTP API overview](../reference/http-api.md#overload-answers) describes the answer.
