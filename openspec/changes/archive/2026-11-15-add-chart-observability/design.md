# Design

## Context

Three opt-in features of the chart write into the same two places: the server container's environment and its pod
template. The agent and the JVM flags both need `JDK_JAVA_OPTIONS`. The shared settings file needs a mount and an
environment variable. Each one has to leave the default render alone and must not clash with what an operator already set
in `env`.

## Decision 1: one merged JDK_JAVA_OPTIONS entry

Kubernetes keeps the last of two env entries with the same name, and the staging values show the failure: a second
`JDK_JAVA_OPTIONS` would silently drop the `-javaagent` flag. The chart therefore builds one value.

- The operator's own `JDK_JAVA_OPTIONS` value, if any, comes first, unchanged.
- The chart appends its flags in a fixed order: the JVM block, then the agent flag.
- If the operator's value and the JVM block both set the same `-XX` flag, the render fails and names the flag. Two values
  for one flag in one variable depend on ordering, so the chart refuses to guess.
- The render is done in one helper that merges entries by name, so `env` never contains a duplicate name.

## Decision 2: JAVA_OPTS and the heap

The launcher reads `JDK_JAVA_OPTIONS` before the command line, and the command line carries `JAVA_OPTS`. An explicit `-Xmx`
overrides `MaxRAMPercentage`. The chart
default sets `JAVA_OPTS=-Xmx64M`, so enabling the percentage alone would do nothing. NOTES.txt warns in that case, and does
not fail, in line with the existing cluster-bus warning. The warning names the two ways out: remove the `-Xmx` from
`JAVA_OPTS` or drop the percentage.

## Decision 3: the shared file path travels in an environment variable

The backend reads the shared file from the `--shared-configuration` argument, and the entrypoint takes its arguments from
`INGOT_OPTS`, falling back to `REPOSILITE_OPTS`. Appending a flag to those variables would have to know which of the two
the operator uses, and `envFrom` values cannot be seen at render time. Writing `INGOT_OPTS` could also drop arguments
that come from `REPOSILITE_OPTS` in `envFrom`.

The chosen path is an environment variable, `INGOT_SHARED_CONFIGURATION_FILE` (with the `REPOSILITE_` fallback), added by
`add-shared-configuration-reload`. It needs no argument merge, and it fits the rule that new settings are environment
variables.

Alternative, if that change cannot take the variable: the chart appends `--shared-configuration <path>` to the variable
the entrypoint reads, and fails when `envFrom` supplies options it cannot see. This is the fallback, not the plan.

## Decision 4: the mount is a directory, and reload handles updates

The file is mounted as a directory at `/app/shared-config`, read-only. The data volume at `/app/data` stays as it is, so
the PVC is not shadowed. A `subPath` mount would not receive ConfigMap updates, so it is not used. For chart-owned
content, the checksum annotation restarts the pods on change. For an existing ConfigMap no annotation is set, because the
reload picks up changes without a restart.

## Decision 5: the reload needs the read-only guard

A read-only mount breaks the dashboard's save path, which writes the file back. The chart therefore may only enable the
mount together with the 409 guard for file-managed domains. Both come from `add-shared-configuration-reload`, and this
change lists it as a dependency. The chart does not set `INGOT_SHARED_CONFIGURATION_RELOAD` without the mount.

## Decision 6: alerts only reference metrics the image exports

Rules reference only metric names that `add-cluster-metrics` registers and the JVM metrics that the prometheus plugin
already registers through `JvmMetrics`. A rule on a name that does not exist would never fire, and nothing would report it.
A CI check compares the metric names in the rules with a checked-in list of exported names and fails on a mismatch.

## Not decided here

- The agent image digest. Renovate pins it with the tag, as for other images, and the apply step sets the first value.
- Whether the dashboards are generated from a source or kept as JSON. They are kept as JSON, as in `files/dashboard-segments.json`.
