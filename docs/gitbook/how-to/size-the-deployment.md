---
description: Choose the JVM heap and the container memory and CPU limits of an Ingot pod by a method you can repeat on your own workload, with the chart defaults and their source. No measured values are given here.
---

# Size the deployment

This page describes how to choose the heap size and the memory and CPU limits of an Ingot pod. It gives no recommended values, because no measurement of Ingot under a recorded workload exists yet. The chart defaults are shown only as the starting point, and they are not measured.

## The chart defaults

| Setting | Default | Source |
|:--------|:--------|:-------|
| `JAVA_OPTS`, which sets the heap with `-Xmx` | `-Xmx64M` | `env` in `charts/ingot/values.yaml` |
| Memory request | `128Mi` | `resources.requests` in `charts/ingot/values.yaml` |
| Memory limit | `256Mi` | `resources.limits` in `charts/ingot/values.yaml` |
| CPU request | `1.0` | `resources.requests` in `charts/ingot/values.yaml` |
| CPU limit | `2.0` | `resources.limits` in `charts/ingot/values.yaml` |

These are the upstream Reposilite values. A limit of `256Mi` leaves little room beyond a `64M` heap, so an instance with real traffic needs its own numbers. Set the values in your own values file, with `JAVA_OPTS` in `env`.

## Step 1: Turn on the metrics

The JVM and request metrics come from the bundled metrics plugin. It is off by default. Set `INGOT_METRICS_ENABLED=true` in the server environment, then scrape the `/metrics` endpoint. The endpoint needs `INGOT_PROMETHEUS_USER` and `INGOT_PROMETHEUS_PASSWORD`, and the plugin does not start without them. See [Monitor status and failures](monitor-status-and-failures.md) and the [environment variables](../reference/environment-variables.md#metrics-switch) reference.

The JVM series used below are `jvm_memory_used_bytes`, `jvm_memory_committed_bytes` and `jvm_memory_used_after_last_gc_bytes`, with the label `area` set to `heap` or `nonheap`. The request series are `ingot_http_server_requests_total` and `ingot_http_server_request_duration_seconds`.

## Step 2: Choose the heap from the live set

The heap has to hold the data that is still referenced after a garbage collection, plus room for the work of the next requests. The live set is the best estimate of the first part.

1. Read `jvm_memory_used_after_last_gc_bytes` with `area="heap"` over a period that includes your busiest hour. Take the highest value of the period.
2. Choose `-Xmx` above that value. Leave headroom for the concurrent uploads of step 4, and for the peaks between collections.
3. Check the result against `jvm_memory_used_bytes` with `area="heap"`. The used value may rise to the maximum before a collection, and it must not stay close to the maximum after one.

Set `-Xmx` in `JAVA_OPTS`, for example `-Xmx768M`. A heap that is too small shows as frequent collections and as requests that slow down.

## Step 3: Choose the memory limit from the working set

The memory limit of the container covers the heap and the memory outside it, such as the metaspace, the thread stacks and the native buffers.

1. Read the working set of the container, `container_memory_working_set_bytes` for the Ingot container, which the kubelet exports. Take the highest value over the same period.
2. Read the non-heap memory with `jvm_memory_committed_bytes` and `area="nonheap"`. This is the part of the working set that the heap does not cover.
3. Set the memory limit above the heap plus the non-heap value, with headroom. The limit must stay above the working set, or the kernel ends the container.

The memory request should be close to the working set in normal operation, so the scheduler places the pod on a node with room for it.

## Step 4: Add the headroom for uploads

An upload above 8 MiB is sent to the bucket in parts of 8 MiB, and one part is held in the heap while it is sent. Each concurrent upload to an S3 repository therefore needs one part of heap, see [Use S3 storage](use-s3-storage.md#large-uploads). Count the largest number of concurrent uploads that your clients can start, and add that many 8 MiB parts to the heap.

## Step 5: Choose the CPU from the request rate

CPU use follows the request rate and the work per request. Read the rate of `ingot_http_server_requests_total` and the latency of `ingot_http_server_request_duration_seconds` at your busiest hour. Set the CPU request to the normal use, and the CPU limit above the peak. A CPU limit that is too low shows as latency that rises with the request rate, and not as a restart.

## Step 6: Check the result under load

Repeat the measurement after each change of the values. Record the date, the workload and the query for each figure. Keep the numbers next to the values in your own values file, so that the next upgrade can compare against them.

## Related

* [Run on Kubernetes](run-on-kubernetes.md)
* [Monitor status and failures](monitor-status-and-failures.md)
* [Use S3 storage](use-s3-storage.md)
