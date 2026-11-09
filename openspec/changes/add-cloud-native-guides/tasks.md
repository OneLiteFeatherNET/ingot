# Tasks

## 1. Verification before writing

- [ ] 1.1 Spike, no commit: on the staging cluster, run one test pod with a service account annotated for an IAM role and
  empty `accessKey` and `secretKey` in the S3 repository settings. Confirm that uploads and downloads succeed and that the
  server log names no static key. Record the result in the pull request description. Verify: the uploads succeed and the
  result is recorded. If they fail, the S3 section is rewritten to state the failure, and the proposal is updated first.
- [ ] 1.2 Spike, no commit: confirm with a read-only check of the staging chart values that `serviceAccount.annotations`
  reaches the rendered service account (`helm get manifest`). Verify: the annotation appears in the manifest.
- [ ] 1.3 Measurement, no commit: collect heap and memory figures for the staging server over one week with the read-only
  Grafana queries (JVM heap used and committed, container working set, request rate). Record the date, workload and query
  for each figure. Verify: the figures are in the pull request description. If they are missing, the sizing guide keeps the
  method and the chart defaults only, as the spec requires.

## 2. Test for the guides (test(deploy))

- [ ] 2.1 `test(deploy)`: write a failing test `charts/ingot/tests/test_guides.py` with `unittest`. It reads the repository files
  only, uses no network and no clock, and needs no temporary directory. Cases: every guide listed in this change is listed in
  `docs/gitbook/SUMMARY.md`; every YAML block in the new and updated guides that sets a chart key uses a key that
  `charts/ingot/values.yaml` defines (the test walks the parsed block and checks each path against the values file); and the
  test names the file in its failure message. Each case has one behaviour and one name. Verify: the test fails, because the
  guides do not exist yet, and the failure messages name the missing files.

## 3. Documentation (docs(deploy))

- [ ] 3.1 `docs(deploy)`: update `docs/gitbook/how-to/use-s3-storage.md` with a section "Credentials from the workload
  identity". It covers the IRSA service account annotation through `serviceAccount.annotations`, Pod Identity without an
  annotation, the rule that keys are used only when both are set, and a link to the storage providers reference. Verify:
  test 2.1 passes for this page.
- [ ] 3.2 `docs(deploy)`: add `docs/gitbook/how-to/terminate-tls-at-the-gateway.md`. It covers a cert-manager Certificate, the
  Gateway listener that references its Secret, the chart's HTTPRoute with `httpRoute.parentRefs`, and the ingress alternative
  from `run-on-kubernetes.md`. It has a section on in-app TLS that states the `.pem` and `.jks` rule and that cert-manager's
  default file names are not accepted until `fix-tls-key-file-names` merges. Verify: test 2.1 passes for this page.
- [ ] 3.3 `docs(deploy)`: add `docs/gitbook/how-to/configure-the-edge-for-large-uploads.md`. It covers the body size and
  timeout settings for nginx ingress (`nginx.ingress.kubernetes.io/proxy-body-size`, `proxy-read-timeout`,
  `proxy-send-timeout`) and the Gateway API timeouts of the implementation, states that the chart renders no HTTPRoute timeout
  field, and links to `run-behind-reverse-proxy.md` for the 413 case. Each annotation name is checked against the controller's
  documentation during the task. Verify: test 2.1 passes for this page.
- [ ] 3.4 `docs(deploy)`: add `docs/gitbook/how-to/manage-secrets-with-gitops.md`. It covers SOPS, External Secrets Operator and
  Sealed Secrets, each delivering a Secret that the chart reads through `env` and `envFrom`. It lists the values Ingot reads from
  the environment today, taken from `docs/gitbook/how-to/run-on-kubernetes.md` and the environment variables reference, and states
  that repository settings such as S3 keys are set through the API or the dashboard. Verify: test 2.1 passes for this page and the
  examples use the `envFrom` and `env` keys that `values.yaml` defines.
- [ ] 3.5 `docs(deploy)`: add `docs/gitbook/how-to/size-the-deployment.md`. It gives the sizing method (heap from the JVM metrics,
  memory limit from the working set, headroom for uploads). It contains a number only when task 1.3 recorded it with its date,
  workload and source. Otherwise it shows the chart defaults and says they are not measured. Verify: test 2.1 passes for this
  page and every number on it has a source line.
- [ ] 3.6 `docs(deploy)`: add `docs/gitbook/explanation/deployment-options-compared.md`. It compares the JAR, the container image,
  the Helm all-in-one chart and the Helm split chart by install, upgrade and rollback, and links to the matching how-to for each.
  It contains no throughput or latency figures. Verify: test 2.1 passes for this page and the page has no figure with a unit of
  requests or bytes per second.
- [ ] 3.7 `docs(deploy)`: list the six new pages in `docs/gitbook/SUMMARY.md` under the matching groups, and link them from
  `docs/gitbook/how-to/run-on-kubernetes.md`. Verify: test 2.1 passes, and the summary shows each new page once.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `docs(deploy): add guides for keyless S3, gateway TLS and large uploads`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description follows the operator's story (the first week on
  a cluster, the questions that had no answer, the answer that already existed in the code), names the follow-up changes
  `fix-tls-key-file-names`, `add-httproute-timeouts` and `add-storage-credentials-log`, and cites the staging results from tasks
  1.1 to 1.3. It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the description has no em or en dash.
