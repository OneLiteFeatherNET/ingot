# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first and seen to
fail. Every test follows F.I.R.S.T.: the property lookup is injected, no real network, no shared state. Gradle runs go through `heavy`
with `--max-workers=2`, targeted.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose `add-s3-path-style-setting`. Verify: `openspec validate add-s3-path-style-setting --strict
  --no-interactive` passes.

## 2. Resolution and field (red, then green)

- [ ] 2.1 `test(storage)`: failing tests of the precedence (set true, set false, unset with property, unset without) and of the settings
  round trip for an old file. Verify: they fail.
- [ ] 2.2 `feat(storage)`: the nullable field with its `@Doc`, the resolution function and its use in `S3StorageProviderFactory`. Verify: the
  tests of 2.1 pass and the existing S3 tests pass unedited.

## 3. Documentation

- [ ] 3.1 `docs(storage)`: describe the field and the property fallback in `references/repositories/storage-providers.md` and
  `repository-settings.md`. Verify: the pages name no new environment variable.

## 4. Open the code pull request

- [ ] 4.1 Open the code pull request `feat(storage): add a path style setting to the s3 storage` against `main` with the body of
  `.github/PULL_REQUEST_TEMPLATE.md`. It has no file under `openspec/`, no session link and no co-author.
