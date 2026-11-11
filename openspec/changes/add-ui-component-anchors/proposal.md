# Proposal

## Why

An operator who runs a plugin with a dashboard page can ask the page to show a status or a button. Asking it to look
different, hide one control or place a note next to a table is a different request, and today it has no answer that
survives an upgrade. The dashboard is drawn from utility classes that change with every restyle, and the only stable
hooks are a handful of `data-testid` attributes in three files (`reposilite-frontend/src/components/dashboard/InstanceStatusPanel.vue:237`,
`:307`, `:345`, `:352`, and two test files). Nothing names the components of the dashboard in a way a plugin can rely on,
and nothing in the product says which names are promised.

This change gives every component of the dashboard a stable name in the DOM, `data-ingot-component`, and a stable name for
its named inner parts, `data-ingot-part`. The names are listed in one registry, they are checked by the test suite, and
a rename keeps the old name for one minor release. The attributes change nothing that a user sees and nothing that a
browser runs.

The attributes are the hook, not the feature. Today a plugin cannot ship a stylesheet or a script into the dashboard at
all: the dashboard policy and the design decided that in `add-plugin-ui-extension-point`, and the only other channels
(placeholders in the bundled `index.html` and `.js` files, `FrontendFacade.kt:50`, and the appearance settings,
`AppearanceSettings.kt:35`) cannot carry a rule. So for a plugin author the change makes the names available and documents
them, and a later change must add a way to deliver CSS before a plugin can use them. That gap is stated here and in
`design.md`, not hidden.

Facts checked against this worktree (main at `db0717b2b`) are in `design.md`, with `path:line` citations.

## What Changes

- **Component anchors.** Every `.vue` component under `reposilite-frontend/src/components/` except the decorative icons,
  `pages/IndexPage.vue` and `App.vue` carries a literal `data-ingot-component="<kebab-name>"` on the element that is its root.
- **Part and state attributes.** Named inner parts that a plugin would target carry `data-ingot-part="<part>"`. Where a
  part has a meaningful state it carries `data-ingot-state` with one value from a closed set (`current`, `loading`,
  `error`, `empty`, `open`).
- **Repository identity.** The repository row and the repository detail header carry `data-ingot-repository="<name>"`,
  the name already shown on screen. No other identity is put into an attribute in this change.
- **Registry.** `reposilite-frontend/src/anchors/registry.json` lists every component, its file, its parts, its states and
  its stability (`stable` or `experimental`). It carries a `contract` number. The registry is the source of the names.
- **Deprecation.** A renamed component keeps the old name as `data-ingot-component-alias` on the same element for one minor
  release, and the registry records the removal version.
- **Documentation.** A page generated from the registry, `docs/gitbook/reference/dashboard-anchors.md`, lists every name, and
  `docs/gitbook/how-to/extend-the-dashboard-from-a-plugin.md` explains what can be targeted today and what cannot.
- **Tests.** A vitest suite under `reposilite-frontend/src/anchors/` fails when a component lacks its attribute, a name is not
  kebab-case or not unique, a name is missing from or extra to the registry, a part or state is not in the registry, a
  dynamic binding of a `data-ingot-*` attribute is not on the allow-list, or the generated page is out of date. It runs in
  the existing `npm test` step of the frontend job (`.github/workflows/ci.yml:129`).

### Out of scope

- Insertion points and extension slots, where a plugin adds content to the dashboard. Follow-up change `add-ui-insertion-points`.
- A way for a plugin to ship a stylesheet into the dashboard. This is the named follow-up `add-plugin-stylesheet-channel`, which
  ships in the same proposal PR and depends on this change. It is not part of this change's code, so the stylesheet decisions
  (what a stylesheet may read, and where its rules sit in the cascade) are made there and not here.
- Any plugin script in the browser. `add-plugin-ui-extension-point` already rules it out for now.
- A theming tokens API, and any change to colours, the Material 3 components or the appearance settings.
- Renaming `data-testid`. Existing test ids stay as they are and are not replaced by the new attributes.
- Identity attributes for principals, teams, tokens and machine accounts. Only the repository name is in scope.
- Changing the `{{REPOSILITE.*}}` placeholders, the configuration or any environment variable. This change adds no key.

## Capabilities

### New Capabilities

- `ui-component-anchors`: the attribute scheme, the registry, the stability levels and deprecation, the rule for dynamic
  identity, and the checks that keep them in step with the source.

## Impact

- **Type and scope.** The change ships under `feat(frontend)`, the scope of `add-plugin-ui-extension-point` and of 34 of the
  last 200 commits on main (`ui` is used for 14, and the plugin extension point that this change serves uses `frontend`).
  Commits use `test(frontend)` for the suite, `feat(frontend)` for the attributes, the registry and the generator, and
  `docs(gitbook)` for the documentation pages, which is the scope the recent docs commits use. The pull request title, squash-merged onto main, is:

  `feat(frontend): add stable data-ingot anchors to every dashboard component`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, plugin or route
  changes. The attributes are additive DOM attributes. No existing `data-testid`, class, route or text is renamed. The title
  carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None for users. The bundle grows by the attribute strings (see `design.md`, Build size).
- **Both deploy modes.** The attributes are compiled into the dashboard build, so the bundled dashboard and the split image
  show the same names.
- **Dependencies on other changes.** None blocks implementation. `add-plugin-stylesheet-channel` depends on this change and
  is proposed in the same proposal PR, so the stylesheet channel is named here and not left to a later, unnamed change. See `design.md`, Coordination, for the in-flight navigation and
  settings changes that touch the same files, and for `add-plugin-ui-extension-point`, whose code is merged but whose change
  folder is still on main.
- **Code.** `reposilite-frontend/src/components/**`, `reposilite-frontend/src/pages/`, `reposilite-frontend/src/App.vue`,
  `reposilite-frontend/src/anchors/`, `reposilite-frontend/scripts/` (the generator), `docs/gitbook/`.

## Verification

- `npm test` in `reposilite-frontend` passes with the anchor suite, and the suite fails before the attributes exist (the red
  commit comes first).
- `npm run eslint` and `npm run build` pass. The bundle size before and after is recorded in the pull request description.
- `openspec validate add-ui-component-anchors --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-ui-component-anchors` prints nothing.
