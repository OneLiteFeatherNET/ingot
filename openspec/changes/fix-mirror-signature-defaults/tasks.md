# Tasks

Each group is one or more commits of one Conventional Commits type each. Tests are written first and seen to fail before the
production code changes. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no fixed ports, no
shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through `heavy`, with
`--max-workers=2`, targeted and never the full suite. The remote host of a mirror test is an in-process fake, never a real
network.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-mirror-signature-defaults`: this change and nothing else. Verify:
  `openspec validate fix-mirror-signature-defaults --strict` passes and the dash grep from the proposal prints nothing.

## 2. Default extension list

- [ ] 2.1 `test(maven)`: write failing tests in the mirror test class next to the `.wsdl` case of `MavenFacadeTest`: the
  default `allowedExtensions` of a new `MirroredRepositorySettings` contains `.sigstore.json` and keeps `.jar`, `.pom`,
  `.sha512` and `.asc`; a mirror with the default list fetches `a-1.0.jar.sigstore.json` from the fake remote host and
  serves it; a mirror with the stored list `.jar`, `.pom` and `.asc` does not contact the fake host for the same file. Verify:
  the first two fail before 2.2, the third passes already and pins the stored-list rule.
- [ ] 2.2 `fix(maven)`: add `".sigstore.json"` after `".asc"` in the default `allowedExtensions` of `MirroredRepositorySettings`
  (`MavenSettings.kt`). Verify: the tests of 2.1 pass and the rest of `MavenFacadeTest` passes unedited.

## 3. Documentation

- [ ] 3.1 `docs(gitbook)`: add `.sigstore.json` to the default lists in `docs/gitbook/explanation/storage-and-mirror-caching.md`,
  `docs/gitbook/how-to/create-proxy-repositories.md`, `docs/gitbook/reference/repository-settings.md` and
  `docs/gitbook/reference/settings-domains.md`. Add a line to `docs/gitbook/reference/upgrade-changes.md` that a stored
  `allowedExtensions` list is kept, and that an operator adds `.sigstore.json` by hand. Verify: `git grep -n sigstore.json`
  finds the five pages, and the dash grep prints nothing.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `fix(maven): allow sigstore bundles in the default mirror extensions`.
  Read `.github/pull_request_template.md` and fill its sections. The description tells the story of the operator (a client
  that verifies Sigstore fails for a file the mirror never fetched) without class names. It carries no session link and no
  attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every
  commit, and the body has no em or en dash.
