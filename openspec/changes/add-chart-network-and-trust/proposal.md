# Proposal

## Why

The Helm chart deploys Ingot the way the upstream chart does, and it leaves the network open. Any pod in the cluster can reach
the Ingot port, the same port that serves artifacts, the API and the metrics. Operators who run Ingot in a shared cluster
have no chart setting that narrows this, so they write their own policy by hand, and the chart has no place to document what
Ingot needs to work (the gateway, the monitoring namespace, the dashboard in split mode, and outbound access for mirror
proxying).

The same operators run internal certificate authorities. A Galera cluster, a NATS broker, an in-cluster S3 endpoint or an
internal identity provider often presents a certificate that the public trust store does not know. Ingot has no setting for
an extra CA, and the only workaround is to replace the JDK trust store, which also drops the public CAs that mirror proxying
needs (`docs/gitbook/how-to/configure-cluster-bus.md:81` says the same). The chart cannot pass such a bundle to the server
today.

## What Changes

- **Opt-in NetworkPolicy.** New `networkPolicy` values. With `networkPolicy.enabled: false` (the default) the chart renders
  nothing. When enabled, the chart renders a policy for the server. The server accepts the Ingot port only from the namespaces
  and pods listed in `networkPolicy.ingress`. In split mode the chart also renders a policy for the dashboard, and the server
  accepts the dashboard pods. Egress restriction is a second opt-in under `networkPolicy.egress`. When it is on, only DNS and
  the listed destinations are allowed, and `allowInternet` allows public addresses while excluding the RFC 1918 ranges.
- **Opt-in extra CA certificates in the chart.** New `extraCaCerts` values that name exactly one ConfigMap or Secret holding
  PEM files. When enabled, the chart mounts it read-only into the server, sets `INGOT_EXTRA_CA_CERTS` to the mount path and
  provides a writable `emptyDir` at `/tmp`, so the image can merge the bundle into a private trust store. The image side is a
  separate change (see Impact). With the value unset, the rendered manifests do not change.
- **Documentation.** `docs/gitbook/how-to/run-on-kubernetes.md` gets a section for the network policy and one for the extra CA
  bundle, with the values and the verification steps.

Ingot's name for the variable is `INGOT_EXTRA_CA_CERTS`. The image reads `REPOSILITE_EXTRA_CA_CERTS` as a fallback (in the
image change), so the naming rule of `CLAUDE.md` holds.

### Out of scope

- The Artifact Hub metadata (`home`, `icon`, annotations, `artifacthub-repo.yml` and its `oras` push). It is `chore(chart)`,
  and a change that mixes types is two changes, so it ships as `add-chart-artifact-hub`.
- The entrypoint and image part of the extra CA bundle (the `keytool` import, the trust store flag and the writable path
  handling for the non-root `user:` path). It is `feat(image)` and ships as `add-extra-ca-bundle`.
- Signing of the chart and an `artifacthub.io/signKey` annotation (`sign-release-artifacts`).
- A CI job that runs NetworkPolicy enforcement on kind. The check runs locally before the pull request (see tasks), and a
  committed job would be a separate `ci(chart)` change.
- Default-deny for the database, the bus or storage egress, and a policy for the chart's optional Ingress or HTTPRoute
  resources beyond the allow-list.
- Any change to the default render, the default values or the image.

## Capabilities

### New Capabilities

- `helm-network-policy`: the opt-in NetworkPolicy of the server and the dashboard, its ingress allow-list and its opt-in egress
  restriction.
- `helm-extra-ca-bundle`: the opt-in mount of a PEM bundle for the server and the environment variable that names it.

## Impact

- **Type and scope.** The change ships under `feat(chart)`. The tests are committed first as `test(chart)`, and the docs as
  `docs(deploy)`, following the pattern of `harden-http-security`. The pull request title, squash-merged onto main, is:

  `feat(chart): add opt-in network policy and extra ca bundle`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume or manifest has to change for an
  existing release. The new values default to off, and the new environment variable is only set when `extraCaCerts` is enabled.
  The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None. `test_equivalence.py` (allInOne renders identical to upstream chart 1.4.5) keeps
  passing without edits.
- **Dependencies on other changes.**
  - `add-deploy-modes` (merged): supplies the split-mode dashboard that the dashboard policy protects.
  - `add-extra-ca-bundle` (`feat(image)`, not written here): the image merges the bundle. The chart part can merge first, since
    it changes nothing when the variable is unused. The end-to-end check and the documentation claim need the image change.
  - `add-chart-artifact-hub` (`chore(chart)`, not written here): no file overlap.
  - Ordering: `add-chart-lifecycle-and-warnings` (Wave C) and `add-chart-observability` (Wave D) also edit
    `charts/ingot/templates/deployment.yaml`. Whichever lands second rebases its template edit.
- **Code.** `charts/ingot/` (`templates/networkpolicy.yaml`, `templates/deployment.yaml`, `values.yaml`, `values.schema.json`,
  `tests/`) and `docs/gitbook/how-to/run-on-kubernetes.md`.

## Verification

- `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v` passes, with the new tests and `test_equivalence.py`.
- `helm lint charts/ingot` passes with the new values set and unset.
- On a local kind cluster with an enforcing CNI (Calico), the server and dashboard policies allow the gateway, monitoring and
  dashboard traffic and refuse a pod in an unlisted namespace, and the readiness probe stays green. The run goes through
  `HEAVY_SLOTS=1 heavy`, one cluster at a time.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-chart-network-and-trust` prints nothing.
