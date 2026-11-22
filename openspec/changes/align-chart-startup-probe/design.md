# Design

The decision is only which side is right, the spec or the chart. The chart is right: it is released behaviour, it has a test for each
case, and it fixes a real failure (a first start that outlasts the liveness budget is restarted under a small CPU limit).

The values in the chart that the spec must match:

- `probes.startup`: `enabled: true`, `periodSeconds: 5`, `timeoutSeconds: 2`, `failureThreshold: 60`, 300 seconds in all.
- `ingot.startupProbeEnabled` renders the probe unless `probes.startup.enabled` is set to `false`, in every mode.
- The startup probe uses the server probe path, which is the liveness path. The default `probes.path` is `/api/status/health`.
- `probes.readiness` and `probes.split.readiness` both hold `timeoutSeconds: 3` and `failureThreshold: 2`.

The requirement is renamed instead of kept under its old name, because "of the split server" would be wrong once it covers both modes.
