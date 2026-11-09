# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `maven`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(any time source is injected as a `java.time.Clock`), no fixed ports (a fake proxy and a fake upstream bind to port 0), no shared
mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy` with
`--no-daemon --max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-mirror-integrity`: this change and nothing else. Verify:
  `openspec validate add-mirror-integrity --strict` passes and the dash grep from the proposal prints nothing.

## 2. Egress proxy characterisation (decides group 4)

- [ ] 2.1 `test(maven)`: characterise proxy selection of the mirror client. A loopback fake proxy records the requests it
  receives. With `HTTPS_PROXY` set and no `httpProxy`, the test asserts whether the request reached the fake proxy, and with
  `-Dhttps.proxyHost` set it asserts that the request does. Record the outcome in the code pull request description. Verify:
  the test runs without a network, and the outcome is written down before group 4 starts.

## 3. M1 Checksum policy

- [ ] 3.1 `test(maven)`: write failing tests, with `FakeRemoteClientProvider` and a temporary storage directory: `ignore` (and an
  absent key) stores without a sibling request; `warn` stores a mismatch and logs it (captured appender); `fail` with a matching
  `.sha256` stores; `fail` with a mismatch refuses the host and the next host's matching copy is stored; `fail` with `.sha256`
  missing uses `.sha1`; `fail` with neither file refuses; `fail` with every host mismatching gives `502` and no file in storage;
  metadata and checksum files are not checked; `store` disabled with `warn` or `fail` is refused at validation and the message
  names `checksumPolicy`; a settings file without the key loads with `ignore`. Verify: they fail before 3.2.
- [ ] 3.2 `feat(maven)`: add `checksumPolicy` to `MirroredRepositorySettings`, the sibling request and the digest check in
  `MirrorService`, and the commit of a verified copy under `fail`. Verify: the tests of 3.1 pass and the existing mirror tests
  (`MavenMirrorsIntegrationTest`, `NestedMirrorWaitIntegrationTest`) pass unedited.
- [ ] 3.3 `docs(maven)`: document `checksumPolicy` in `docs/gitbook/how-to/create-proxy-repositories.md` (the key table and a
  section on checksums, with the `store` requirement) and the verified copy in `docs/gitbook/explanation/storage-and-mirror-caching.md`.

## 4. M2 Egress proxy (only if group 2 shows the variables are ignored)

- [ ] 4.1 `test(maven)`: write failing tests for the proxy selection as a pure function of the switch, the scheme, the
  environment map and `NO_PROXY`: switch off gives no environment proxy; https takes `HTTPS_PROXY`; http takes `HTTP_PROXY`;
  `NO_PROXY` exact host, domain suffix with a leading dot, and `*`; a per-mirror `httpProxy` wins; a value with credentials is
  refused with a message that names the variable and no log line holds the address. The environment is passed in, never read in
  the test. Verify: they fail before 4.2.
- [ ] 4.2 `feat(maven)`: add `INGOT_HTTP_USE_ENV_PROXY` (`REPOSILITE_HTTP_USE_ENV_PROXY` as fallback, default `false`) and the
  selector in the mirror host configuration of `RepositoryFactory`. Verify: the tests of 4.1 pass and group 3 still passes.
- [ ] 4.3 `docs(deploy)`: add the switch to `docs/gitbook/reference/environment-variables.md`, and the egress and NetworkPolicy
  pairing to `docs/gitbook/how-to/run-on-kubernetes.md`. Verify: every named variable appears in the code.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `feat(maven): verify mirrored artifacts against upstream checksums`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (a bad copy
  that stayed in the cache and kept being served; the cluster that could not reach the upstream) without class names. It carries
  no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts
  the title and every commit, and the body has no em or en dash.
