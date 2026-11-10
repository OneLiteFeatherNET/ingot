# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `maven`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test,
and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted and never
the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-namespace-protection`: this change and nothing else. Verify:
  `openspec validate add-namespace-protection --strict` passes and the dash grep from the proposal prints nothing.

## 2. Settings and matching

- [ ] 2.1 `test(maven)`: write failing tests: a settings file without the key loads with an empty list; a prefix list on a `PROXY`,
  `HOSTED` or `MIXED` repository is refused and the message names `protectedGroupPrefixes`; blank, dotted-at-the-edge, slash and
  duplicate prefixes are refused; segment matching covers `net.onelitefeather` for `net/onelitefeather/ingot/1.0/a.jar` and
  `net/onelitefeather/maven-metadata.xml`, does not cover `net/onelitefeatherx/...` or `net/`, and is case sensitive. Verify: they
  fail before 2.2.
- [ ] 2.2 `feat(maven)`: add `protectedGroupPrefixes` to `RepositorySettings`, its validation, and a pure matcher. Verify: the tests
  of 2.1 pass and the existing settings tests pass unedited.

## 3. Hosted-only resolution

- [ ] 3.1 `test(maven)`: write failing tests with `FakeRemoteClientProvider` and a recording fake upstream: a protected coordinate
  held by a hosted member is served and the upstream gets no request; held by nobody gives 404 with the body of an ordinary miss and
  the upstream gets no request; a member group with a proxy at any depth is skipped; a cyclic member graph terminates; an
  unprotected coordinate still resolves from the proxy; an empty list changes nothing for the existing group tests; a settings
  change to the list takes effect without a restart. Verify: they fail before 3.2.
- [ ] 3.2 `feat(maven)`: classify members and apply the host filter in `ResolutionProvider.resolve`, supplied by `RepositoryService`
  for `findInputStream`, `findInputStreamRange` and `findDetails`. Verify: the tests of 3.1 pass.

## 4. Refusal audit and counter

- [ ] 4.1 `test(maven)`: write failing tests with a captured event bus: one refused request raises one `ProxyRefusedEvent`; a served
  request raises none; the audit plugin writes `NAMESPACE_PROXY_REFUSED` with `success` false, repository, path and details
  `prefix` and `skippedMembers`; an unresolved token is `anonymous`; the counter `ingot_namespace_refusals_total{repository}` moves
  once. Confirm first whether the request address can reach the event without changing the request path. Verify: they fail before 4.2.
- [ ] 4.2 `feat(maven)`: add the event, the audit action, the listener and the counter. Verify: the tests of 4.1 pass.

## 5. Mirror integrity events and metric

- [ ] 5.1 `test(maven)`: write failing tests with a fake remote and a captured event bus: `warn` mismatch stores and raises
  `MIRROR_CHECKSUM_MISMATCH` with outcome `stored`; `fail` mismatch refuses with outcome `refused`; a host without checksum files
  raises `MIRROR_CHECKSUM_MISSING`; `ignore`, metadata and checksum files raise nothing; the six `ingot_mirror_checksum_results_total`
  series exist at zero and move by policy and result. Verify: they fail before 5.2.
- [ ] 5.2 `feat(maven)`: raise the events from `MirrorService`, add the audit actions and the counters, and register the series in
  `OperationalMetrics`. Verify: the tests of 5.1 pass and the #118 tests pass unedited.

## 6. Operator documentation

- [ ] 6.1 `docs(maven)`: add the protection section to `docs/gitbook/how-to/create-a-group-repository.md` (the key, an example, hosted
  members directly in the group, the 404 and what it means, that direct proxy URLs are not covered); add the key to
  `docs/gitbook/reference/repository-settings.md`; add the three actions to `docs/gitbook/reference/audit-events.md`; add the two
  counters to `docs/gitbook/how-to/monitor-status-and-failures.md`; link the events from the checksum section of
  `docs/gitbook/how-to/create-proxy-repositories.md`. Verify: the keys and names match the code.

## 7. Pull request

- [ ] 7.1 Open the pull request with the title `feat(maven): refuse proxy members for protected group prefixes`. The description
  follows `.github/pull_request_template.md`, carries no session link, and states that the change is opt-in with no breaking change.
