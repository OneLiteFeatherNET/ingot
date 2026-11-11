# Design

## Starting point, verified on main

Counts and hooks, from this worktree:

- **Components.** `reposilite-frontend/src` holds 206 `.vue` files: 204 under `src/components/`, `pages/IndexPage.vue` and
  `App.vue`. Of the 204, 33 are decorative icons (`components/icons/`), 15 are the `Md*` wrappers over Material 3
  (`components/md/`), and 156 are feature components across 19 folders. The largest folders are `access` (30), `settings` (18),
  `dashboard` (14), `migration` (12), `maintenance` (10). The file basenames of the 171 non-icon components are unique, so a
  kebab-case basename is a valid default name.
- **Existing hooks.** 18 `data-testid` occurrences in 3 files: `InstanceStatusPanel.vue:237, 307, 345, 352`, and 14 in
  `InstanceStatusPanel.test.js` and `FileBrowserView.test.js:71, 433`. No `data-ingot` string exists in `reposilite-frontend`,
  `docs/`, `openspec/` or `reposilite-backend/src`. So there is no convention to extend, and this change sets the first one.
- **Directives.** None is registered (`grep 'directive(' reposilite-frontend/src` is empty). `main.js` registers the router,
  head and axios plugins only.
- **Attribute fallthrough.** Six components set `inheritAttrs: false` and forward attributes by hand: `md/MdTextField.vue:33,
  163-164`, `md/MdSwitch.vue:33`, `md/MdSelect.vue:41`, `md/MdCheckbox.vue:33`, `md/MdRadio.vue:50`,
  `settings/FactoryResetModal.vue:31, 39`. A `data-ingot-*` attribute that a parent passes to them lands on an inner element,
  or nowhere. The attribute therefore has to be written inside the component's own template.
- **Roots.** An ad hoc count of the top-level template nodes (a scratch script, not added to the repository) gives 194
  single-root components, 10 with several roots (`card/ArtifactSnippet.vue`, `card/CodeBrackets.vue`,
  `card/RepositorySnippet.vue`, `card/XmlTag.vue`, `dashboard/InstanceStatusPanel.vue`, `overview/RepositoryOverview.vue`,
  `plugins/PluginBlock.vue`, `plugins/PluginPageView.vue`, `repositories/RepositoryDetail.vue`, `settings/EntryDetail.vue`),
  and one whose root is a `<Teleport>` (`migration/MigrationDialog.vue:79`). The count includes `v-if` and `v-else`
  siblings as roots, so it is an upper bound on the multi-root cases.
- **Teleports and dialogs.** Three components use `<Teleport>` directly (`access/PrincipalActionsMenu.vue:220`,
  `migration/MigrationDialog.vue:79`, `md/MdSelect.vue:398`). Twenty-six files reference `vue-final-modal` or a `<Modal>`
  for dialogs; that library renders outside the component tree, so the name goes on the dialog's own content element.
- **Plugin UI.** PR #139 is merged (`b2da66a4c`). The code is `reposilite-backend/src/main/kotlin/com/reposilite/frontend/api/PluginUiFacade.kt`,
  `.../infrastructure/PluginUiEndpoint.kt:41` (`/api/frontend/plugin-ui`), `.../infrastructure/PluginUiDataGuard.kt:35`, and in the
  dashboard `src/components/plugins/PluginBlock.vue`, `PluginPageView.vue`, `src/store/pluginUi.js` and
  `src/components/plugins/pluginBlocks.js:23` (`BLOCK_TYPES`). The change folder `openspec/changes/add-plugin-ui-extension-point/`
  is still on main and not archived, so its tasks show as open although the code is in. That is a stale change, and archiving it
  is the maintainer's call. This change builds on the code, not on the folder.
- **Tests.** `reposilite-frontend/vitest.config.js:26` includes `src/**/*.test.js`. The frontend job runs `npm run eslint` and
  `npm test` (`.github/workflows/ci.yml:125, 129`). Dev dependencies include `@vue/compiler-sfc`, `@vue/test-utils` and `jsdom`.
  There is no Playwright or other browser suite on main.
- **Navigation.** `reposilite-frontend/src/navigation/registry.js:205` holds the static `PAGES` list, and `settingsGroupOf` (line 324)
  places settings domains.

## Decision 1: the attribute is written in the template, not added at runtime

Default: a literal attribute in each component's template.

```vue
<template>
  <section data-ingot-component="repositories-table">
    <header data-ingot-part="header">...</header>
```

Alternatives considered:

- **A directive `v-ingot="'repositories-table'"`.** It works on every element, but it runs on mount, so a test cannot see the
  name without mounting every component, and the name is not in the markup a reviewer reads. Vue compiles a static literal
  into the template and hoists it, so the literal costs nothing at run time, while a directive costs a function call per mount.
- **A component wrapper.** It adds a component level to every tree and breaks the multi-root and teleport cases above.
- **`inheritAttrs` with a fixed name.** It cannot carry a per-component name, and it fails for the six components that forward
  attributes by hand.

The directive stays available if a case appears that a literal cannot express. None is known today.

## Decision 2: one key with a value, not one attribute per component

Default: `data-ingot-component="<name>"`, with `data-ingot-part` and `data-ingot-state` as the other two keys.

One attribute per component (`data-ingot-repositories-table`) would also select in CSS, but it has three costs. A plugin cannot
list every anchor with a single selector (`[data-ingot-component]` does). The names are then attribute names, which the HTML
parser lowercases and the registry cannot check as values. A rename of a per-component attribute also removes the old
selector outright, while with one key the old value can travel in an alias attribute (Decision 7). One key is also what the
test asserts on: the presence of one key and a value from the registry.

## Decision 3: the names

Default name of a component: the kebab-case of its file basename, unless the registry says otherwise. Examples in the
current tree: `RepositoriesTable.vue` becomes `repositories-table`, `SideNavLayout.vue` becomes `side-nav-layout` (not
`side-nav`), `MdTextField.vue` becomes `md-text-field`, `PluginBlock.vue` becomes `plugin-block`, `App.vue` becomes `app-shell`
(the registry name, because `app` alone is too generic), and `pages/IndexPage.vue` becomes `index-page`.

The registry is the source. The test does not derive names from file names, so a rename is a deliberate edit of the registry,
with an alias.

Parts are named by the role they play for a reader, not by the element (`header`, `actions`, `row`, `cell-name`, `empty`,
`footer`). A part is listed in the registry for its component, and the test rejects a part that the registry does not list.
Parts are chosen per component in the batches (Decision 9), so the number of parts is known only when the batches are done.

States are a closed set in the registry schema: `current` (nav item, tab), `loading`, `error`, `empty`, `open` (dialog,
menu, disclosure). A component states only the ones it can be in.

## Decision 4: dynamic identity, and its privacy rule

Default: one dynamic attribute, `data-ingot-repository="<name>"`, on the repository row (`repositories/RepositoriesTable.vue`) and
on the repository detail header (`repositories/RepositoryDetail.vue`). The value is the repository name, which is already the
first column and the route of the page.

The rule that governs any dynamic `data-ingot-*` value, which the test enforces by listing the allowed bindings:

- Only a value that is already visible as text in the same element or the same row. The attribute repeats the screen; it does not
  add to it.
- Never a secret, token, token id, password, hash, session or CSRF value, key, or an address, e-mail or other personal data.
- Vue escapes attribute bindings, so a repository name cannot break out of the attribute. The rule above is about what may be
  exposed, not about injection.

Principals, teams, tokens and machine accounts get no dynamic attribute in this change, because their identifiers are closer to
credentials than repository names and a decision about them belongs to the identity model (open question Q4).

## Decision 5: plugin pages

Plugin pages are drawn by `components/plugins/PluginPageView.vue` and `PluginBlock.vue`. Their content is plugin data. The
attributes on them are fixed: `data-ingot-component="plugin-page"` and `data-ingot-component="plugin-block"`, with
`data-ingot-block="<type>"` taken from `BLOCK_TYPES` (`pluginBlocks.js:23`), a closed set the dashboard already checks. A plugin
page id is exposed as `data-ingot-plugin-page="<id>"` only after the same validation the server applies, `[a-z0-9-]{1,32}`
(`PluginUiValidator.kt:27`). The plugin name is not exposed (open question Q5).

## Decision 6: registry, generated page and checks

`reposilite-frontend/src/anchors/registry.json`:

```json
{
  "contract": 1,
  "components": [
    {
      "name": "repositories-table",
      "file": "src/components/repositories/RepositoriesTable.vue",
      "stability": "stable",
      "parts": ["header", "actions", "row", "cell-name", "empty"],
      "states": ["loading", "empty"]
    }
  ],
  "aliases": [
    { "name": "old-name", "replacedBy": "new-name", "removeIn": "1.x.0" }
  ]
}
```

The field names are the schema. `contract` is 1 in this change. Its meaning: a change that removes or renames a name, or
changes the meaning of a value, raises it, and only the maintainer raises it.

The checks, all in `reposilite-frontend/src/anchors/anchors.test.js` (static, no mounting):

1. The set of components found in the source equals the set in the registry, after the excluded icons.
2. Each name is kebab-case (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`), unique, and used once per file.
3. Each file's template carries `data-ingot-component="<name>"` on a root element, or on the first element inside a root
   `<Teleport>`.
4. Every `data-ingot-part` and `data-ingot-state` value in a file is in the registry for that component.
5. The only dynamic `data-ingot-*` bindings are the allow-list of Decision 4 and Decision 5.
6. The docs page `docs/gitbook/reference/dashboard-anchors.md` equals the page generated from the registry.
7. Every alias names a removed or renamed component, and its `removeIn` version is not reached.

The test parses each file with `@vue/compiler-sfc`, which is already a dev dependency. It reads files and runs no server, no
clock and no network, so it is fast and repeatable. The mounted smoke checks (a sample of components rendered with
`@vue/test-utils`) are added only for behaviour the static check cannot see: the teleported dialog and the multi-root cases.

The generator is `reposilite-frontend/scripts/anchors-doc.mjs`. It writes the reference page from the registry and runs as
`npm run anchors:doc`. The test compares the file and fails with the command to run.

## Decision 7: stability and deprecation

- `stable`: the name is part of the contract. A name is added in a minor release. It is removed or renamed only through an
  alias for one minor release, with `removeIn` in the registry and the change in the release notes.
- `experimental`: the name may change in any release. The reference page marks it. A component is experimental until its batch
  says otherwise, and new components may start experimental.

An alias keeps the old name on the same element for one minor release:

```html
<section data-ingot-component="repositories-table" data-ingot-component-alias="repository-list">
```

The alias attribute is present only while a registry alias entry exists, and the test removes the expectation at `removeIn`.
This is the rule the maintainer asked for: a renamed component keeps the old name for one minor release, documented.

## Decision 8: the plugin ABI gate does not cover this

`gate-plugin-abi` checks JVM bytes. These names are a JavaScript DOM contract, so the gate is the wrong tool. The frontend suite
of Decision 6 is the gate for them. The dashboard's version is not the plugin ABI version and the two are not linked.

## Decision 9: batches

Each batch is one `feat(frontend)` commit of attributes, parts and registry entries for one area, and it keeps the suite green.
The suite holds a constant `pendingAreas` of folders that are not yet annotated, and it skips those folders only. The last batch
empties the list, so at the end the suite covers every component. A batch that leaves an area out of `pendingAreas` must cover
all its components. The suite cannot pass with a half-finished area.

| Batch | Folders | Components |
| --- | --- | --- |
| navigation | `layout`, `header`, `footer` | 9 |
| browse | `browser`, `card`, `search`, `overview`, `repositories` | 26 |
| settings | `settings`, `renderers`, `plugins` | 36 |
| access | `access`, `identity`, `tokens`, `account` | 43 |
| dashboard | `dashboard`, `maintenance`, `audit`, `console` | 27 |
| support | `md`, `migration`, `util`, `pages`, `App.vue` | 32 |

Total: 173 components (171 non-icon components, `pages/IndexPage.vue` and `App.vue`). Parts are counted in the batches.

Batch order, and why: navigation first, because every page is inside its shell. `support` last, because `md` is used by every batch
and its attributes are the most reused.

## Decision 10: the attributes do not change behaviour or the policy

- No attribute is read by any code. `data-ingot-*` is not a selector in the source, and the test rejects `querySelector` on it
  in `src/` outside the anchor suite.
- No style, class, event handler or `id` changes. The new attributes are added next to existing ones.
- The dashboard policy (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/web/SecurityHeaders.kt:72`) and the split image
  policy (`reposilite-frontend/server/lib/security-headers.js:27`) are not touched. A static attribute is not a source of script
  or style, and the names are constants.
- The attributes ship in the production build. The reason is that a plugin author runs against the production dashboard, and a
  build without the names would hide the thing being promised.

## Security

- **Injection.** The attribute names and the closed values are written in the source. Dynamic values pass through Vue's attribute
  binding, which escapes them. Plugin text never becomes an attribute name, and plugin page ids are validated before they reach
  an attribute.
- **What a stylesheet could read.** This change does not add a stylesheet channel, but the anchors are what a later channel
  would select on, and that channel needs a decision. Its CSS can read attribute values and input values through selectors and
  send them out through `url()`, because `img-src` allows `https:` (`SecurityHeaders.kt:72`). The follow-up must therefore either
  keep the stylesheet away from data-bearing elements or drop the rule, and it has to say which. This is recorded as a blocker of
  that follow-up, not of this change.
- **Information.** Only values already on screen are exposed (Decision 4). The attributes add no new data to any response.

## What plugins can use today, and what is missing

What exists:

1. **Declared pages.** A plugin declares a page with the `plugin-ui` facade (`docs/gitbook/reference/plugin-api.md`, "Dashboard
   pages"). The text, tables, forms, actions and status blocks are drawn by the dashboard's own components. Plugins can choose
   words and order, not look.
2. **Placeholders.** `FrontendFacade.registerPlaceholder` (`FrontendFacade.kt:50`) substitutes a value into the bundled `index.html`
   and `.js` assets (`FrontendHandler.kt:64`, `FrontendFacade.kt:136`). The only user is the Javadoc plugin
   (`javadocs/application/JavadocPlugin.kt:50`). A placeholder cannot add a stylesheet to the dashboard, because the bundled
   `index.html` has no slot for one.
3. **Appearance settings.** Colours, logos, the favicon and the footer text are configuration (`AppearanceSettings.kt:35-75`), not
   CSS.
4. **Static files.** The `static/` directory (`FrontendPlugin.kt:54`) is served at its own paths, and it replaces the dashboard
   root only when the bundled dashboard is turned off. It is not loaded into the dashboard.

What is missing:

- A way to deliver a stylesheet into the bundled dashboard. Without it, the attributes are reachable only by an operator who
  builds a dashboard of their own. Follow-up `add-plugin-stylesheet-channel`.
- A way to add content at a named place. Follow-up `add-ui-insertion-points`.
- Any plugin script in the browser. Not planned (see `add-plugin-ui-extension-point`, `design.md`).

So the documentation page `extend-the-dashboard-from-a-plugin.md` says what can be targeted now, and labels the example stylesheet as
one for a dashboard build the operator controls. It does not claim that a plugin can load it.

## Build size

Not measured in this proposal, because no build was run. The estimate: about 1,200 attribute literals (173 root attributes plus
the parts and states), each 25 to 60 bytes, so roughly 40 KB uncompressed in the render code and a few kilobytes after gzip, with
the text repeating well. The implementation records the real numbers: `npm run build` before the first commit and after the last,
the gzip size of the largest JavaScript chunk, and the delta. The threshold is 2 KiB gzip for the JavaScript. A larger delta
returns to the maintainer before merge.

## Coordination with in-flight changes

- `restructure-navigation` (on main) and `add-repositories-admin-page` (on main) touch `navigation/registry.js`, the navigation
  and settings folders. The navigation and settings batches are rebased on them.
- `fix-administration-navigation` and `rework-administration-layout` exist only on the remote branch
  `origin/docs/openspec-fix-administration-navigation` (their proposals are not on main). They change the same navigation and
  administration files. Whichever lands later adds the attributes to the components it creates. The suite then fails on the
  missing attribute, which is the enforcement, so nobody has to remember.
- `fix-browse-overview` and `fix-dashboard-status` do not exist on main or in any branch of this clone. If they land, the same rule
  applies to the components they add. Their status is an open item for the maintainer.
- `add-dashboard-e2e-tests` exists only on `origin/docs/openspec-add-dashboard-e2e-tests`. Its design picks Playwright
  (its `design.md`, Decision 1) and says nothing about selectors. The brief asked for "roles first, attributes second" in that
  change. That rule is not in its text. This change records the rule in `dashboard-anchors.md` (Decision 11) and the e2e
  change is to adopt it when it lands.

## Decision 11: selectors for tests

Default: end-to-end and component tests select by role and accessible name first, and by `data-ingot-*` second, and by
`data-testid` only where neither works. The anchors are stable across restyles, which makes them good for plugins and for
structural checks, but a role is what a user perceives, so a test that breaks because a role changed has found a real defect. The
rule is written in the reference page. The attributes do not replace `data-testid`, which existing tests use.

## Open questions (each has a default; none blocks implementation)

- **Q1. Icons.** Default: excluded (33 files, decorative SVG, no behaviour). A plugin that wants to hide an icon can target its
  parent component. Change this only if the maintainer wants every SVG named.
- **Q2. Directive or literal.** Default: literal (Decision 1). Change only if a case appears that a literal cannot express.
- **Q3. Alias attribute name.** Default: `data-ingot-component-alias`, present for one minor release.
- **Q4. Identity attributes beyond repositories.** Default: none for principals, teams, tokens and machine accounts. A decision in
  the identity model is needed first.
- **Q5. Plugin name on plugin pages.** Default: not exposed. Only the validated page id is.
- **Q6. Example stylesheet in the docs.** Default: the example is labelled as one for a dashboard build the operator controls, because
  no plugin can load it today.
- **Q7. Bundle budget.** Default: 2 KiB gzip for the JavaScript. The implementation measures it and returns to the maintainer above it.
- **Q8. Stability of the first names.** Default: `stable` for the components of the navigation and settings batches, `experimental`
  for the others until a release has shipped with them.

## Alternatives not taken

- **Per-component attribute names.** Rejected, see Decision 2.
- **Generated names at build time from file paths.** Rejected: a rename of a file would silently rename the contract, and the
  registry must stay a reviewed document.
- **A runtime registry in the dashboard.** Rejected: the names are needed in the source and in the docs, and a runtime list would
  drift from the DOM.
