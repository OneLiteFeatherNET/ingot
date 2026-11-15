# Proposal

## Why

An operator who runs Ingot on Kubernetes with more than one replica writes the same workarounds by hand. The database
connection string has to sit in a values file or in a Secret that the chart cannot name, so the operator wires it in
through a raw `env` entry. The S3 keys and the cluster bus password follow the same path, and the chart can neither
check nor document them. Two replicas can land on one node, and one node failure then takes both. The chart renders
`image.name:image.tag` only, so pinning a digest needs the tag workaround that the Kubernetes guide describes.

The second problem is the namespace. A cluster that enforces the Pod Security Standards `restricted` level rejects the
default pod. The image starts as root, so `runAsNonRoot` cannot hold, and the chart sets no seccomp profile and drops no
capability. The operator has to write the security context and the two writable `emptyDir` volumes (`/tmp` and
`/var/log/reposilite`) by hand, as the staging release does.

Both gaps are closed by values that are off by default. A release that does not set them renders exactly what it renders
today.

## What Changes

- **C1 Typed HA values.** New optional keys, each rendering nothing when unset:
  - `database.existingSecret` and `database.secretKey` (default `database`) set `INGOT_LOCAL_DATABASE` from a Secret key.
  - `objectStorage.existingSecret` sets `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` from keys of the same names, and
    `AWS_REGION` as an optional key. Empty repository keys then fall back to the AWS default credential chain, as the
    S3 guide already describes.
  - `clusterBus.url` sets `INGOT_CLUSTER_URL`. `clusterBus.passwordSecret` sets `INGOT_CLUSTER_PASSWORD` from the key
    `password`. `clusterBus.credentialsSecret` mounts a NATS credentials file read-only and sets
    `INGOT_CLUSTER_CREDENTIALS` to it.
  - `deployment.topologySpreadConstraints` and `dashboard.topologySpreadConstraints` pass through unchanged.
  - `image.digest` renders `name@digest` in place of `name:tag`.
  - `values.schema.json` types every new key. The root and `deployment` stay open to unknown keys.
- **C2 Restricted security profile.** New `deployment.securityProfile`, one of `""` (default), `restricted` or
  `restricted-openshift`.
  - `restricted` sets `runAsNonRoot`, `runAsUser`, `runAsGroup` and `fsGroup` to `977`, `seccompProfile:
    RuntimeDefault`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]` and `readOnlyRootFilesystem: true`.
    It mounts two chart-managed `emptyDir` volumes, `ingot-tmp` at `/tmp` and `ingot-logs` at `/var/log/reposilite`.
  - `restricted-openshift` sets the same fields without the fixed ids, for clusters that assign the uid.
  - Setting a profile together with `podSecurityContext.enabled` or `containerSecurityContext.enabled` fails at render
    time and names both keys.
- **Tests.** Golden renders of the defaults are committed before the templates change. Render tests cover every new key.
  CI installs the restricted render into a namespace labelled `pod-security.kubernetes.io/enforce=restricted`, and a
  read-only container run checks the same profile outside Kubernetes.
- **Documentation.** `docs/gitbook/how-to/run-on-kubernetes.md` gets the digest key, the Secret examples, the HA values
  and the restricted profile with the ownership step for an existing volume.

### Out of scope

- A digest for the dashboard image. The same helper can take it later.
- NOTES.txt warnings for several replicas, RWX volumes and memory targets. Those belong to
  `add-chart-lifecycle-and-warnings`.
- The `ci/ha-values.yaml` file in the CI chart job. It needs MariaDB, MinIO and a bus that the kind job does not run. See
  design D1.
- The HA smoke test on kind (`add-ha-smoke-test`), the shared configuration mount (`add-shared-configuration-reload`),
  the `_FILE` secrets (`add-file-secrets`) and the PrometheusRule.
- Any change to a default value, to the image user, or to the probes.
- A proof of the `restricted-openshift` variant on a real OpenShift cluster. It is covered by render tests only.

## Capabilities

### New Capabilities

- `chart-ha-values`: the opt-in values for an external database, object storage credentials, the cluster bus, topology
  spread and the image digest, and the render rules that keep unset values inert.
- `chart-security-profile`: the opt-in restricted Pod Security profile and the writable paths it needs.

## Impact

- **Type and scope.** The change ships under `feat(chart)`. The pull request title, squash-merged onto main, is:

  `feat(chart): add typed HA values and an opt-in restricted profile`

- **Breaking marker.** No breaking change. No operator has to change a configuration key, an environment variable, a
  volume or a manifest to keep today's deployment. The title carries no `!`.
- **Operator action only when opting in.** The restricted profile runs the server as uid `977`. A volume whose files
  belong to another uid, for example one written by a deployment that set `runAsUser: 999`, cannot be written by that
  process. The how-to gives the one-time `chown` for that case. The fsGroup setting does not change an owner id.
- **Dependencies on other changes.**
  - `add-deploy-modes` (merged, not archived) states that the chart SHALL NOT set `runAsUser` on the server. This change
    keeps that as the default and adds the restricted profile as the named opt-in.
  - `add-chart-upgrade-rehearsal` (wave A) proves that the defaults keep the data volume. This change must pass it
    unchanged.
  - `add-chart-lifecycle-and-warnings` (wave C, parallel) edits `values.yaml`, `deployment.yaml` and
    `values.schema.json` too. There is no semantic dependency. Whichever change lands second rebases on the first.
  - `add-file-secrets` (wave C, parallel) changes how `env` is read by the server. The chart changes here only add
    `secretKeyRef` entries, so the two do not conflict.
- **Code.** `charts/ingot/` (values, schema, templates, tests, fixture), `.github/workflows/ci.yml` (one restricted
  smoke step), and `docs/gitbook/how-to/run-on-kubernetes.md`.

## Verification

- `helm lint --strict` on both CI values files and on the HA fixture.
- `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v` through `heavy`, which includes the golden
  renders and `test_equivalence.py`.
- The read-only container smoke test through `HEAVY_SLOTS=1 heavy`.
- `openspec validate add-chart-ha-values --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-chart-ha-values` prints nothing.
