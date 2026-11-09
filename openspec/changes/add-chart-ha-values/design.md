# Design

## Context

The chart renders the server Deployment from `values.yaml`. Its `env` list is the only way to pass Ingot settings, so
every HA setting goes through it today. The helper and the deployment template render `env:` only when `env` is set or
the mode is `split`. The image has no `USER`, and the entrypoint starts as root unless the pod sets a non-zero uid.
`helm template` cannot read the cluster, so the chart cannot check that a referenced Secret exists.

## Decisions

### D1 The HA fixture lives under tests, not in `ci/`

Chart-testing installs every `ci/*-values.yaml` on kind, and the lint job runs `helm lint --strict` on each of them.
An HA file needs an external database, object storage and a bus, none of which the kind job starts. Under `ci/` it
would fail the install. The file is `charts/ingot/tests/fixtures/ha-values.yaml`. Unit tests render and lint it. The
HA smoke test (`add-ha-smoke-test`) moves it into `ci/` once those services exist.
This differs from the path in roadmap item P2. The proposal records it.

### D2 Secrets are referenced, never read or rendered

Values hold the name of a Secret and a key. The chart renders `valueFrom.secretKeyRef`, so the value stays in the
cluster. The chart does not use `lookup`, because `helm template` would return nothing and the render would differ
between `template` and `install`. A key that may be absent is marked `optional: true` only where the setting is
optional (`AWS_REGION`, the bus password when the URL carries one).

### D3 Object storage keys use the AWS variable names

The Secret keys are named `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `AWS_REGION`. The env vars take their names
from the keys, so the docs need no mapping table and an operator can copy a Secret from the AWS CLI setup. The S3 guide
already says that empty repository keys fall back to these variables.

### D4 Chart-managed emptyDir volumes get their own names

The restricted profile adds `ingot-tmp` and `ingot-logs`. The staging fixture defines a volume called `tmp` through
`additionalVolumes`, so a chart-managed `tmp` would collide with it. A mount at `/tmp` that the user also sets is the
user's responsibility, and the docs say so. The profile does not render anything when unset.

### D5 No init container chowns the data volume

An init container that runs `chown` would run as root, which the restricted namespace forbids. The profile therefore
runs the server as `977` and expects the data to be owned by `977`. The how-to gives a one-time `chown -R 977:977` for
a volume that was written under another uid. The entrypoint's own ownership step does not run in this mode, because it
only runs as root.

### D6 Conflicting new inputs fail; upstream inputs never do

The NOTES guard warns only, because a `fail()` would stop an upstream user's `helm upgrade` when only the image line
changed (critic note 2). The checks in this change run only when an operator sets a new key in a conflicting way: a
profile with a security block, or the same variable in `env` and in a generated entry. Those failures name both keys.
No upstream values file can reach them.

### D7 Topology spread has no default

A default spread with `whenUnsatisfiable: DoNotSchedule` would keep a single-node cluster from scheduling the second
replica. The chart passes the list through and ships an empty default, and the HA fixture sets the constraint.
