# Tasks

Every group is one or more commits of one Conventional Commits type each. Scopes are `frontend` for code and `gitbook` for
the documentation pages. Tests are written first and seen to fail. Every test follows F.I.R.S.T.: the anchor suite reads source
files and runs no server, no clock and no network, each test builds its own parsed input, and assertions name the file and
the name that fail. Commands run from `reposilite-frontend` (`npm test`, `npm run eslint`, `npm run build`). Build and test runs
go through `heavy` when the machine is loaded, with the usual limits.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-ui-component-anchors`: this change and nothing else. Verify:
  `openspec validate add-ui-component-anchors --strict` passes and the dash grep from the proposal prints nothing.

## 2. Red suite (frontend)

- [ ] 2.1 `test(frontend)`: write `reposilite-frontend/src/anchors/anchors.test.js` with the static checks that need no registry
  content: a fixture template with a root `data-ingot-component="repositories-table"` passes; a template without it, a name in
  CamelCase, a duplicate name, a `data-ingot-state="busy"`, and a dynamic `data-ingot-principal` each fail with a message that names
  the problem. Verify: the fixture tests fail because the parser helper does not exist yet.
- [ ] 2.2 `test(frontend)`: add the source-wide checks, red against the current tree: every `.vue` under `src/components/` except
  `icons/`, plus `src/pages/` and `src/App.vue`, has a root `data-ingot-component` whose name is in the registry; the registry names
  equal the source names; the `pendingAreas` constant lists the six batch folders and is the only exemption. Verify: the suite fails
  and lists the first components without a name, which shows the 173 expected misses.
- [ ] 2.3 `test(frontend)`: add the dynamic-binding rule (only `data-ingot-repository` on the repository table row and the detail
  header) and the rule that no `src/` file other than the suite selects on `data-ingot-`. Verify: the rule fails on nothing yet and
  is shown to catch a seeded violation in a fixture.
- [ ] 2.4 `test(frontend)`: add the docs-sync check, which compares `docs/gitbook/reference/dashboard-anchors.md` with the output of
  `scripts/anchors-doc.mjs`, and the alias check. Verify: the check fails because neither file exists.

## 3. Registry and generator (frontend)

- [ ] 3.1 `feat(frontend)`: add `src/anchors/registry.json` with `contract` 1, the navigation batch components, and an empty
  `aliases` list, plus the parser helper that the red tests of group 2 call. Verify: the tests of 2.1 pass, and the source-wide check of 2.2
  fails only for the navigation components, which group 4.1 annotates.
- [ ] 3.2 `feat(frontend)`: add `scripts/anchors-doc.mjs` and the `anchors:doc` npm script, and generate the reference page from the
  registry. Verify: the docs-sync check of 2.4 passes for the navigation batch.

## 4. Batches (frontend)

Each batch adds the attributes, the parts and the states to its folders, and the registry entries for them, and removes its folders
from `pendingAreas`. Each batch is one commit and the suite passes after it. The last batch empties `pendingAreas`.

- [ ] 4.1 `feat(frontend)`: navigation batch (`layout`, `header`, `footer`, 9 components). Rebase on `restructure-navigation`,
  `add-repositories-admin-page` and, if merged, `fix-administration-navigation` and `rework-administration-layout`, and add the
  attributes to any component those change create.
- [ ] 4.2 `feat(frontend)`: browse batch (`browser`, `card`, `search`, `overview`, `repositories`, 26 components), including the
  repository row and the repository detail header with `data-ingot-repository`.
- [ ] 4.3 `feat(frontend)`: settings batch (`settings`, `renderers`, `plugins`, 36 components), including `plugin-page` and
  `plugin-block` with `data-ingot-block` from the closed block set. Rebase on `add-plugin-ui-extension-point` as merged code.
- [ ] 4.4 `feat(frontend)`: access batch (`access`, `identity`, `tokens`, `account`, 43 components). `FactoryResetModal.vue`
  (settings batch) also forwards attributes by hand, so its name is written inside its own template there.
- [ ] 4.5 `feat(frontend)`: dashboard batch (`dashboard`, `maintenance`, `audit`, `console`, 27 components).
- [ ] 4.6 `feat(frontend)`: support batch (`md`, `migration`, `util`, `pages/IndexPage.vue`, `App.vue`, 32 components). Empties
  `pendingAreas`, after which the suite covers all 173 components. The `md` wrappers take the attribute inside their own templates,
  because `MdTextField`, `MdSwitch`, `MdSelect`, `MdCheckbox` and `MdRadio` forward attributes by hand.

## 5. Build size and browser check (frontend)

- [ ] 5.1 Record `npm run build` before group 4 and after group 4.6: the gzip size of the largest JavaScript chunk, and the delta.
  The threshold is 2 KiB gzip. Record the numbers in the pull request description. A larger delta returns to the maintainer.
- [ ] 5.2 Render the bundled dashboard with the fake API (`npm run fake-api`, `npm run dev`) and check in a browser that the
  navigation, a repository row and a plugin page show the expected names and look as before. Stop the dev server afterwards.

## 6. Documentation (gitbook)

- [ ] 6.1 `docs(gitbook)`: add `docs/gitbook/how-to/extend-the-dashboard-from-a-plugin.md`, with what plugins can target today
  (declared pages, the placeholders, the appearance settings), what is missing (a stylesheet channel, `add-plugin-stylesheet-channel`, insertion points, plugin
  script), an example stylesheet labelled as one for a dashboard build the operator controls, and the selector rule of Decision 11.
  Link it from `docs/gitbook/reference/plugin-api.md` ("Dashboard pages") and `docs/gitbook/how-to/write-a-plugin.md`, and add it to
  `docs/gitbook/SUMMARY.md`.
- [ ] 6.2 `docs(gitbook)`: commit the generated `docs/gitbook/reference/dashboard-anchors.md` for the final registry and add it to
  `docs/gitbook/SUMMARY.md`. Verify: the docs-sync check passes.
- [ ] 6.3 `docs(gitbook)`: no configuration key or environment variable is added, so `configuration-and-startup.md` and
  `environment-variables.md` are not changed. Verify by grep that the change adds neither.

## 7. Pull request

- [ ] 7.1 Open the code pull request, with no file under `openspec/`, titled
  `feat(frontend): add stable data-ingot anchors to every dashboard component`. Fill the repository's pull request template. The
  description states the bundle size delta, the names of the follow-up changes, and that no plugin can yet ship a stylesheet. Do not
  tick tasks in this pull request.
