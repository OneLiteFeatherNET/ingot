# Tasks

Each group is one or more commits of one Conventional Commits type each. Tests are written first and seen to fail before the production
code exists. Every test follows F.I.R.S.T.: no sleeps, no system clock, no shared mutable state, a temporary directory per test, and
assertions with messages. The parity tests run in process against in-memory repositories and tokens, with no network. Gradle runs go
through `heavy` with `--max-workers=2`, targeted.

Prerequisites: `add-plugin-api-artifact` (allowlist), `canonicalize-authorization-paths` (path rule, D3). Merge order with the authorizer
chain: this change delegates to the current rules (D2). The switch to the chain is a follow-up change of its own (D5).

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose `expose-plugin-access-checks`. This change and nothing else. Verify:
  `openspec validate expose-plugin-access-checks --strict --no-interactive` passes and the dash grep from the proposal prints nothing.

## 2. Parity matrix (red, then green)

- [x] 2.1 `test(maven)`: failing parity tests over the matrix of visibilities (PUBLIC, HIDDEN, PRIVATE) and viewers (anonymous, a token
  without rights, a token with the read right, a token with rights on another repository, a session), comparing each answer of the
  `Viewer` with the route function for the same input, and `listedRepositories()` with the list route (decision 70), including a `HIDDEN` repository the viewer can see but may not list. The test fails on the missing facade. Verify: they fail.
- [x] 2.2 `feat(maven)`: `ViewerAccessFacade` and `Viewer` in `net.onelitefeather.ingot.access`, delegating to `RepositorySecurityProvider`,
  `MavenFacade.listRepositories` (the source of `listedRepositories()`, decision 70) and `SearchAccessCompiler.compile`, all marked `@ExperimentalIngotPluginApi`. Add the classes to the
  allowlist of `add-plugin-api-artifact`. Verify: the tests of 2.1 pass.

## 3. Paths and no existence leak (red, then green)

- [x] 3.1 `test(maven)`: failing tests: `..`, empty segments, encoded slashes and dot segments give `false`; a canonical path gives the
  route's answer; an unknown repository, an unreadable repository and a missing one give the same `false`. Verify: they fail.
- [x] 3.2 `feat(maven)`: the canonical path check of `canonicalize-authorization-paths` applied in `canReadPath`, and the uniform `false`.
  Verify: the tests of 3.1 pass.

## 4. Surface guard (red, then green)

- [x] 4.1 `test(maven)`: failing test over the public members of `net.onelitefeather.ingot.access` and their types: no type of the
  authorization package, no token secret or hash, no public constructor of `Viewer`, and no `toString` that shows the token. Verify: it
  fails before the surface exists and passes after 2.2.
- [x] 4.2 `test(maven)`: an architecture test that the access package depends on no `authorization` type (extends the existing
  `DependencyRulesTest` pattern with the new package). Verify: it passes.

## 5. Search access value (red, then green)

- [x] 5.1 `test(maven)`: failing test that `searchAccess()` equals `SearchAccessCompiler.compile` for the same subject and visibilities, for
  each viewer of group 2. Verify: it fails.
- [x] 5.2 `feat(maven)`: the `searchAccess()` method, and the allowlist entries of `SearchAccess`, `RepositoryAccess` and the
  `PathPredicate` hierarchy, subject to the closure check of `add-plugin-api-artifact`. Verify: the test of 5.1 passes and the closure
  check passes.

## 6. Documentation (pull request of its own)

- [x] 6.1 `docs(maven)`: a page under `docs/gitbook/references/plugins/` describing the facade, the four answers, the path rule, the
  uniform `false` and the list of what stays internal. Verify: the page links the consumer fixture of `add-plugin-api-artifact`.

## 7. Pull requests

- [x] 7.1 Open the code pull requests in the order of groups 2 to 6, each from a fresh `origin/main` that contains the merged proposal and
  the prerequisites named above, each small and with no file under `openspec/`. The first one carries the title
  `feat(maven): expose viewer aware access checks to plugins`, and the documentation pull request is titled `docs(maven): describe viewer aware access checks for
  plugins`. Each pull request body fills `.github/PULL_REQUEST_TEMPLATE.md` and contains no session link.
