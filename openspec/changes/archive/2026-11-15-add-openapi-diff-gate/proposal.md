# Proposal

## Why

A team that builds against the Ingot API, with a script, a CI job or a generated client, relies on the endpoints staying where
they are. Today nothing guards that. A pull request can remove an endpoint, rename a path parameter, make an optional field
required or change a response type, and it merges green, because no check compares the API before and after. The people who find
out are the users, after the release, and the Reposilite-compatibility promise that Ingot rests on is only as good as the next
careless merge.

What is needed is a check on every pull request that says: this change removes or incompatibly changes part of the API, and the
pull request title does not carry the `!` marker that release-please turns into a major version and a changelog entry. The
maintainer gets a red check with the list of changes, and a contributor who really means to break the API adds the `!` and the
`BREAKING CHANGE:` footer and the check turns into a report.

## What Changes

- **A committed snapshot of the document.** `docs/openapi/ingot-openapi.json` holds the OpenAPI document as the server serves
  it, with the version field normalised so that it does not change on every release. A pull request that changes the API changes
  this file, so reviewers see the API diff in the diff.
- **A test that keeps the snapshot honest.** An integration test starts the in-process server, requests `/openapi` and compares
  it with the snapshot. When they differ it fails and tells the author to refresh the file with a documented Gradle property.
  The check in CI therefore compares two files and needs no second build of the base branch.
- **A new workflow `.github/workflows/openapi-diff.yml`.** On pull requests it reads the snapshot from the base branch and from
  the pull request, and runs `oasdiff breaking` (the maintained `oasdiff/oasdiff` project) through its GitHub action, pinned by the
  commit SHA with the tag in a comment, the same way the other workflows pin actions. It runs with `review: false` and an empty
  token, so no spec leaves CI and nothing is posted. It fails on error-level changes unless the pull request title has a `!`
  after the type or scope, in which case it only reports them. The title is read through an environment variable, never
  interpolated into a script.
- **Release artifact.** On a release, `release-please.yml` uploads the snapshot as `ingot-<version>-openapi.json` next to the jar,
  with the version field set to the release version.
- **Documentation.** A new reference section in `docs/gitbook/reference/http-api.md` on where to get the document and the
  compatibility rule, and a short contributor section on refreshing the snapshot and on the `!` marker.

### Out of scope

- Completing the document. That is `complete-openapi-spec`.
- Gating the Maven protocol, the dashboard routes or the plugin API. The gate covers the documented REST API only.
- Semantic versioning rules beyond what oasdiff reports, and per-endpoint stability or deprecation policy.
- Gating the Java and Kotlin plugin API. That is `gate-plugin-abi`.
- Hosting the document or a published API explorer.
- Signing the new release artifact. `sign-release-artifacts` decides how release files are signed; this change uploads the file
  the same way the jar is uploaded and follows that change if it lands first.
- Legal and business model questions.

## Capabilities

### New Capabilities

- `openapi-compatibility-gate`: the snapshot, its freshness test, the pull request check, the `!` rule and the release artifact.

## Impact

- **Type and scope.** The change ships under `ci(api)`. Its test, build and docs commits use the scope `api` as well. The pull
  request title, squash-merged onto main, is:

  `ci(api): fail pull requests that break the rest api without a marker`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin, and the
  runtime is untouched. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** Contributors only: a pull request that breaks the API now fails a check. Operators see
  nothing, apart from one more file on the release page.
- **Opt-in.** Not applicable; no new setting.
- **Dependencies on other changes.**
  - `complete-openapi-spec`: preferred before this change, so that the first snapshot is the complete document. Without it the
    gate works but guards a smaller document.
  - `harden-ci-supply-chain`: sets the pinning convention (action SHA plus tag comment). This change follows it and is
    independent of whether that change has merged.
  - `sign-release-artifacts`: touches the release upload step. Whichever lands second adapts the other's lines.
- **Required status check.** Making the new job a required check is a repository setting that the maintainer changes after the
  workflow has run once. It is not part of the diff.
- **Code.** `.github/workflows/`, `reposilite-backend` (one integration test and a Gradle property), `docs/openapi/`,
  `docs/gitbook/`.

## Verification

- The snapshot test runs through `heavy` with `--max-workers=2`.
- The workflow is exercised by three throwaway pull requests against a fork or branch: one that removes an endpoint (red), the
  same with `!` in the title (green and reported), and a docs-only change (green, skipped). The results are quoted in the pull
  request.
- `openspec validate add-openapi-diff-gate --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-openapi-diff-gate` prints nothing.
