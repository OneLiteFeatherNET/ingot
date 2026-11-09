# Tasks

Each group is one or more commits of one Conventional Commits type each, all under the chart. The change ships as
`feat(chart)`; its test and docs commits follow the precedent of the merged changes. Tests are written first and seen to
fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no
shared mutable state, a temporary directory per test, and assertions with messages. Chart renders run through `helm
template` and `helm lint` in a subprocess, with no network. Local Python runs go through `heavy`. Heavy verification uses
`HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2` where a Gradle run is needed. kind and Testcontainers
run one at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-chart-ha-values`: this change and nothing else. Verify:
  `openspec validate add-chart-ha-values --strict` passes and the dash grep from the proposal prints nothing.

## 2. Golden renders of the defaults

- [ ] 2.1 `test(chart)`: store golden renders of the defaults before any template changes: `allInOne` with no values, `split`
  with `mode: split`, and the staging fixture with the migration overrides. Each golden is the parsed manifests sorted by kind
  and name, under `charts/ingot/tests/fixtures/golden/`. Tests compare the render to the golden. Verify: the tests pass on the
  unchanged chart, and a temporary change to one default makes them fail (checked locally and reverted, not committed).

## 3. Typed HA values (C1)

- [ ] 3.1 `test(chart)`: write failing tests: `database.existingSecret` renders a `secretKeyRef` and no literal value; the same
  variable in `env` fails naming both keys; `objectStorage.existingSecret` renders the two keys and `AWS_REGION` with
  `optional: true`; `clusterBus.url`, `passwordSecret` and `credentialsSecret` render the variables and the read-only mount;
  topology spread is rendered for the server and the dashboard when set and absent when empty; `image.digest` renders
  `name@digest` and wins over the tag; the schema rejects `image.digest: 12` and accepts an unknown root key; the rendered
  output holds no literal secret; the fixture `tests/fixtures/ha-values.yaml` renders two replicas and lints. Verify: the new
  tests fail before 3.2, and the golden tests of 2.1 pass.
- [ ] 3.2 `feat(chart)`: add the values with defaults that render nothing, the helper that renders the generated `env` entries
  before the user's `env`, the volume and mount for the bus credentials, the topology spread pass-through for both Deployments,
  the digest in the image line, the typed schema entries, and `tests/fixtures/ha-values.yaml`. Verify: the tests of 3.1 pass,
  the goldens of 2.1 pass unedited, and `helm lint --strict` is clean with the existing CI values files.
- [ ] 3.3 `docs(deploy)`: update `docs/gitbook/how-to/run-on-kubernetes.md`: replace the digest-in-tag workaround with
  `image.digest`, add the Secret examples for the database and the object storage keys, add the bus and topology keys, and keep
  the one-replica SQLite default statement. Verify: every new key named in the page exists in `values.yaml`, and the dash grep
  prints nothing.

## 4. Restricted security profile (C2)

- [ ] 4.1 `test(chart)`: write failing tests: an unset profile renders the golden output; `restricted` renders the pod and
  container fields with uid `977`; `ingot-tmp` and `ingot-logs` are mounted at `/tmp` and `/var/log/reposilite`; the data volume
  is unchanged; `restricted-openshift` renders no `runAsUser`, `runAsGroup` or `fsGroup`; a profile together with
  `podSecurityContext.enabled` fails naming both keys; `privileged` is rejected by the schema. Verify: the new tests fail before
  4.2.
- [ ] 4.2 `feat(chart)`: add `deployment.securityProfile`, its template branches, the two chart-managed volumes and the schema
  enum. Verify: the tests of 4.1 pass and the goldens of 2.1 pass unedited.
- [ ] 4.3 `test(chart)`: add the CI step that installs the restricted render into a namespace labelled
  `pod-security.kubernetes.io/enforce=restricted` after the chart job's install, and waits for the pod to be ready with
  `kubectl wait`. Add a read-only container check that runs `docker run --read-only --user 977:977 --cap-drop ALL` with
  `/tmp` and `/var/log/reposilite` as writable mounts and asserts `GET /api/status/health` returns `200`. Verify: locally,
  `HEAVY_SLOTS=1 heavy` runs the container check and it passes; the kind step runs in CI on the pull request and passes.
- [ ] 4.4 `docs(deploy)`: document the restricted and restricted-openshift profiles in
  `docs/gitbook/how-to/run-on-kubernetes.md`, including the one-time `chown -R 977:977` for a volume that was written under
  another uid, and the note that the profile needs the data owned by uid `977`. Verify: the uid and key names in the page match
  the rendered output, and the dash grep prints nothing.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `feat(chart): add typed HA values and an opt-in restricted profile`. Read
  the repository's pull request template and fill its sections. The description tells the operator's story (the Secret that the
  chart could not name, the namespace that refused the pod) without template internals and without a session link or
  attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every
  commit, the diff touches no file under `openspec/`, and the body contains no em or en dash.
