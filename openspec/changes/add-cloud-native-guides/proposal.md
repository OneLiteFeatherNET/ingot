# Proposal

## Why

An operator who runs Ingot on Kubernetes meets the same questions after the first install: how the server gets S3 access
without static keys, how TLS terminates at the cluster gateway, how a large artifact gets through the edge, how secrets stay
out of Git, and how big the pods should be. The chart installs Ingot, but the documentation does not answer these questions.
The S3 guide lists the access and secret keys as fields and does not say they can be left empty. The Kubernetes guide says its
sizing values "were not measured". The TLS guide only describes TLS inside Ingot, which a gateway deployment does not use.
Operators answer these questions by trial on production clusters.

Most of the answers already hold in the code. Ingot passes no credentials to the S3 client when the keys are empty, so the AWS SDK
default chain applies. The chart already renders the service account annotations that IRSA needs. The gaps are in the guides, and
this change fills them. Where the product does not yet behave the way an operator would want, the guide says so, and the fix
ships as its own change.

## What Changes

- **Keyless S3.** The S3 how-to gets a section on credentials from the workload identity (IRSA and Pod Identity), with the
  chart value for the service account annotation. The guide states that Ingot uses the keys only when both are set.
- **TLS at the gateway.** A new how-to shows cert-manager issuing the certificate for a Gateway listener, with the chart's
  HTTPRoute attached to it, and states that in-app TLS is not needed in that setup.
- **Large uploads at the edge.** A new how-to lists the body size and timeout limits that the edge must allow, names the
  ingress annotations for the common controllers, and states that the chart renders no timeout field on the HTTPRoute.
- **GitOps secrets.** A new how-to shows SOPS, External Secrets Operator and Sealed Secrets as ways to deliver Secrets
  that the chart reads through `env` and `envFrom`. It states which values Ingot reads from the environment today and which
  live in repository settings.
- **Sizing.** A new how-to gives the method for sizing heap and memory limits. It contains resource numbers only when they come
  from a recorded staging measurement (task 1.3). Otherwise it contains the method and the chart defaults.
- **Comparison.** A new explanation page compares the deployment shapes (JAR, container image, Helm all-in-one and Helm split)
  by how each one is installed, upgraded and rolled back. It makes no throughput claims.
- **Navigation and checks.** The new pages are listed in `docs/gitbook/SUMMARY.md`, and `run-on-kubernetes.md` links to them.
  A Python test in `charts/ingot/tests` fails when a listed page is missing or when a page cites a chart value that
  `values.yaml` does not define.

Conventional Commits type and scope: `docs(deploy)`. The commit for the check is `test(deploy)`, as in the merged deploy-modes
change. Pull request title: `docs(deploy): add guides for keyless S3, gateway TLS and large uploads`.

### Out of scope

- The log line that names the S3 credential source. It changes code, so it is a `feat(storage)` change, `add-storage-credentials-log`.
- In-app TLS with the file names `tls.crt` and `tls.key` that cert-manager writes. The check in
  `reposilite-backend/src/main/kotlin/com/reposilite/web/application/JavalinConfiguration.kt:156` accepts only key paths that end
  in `.pem` or `.jks`, so cert-manager's default names fail with "Provided key extension is not supported." The fix is
  `fix(web)`, change `fix-tls-key-file-names`. The TLS guide documents the current behaviour until that change merges.
- A timeout field on the chart's HTTPRoute, and any body size or timeout value in the chart. This is `feat(chart)`, change
  `add-httproute-timeouts`. The edge guide documents the workaround.
- Secret references in repository settings (`_FILE` and similar). These belong to `add-file-secrets` and
  `add-settings-secret-references`. The GitOps guide only names them.
- Sizing numbers that have no staging measurement behind them.
- Any new setting, environment variable, default, manifest value or Java code path.

## Impact

- **Operators:** nothing changes. Defaults, settings, manifests and data stay as they are, and no operator must change anything.
  **BREAKING: no.** The guides describe the behaviour that already exists, and they say where a behaviour does not exist yet.
- **Code:** only the consistency test under `charts/ingot/tests` and Markdown under `docs/gitbook/`.
- **Dependencies on other changes:** none open. The guides use the chart and the deploy-target page from `add-deploy-modes`,
  which is merged. `add-httproute-timeouts`, `fix-tls-key-file-names` and `add-storage-credentials-log` are independent. When they
  merge, each one updates the guide it touches in its own change.
- **Staging:** task 1.3 reads Grafana and the staging pods. It does not change them.
