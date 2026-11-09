# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `configuration`. This change starts
only after `report-configuration-errors` has merged, from the remote main branch that contains it. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (none
is needed here), no fixed ports, no reads of the real environment (the source maps are passed in), no shared mutable state, a
`@TempDir` per test for every file, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`,
targeted and never the full suite. The container test runs through `heavy` as well.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose add-file-secrets: this change (proposal, design, capability spec, tasks) and nothing else.
  Verify: `openspec validate add-file-secrets --strict` passes and the dash grep from the proposal prints nothing.

## 2. File values (`test(configuration)` then `feat(configuration)`)

- [ ] 2.1 `test(configuration)`: write failing unit tests for the resolver as a pure function of the environment map, the property
  map and a file reader, one behaviour per test: a `_FILE` value is read; the `INGOT_` spelling beats the `REPOSILITE_` spelling; a
  trailing `\n` and `\r\n` are removed; inner and leading spaces are kept; an empty file gives the empty string; the plain variable
  wins over the file in the same source, the file is not read and one warning names both variables; a missing file is ignored when
  the plain variable is set; an environment file loses to a system property; `ingot.local.<key>.file` and
  `reposilite.local.<key>.file` are read; a `_FILE` variable of an unknown key is warned about and its file is not read; a missing
  file, a directory and invalid UTF-8 each throw `LocalConfigurationFileException`, whose message names the variable and the path
  and does not contain the file content. Each test uses its own `@TempDir`. Verify: they fail on the missing resolver and exception.
- [ ] 2.2 `test(configuration)`: write a failing container test in the existing containerTest source set, modelled on
  `DeployTargetContainerTest`: the image started with `INGOT_LOCAL_KEYPASSWORD_FILE` naming a missing file exits with status 1 and
  prints exactly one line that names the variable and no stack trace; the image started with `INGOT_LOCAL_PORT_FILE` naming a file
  with `8181` answers on port 8181. Verify: they fail on the current image. Run through `heavy`.
- [ ] 2.3 `feat(configuration)`: add the resolver to `LocalConfigurationFactory` and the `LocalConfigurationFileException`, feed
  the environment and property layers through it, pass the root journalist from `ReposiliteFactory` to the factory instead of
  `null`, and catch the exception in `main` of `ReposiliteLauncher` to print one line and exit with status 1. Verify: the tests of
  2.1 pass, the container tests of 2.2 pass through `heavy`, and the existing `LocalConfigurationFactoryTest` and
  `SearchConfigurationTest` pass with no assertion changed.

## 3. Documentation (`docs(configuration)`)

- [ ] 3.1 `docs(configuration)`: update `docs/gitbook/reference/environment-variables.md` (a row for the `_FILE` spelling and its
  fallback in the mapping rules, the file rules and the property form), `docs/gitbook/reference/configuration-and-startup.md`
  (the file spelling counts at the same step as its plain form in the order of precedence) and the variable table in
  `docs/gitbook/how-to/install-with-docker.md`. Verify: the dash grep prints nothing and every link in the changed pages resolves
  within `docs/gitbook/`.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `feat(configuration): read local settings from files with _FILE variables`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of an operator with a database
  or key password that should stay out of the manifest, and what happens when the file is missing. It carries no session link and
  no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every
  commit, and the body has no em or en dash.
