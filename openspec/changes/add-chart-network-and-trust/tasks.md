# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `chart`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no shared mutable state, a temporary directory per test (the helper writes its values file there), and assertions with
messages. Each test names one behaviour. Render tests run `helm template` and never touch a cluster or the network. Run them
with `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-chart-network-and-trust`: this change and nothing else. Verify:
  `openspec validate add-chart-network-and-trust --strict` passes and the dash grep from the proposal prints nothing.

## 2. Opt-in NetworkPolicy

- [ ] 2.1 `test(chart)`: write failing render tests in `charts/ingot/tests/test_network_policy.py`: default render has no
  NetworkPolicy; enabled renders one server policy whose pod selector is the server's selector labels; the ingress allows
  only the listed namespace selectors and the server port; split mode adds a dashboard policy and lets the dashboard pods
  reach the server; egress is absent from `policyTypes` when `networkPolicy.egress.enabled` is false; with egress enabled and
  `allowInternet: true` the public ranges are allowed and `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` are excepted; with
  `allowInternet: false` only DNS and `extra` rules remain; the schema refuses a string for `networkPolicy.enabled`.
  Verify: the tests fail, because no template exists.
- [ ] 2.2 `feat(chart)`: add `charts/ingot/templates/networkpolicy.yaml`, the `networkPolicy` block in `values.yaml` with
  comments, and the keys in `values.schema.json`. Use the helpers `ingot.selectorLabels` and `ingot.dashboardSelectorLabels`.
  Verify: the tests of 2.1 pass and `test_equivalence.py` passes unedited.
- [ ] 2.3 `docs(deploy)`: add a section "Restrict network traffic" to `docs/gitbook/how-to/run-on-kubernetes.md`, with the
  values, the default that renders nothing, the note that enforcement needs a CNI that enforces policies, and the kubelet
  probe note. Verify: the page links resolve and the dash grep prints nothing.

## 3. Extra CA bundle in the chart

- [ ] 3.1 `test(chart)`: write failing render tests in `charts/ingot/tests/test_extra_ca.py`: default render has no
  `INGOT_EXTRA_CA_CERTS` variable and no extra volume; `configMapName` mounts the ConfigMap read-only at the mount path and sets
  `INGOT_EXTRA_CA_CERTS` to that path; `secretName` mounts the Secret the same way; enabled with both or with neither source
  fails the render with a message naming `extraCaCerts`; the writable `emptyDir` is mounted at `/tmp` only when enabled; the
  dashboard Deployment gets none of it in split mode. Verify: they fail before 3.2.
- [ ] 3.2 `feat(chart)`: extend `charts/ingot/templates/deployment.yaml` with the volume, the mount, the `emptyDir` and the
  environment variable, guarded by `extraCaCerts.enabled`. The `env` block condition gains `or .Values.extraCaCerts.enabled`.
  Add the `extraCaCerts` block to `values.yaml` and `values.schema.json`. Verify: the tests of 3.1 pass and `test_equivalence.py`
  passes unedited.
- [ ] 3.3 `docs(deploy)`: add a section "Trust an internal certificate authority" to
  `docs/gitbook/how-to/run-on-kubernetes.md`. It names the ConfigMap or Secret keys, the read-only mount, the `/tmp` emptyDir,
  and says that the public CAs stay trusted. It states the image requirement and links `add-extra-ca-bundle`. Do not merge this
  commit before that image change is on main. Verify: the dash grep prints nothing.

## 4. Verification (no commit)

- [ ] 4.1 Run the chart suite and `helm lint charts/ingot` with the new values unset and set. Verify: both pass, and the
  rendered allInOne manifests of the staging fixture are byte-identical to the render of `origin/main`
  (`helm template` output compared with `diff`).
- [ ] 4.2 Create a kind cluster with Calico (enforcing CNI), one cluster at a time, through `HEAVY_SLOTS=1 heavy`. Install the
  chart in split mode with `networkPolicy.enabled=true`, a gateway-labelled namespace and a monitoring-labelled namespace in
  `ingress.fromNamespaces`. Verify: readiness on the server and dashboard stays green, the gateway namespace reaches the
  server port, a pod in an unlisted namespace is refused, and the monitoring namespace reaches `/metrics`. Delete the cluster
  afterwards.
- [ ] 4.3 After `add-extra-ca-bundle` is on main: on the same kind cluster, mount a Secret with a private CA and point an
  internal HTTPS test endpoint at it. Verify: the server starts with `extraCaCerts` set and trusts the endpoint, and it starts
  with the value unset as before (the endpoint is then refused, which proves the bundle is the cause).

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `feat(chart): add opt-in network policy and extra ca bundle`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (a shared cluster
  where any pod reached the repository, and an internal CA that the chart could not pass on) without template names. It carries
  no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts
  the title and every commit, and the body has no em or en dash.
