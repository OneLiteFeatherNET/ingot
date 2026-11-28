# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Tests are written first and seen to
fail. Every test follows F.I.R.S.T. Heavy jobs run through `heavy`.

Prerequisite: `add-search-attribute-facets` code merged, and Q1 of the design settled.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose `add-search-facet-ui`. Verify: `openspec validate add-search-facet-ui --strict --no-interactive` passes.

## 2. Query and address (red, then green)

- [x] 2.1 `test(ui)`: failing tests of the `attribute` parameter round trip between address and request. Verify: they fail.
- [x] 2.2 `feat(ui)`: the parsing in `searchQuery.ts`. Verify: the tests of 2.1 pass.

## 3. Filter groups (red, then green)

- [x] 3.1 `test(ui)`: failing component tests for the groups, the zero-count chosen value and the refusal fallback. Verify: they fail.
- [x] 3.2 `feat(ui)`: the groups in `SearchFilters.vue` and the fallback in `SearchView.vue`. Verify: the tests of 3.1 pass and the existing
  search tests pass unedited.

## 4. Open the code pull request

- [x] 4.1 Open the code pull request `feat(ui): filter the search by plugin attributes` against `main` with the label `ci:e2e`, the body of
  `.github/PULL_REQUEST_TEMPLATE.md` and screenshots at 320, 600 and 1440 pixels. It has no file under `openspec/`, no session link and no
  co-author.
