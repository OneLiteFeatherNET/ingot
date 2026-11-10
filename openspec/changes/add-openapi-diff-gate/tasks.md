# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `api`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (any
time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test, and
assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite. Integration
tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-openapi-diff-gate`: this change and nothing else. Verify:
  `openspec validate add-openapi-diff-gate --strict` passes and the dash grep from the proposal prints nothing.

## 2. Snapshot and freshness test

- [ ] 2.1 `test(api)`: write the failing integration test that requests `/openapi` from the in-process server, normalises
  `info.version`, and compares it with `docs/openapi/ingot-openapi.json`. Confirm here how the document is obtained (see the
  design) and record the answer. Verify: it fails because the snapshot does not exist.
- [ ] 2.2 `build(api)`: add a Gradle property that rewrites the snapshot from the served document, and commit the first
  snapshot. Verify: the test of 2.1 passes and a changed annotation makes it fail with the refresh command in the message.

## 3. Pull request check

- [ ] 3.1 `ci(api)`: read the Dockerfile of the diff action at the pinned commit and record whether its base image is pinned by
  digest. If not, pin it as the design describes. Verify: the result is in the pull request description.
- [ ] 3.2 `ci(api)`: add `.github/workflows/openapi-diff.yml`: triggers `opened`, `synchronize`, `reopened`, `edited` on pull
  requests to main; `permissions: contents: read`; checkout with the base ref fetched; skip when the base has no snapshot or
  the snapshot is unchanged; read the title through `env`; run `oasdiff breaking` with `fail-on: ERR` unless the title matches
  the `!` pattern, then report only; `review: false`, `github-token: ''`. Verify: `actionlint` is clean and every `uses:` is a
  SHA with a tag comment.
- [ ] 3.3 `ci(api)`: add `docs/openapi/.oasdiff.yaml` only if a rule needs adjusting, with a comment per entry. Verify: the three
  throwaway pull requests of the proposal give red, green with report, and skipped.

## 4. Release artifact

- [ ] 4.1 `ci(api)`: in `release-please.yml`, upload the snapshot as `ingot-<version>-openapi.json` after the jar, with
  `info.version` set to the release version, using the same `gh release upload ... --clobber` pattern as the jar. If
  `sign-release-artifacts` has merged, sign the file the way that change signs the jar. Verify: a dry run on a branch lists the
  file name and the version inside it.

## 5. Documentation

- [ ] 5.1 `docs(api)`: add to `docs/gitbook/reference/http-api.md` where to get the document (`/openapi`, the release asset) and
  the compatibility rule, and add a contributor section on refreshing the snapshot and on the `!` marker. No configuration key or
  environment variable is added, so no other page changes.

## 6. Pull request

- [ ] 6.1 Open the pull request with the title `ci(api): fail pull requests that break the rest api without a marker`. The
  description follows `.github/pull_request_template.md`, carries no session link, and states that there is no breaking change.
