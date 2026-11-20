# Tasks

Priority: nice to have, after the must-have changes. Nothing is implemented until the maintainers pick it up. Each group is one or
more commits of one Conventional Commits type each. Tests are written first and seen to fail before production code exists, and
follow F.I.R.S.T.: no sleeps, injected `Clock`, a temporary directory per test, no shared state. Gradle runs go through `heavy` with
`--max-workers=2`, targeted.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: this proposal, together with `add-minecraft-plugin-portal-plugin`, in one pull request.

## 2. Core prerequisites (each its own proposal and code pull request, each additive)

- [x] 2.1 `add-plugin-api-artifact`: `feat(plugin): publish a slim plugin api artifact` (shared with the Minecraft portal).
- [ ] 2.2 `add-artifact-enrichment-hook`: `feat(search): let plugins enrich artifacts when they are indexed` (shared).
- [ ] 2.3 `add-search-document-attributes`: `feat(search): store plugin attributes with indexed artifacts` (shared).
- [x] 2.4 `expose-plugin-access-checks`: `feat(maven): expose viewer aware access checks to plugins`.
- [ ] 2.5 `add-plugin-reader-pages`: `feat(frontend): let plugins contribute pages for readers`.
- [ ] 2.6 `add-plugin-ui-browse-blocks`: `feat(frontend): add list, code and filter blocks to plugin pages`.
- [ ] 2.7 `extend-plugin-abi-gate`: `ci(plugin): cover the ingot plugin api packages in the abi gate` (shared).

## 3. Plugin repository

- [ ] 3.1 `build`: create `OneLiteFeatherNET/ingot-plugin-gradle-portal` with Apache 2.0, DCO check, Conventional Commits check,
  Renovate, and a build that depends only on the published plugin API artifact.
- [ ] 3.2 `feat(gradle-portal)`: marker and descriptor detection with a test for each scenario of the spec, including the damaged jar.
- [ ] 3.3 `feat(gradle-portal)`: rights-aware listing and detail routes, with tests for private, hidden and public repositories and
  for the anonymous viewer.
- [ ] 3.4 `feat(gradle-portal)`: snippet generation, with tests that no secret appears and that ids are validated.
- [ ] 3.5 `feat(gradle-portal)`: dashboard pages.
- [ ] 3.6 `ci`: clean-checkout build, smoke test against the current Ingot image, signed release job for `publish-plugin-catalogue`.
- [ ] 3.7 `docs`: README with the install steps from the plugin store, the proxy how-to of D6 and the list of API gaps that remain.

## 4. Pull requests

- [x] 4.1 Open the proposal pull request under the title `docs(openspec): propose add-gradle-plugin-portal-plugin and
  add-minecraft-plugin-portal-plugin`, from a branch of the remote main branch, with only the two change directories.
- [ ] 4.2 Open each core prerequisite pull request under the title given in group 2, from a branch of the remote main branch that
  contains the merged proposal, with no file under `openspec/`.
- [ ] 4.3 Open the first pull request of the plugin repository under the title `feat(gradle-portal): list gradle plugins with
  resolution snippets`.
