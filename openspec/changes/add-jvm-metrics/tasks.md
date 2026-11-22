# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `prometheus`. Tests are written first and seen
to fail. Every test follows F.I.R.S.T.: environment and properties are injected, a fresh registry per test, no shared state. Gradle runs
go through `heavy` with `--max-workers=2`, targeted.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose `add-jvm-metrics`. Verify: `openspec validate add-jvm-metrics --strict --no-interactive` passes.

## 2. Switch (red, then green)

- [ ] 2.1 `test(prometheus)`: failing tests of the settings reading (default, each spelling, `false`, a typo) and of the registration with a
  fresh registry. Verify: they fail.
- [ ] 2.2 `feat(prometheus)`: the setting, the registry-taking registration and its use in `PrometheusPlugin`. Verify: the tests of 2.1 pass
  and the existing plugin tests pass unedited.

## 3. Documentation

- [ ] 3.1 `docs(prometheus)`: the families, the switch and its legacy spellings in the metrics reference. Verify: the page is linked from
  the configuration reference.

## 4. Open the code pull request

- [ ] 4.1 Open the code pull request `feat(prometheus): let an operator leave out the jvm metrics` against `main` with the body of
  `.github/PULL_REQUEST_TEMPLATE.md`. It has no file under `openspec/`, no session link and no co-author.
