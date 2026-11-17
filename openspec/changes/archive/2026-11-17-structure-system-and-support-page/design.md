# Design

The design refers to the files of `reposilite-frontend/src` on `main`. The change builds on `add-support-bundle` (the endpoints and the
redaction, archived) and `ui-component-anchors` (the registry, the parts, the states and the alias rule). It does not depend on
`rework-admin-header-and-repository-tabs`, which is not merged; that change reuses the tabs component built here (D3).

## D1. What is on the page today

`SystemInfoView.vue` (about 310 lines) reads `client.support.systemInfo()` once on mount and draws ten sections in one column:
Ingot, Java (with the input arguments), Operating system, Database, Storage, Cluster, Plugins (with the tier label of each
plugin, `data-ingot-part="plugin-tier"`), Environment, System properties and Configuration (Local as a list, Shared as a
JSON block). Its root is `data-ingot-component="system-info-view"`, and it has one state, `loading`. Each section heading is
an `h2` with an id `support-<section>`, which is the anchor contract of the current page.

`SupportView.vue` (110 lines) reads `client.support.availability()` on mount and draws the zip download. Its states are
`off` ("The support pages are turned off on this instance."), `failed` ("The support bundle cannot be checked right now.") and
`ready`, which shows the description, the section "What is removed" with the masking note, and the button "Download support
bundle". Its root is `data-ingot-component="support-view"`.

Two requests are made today, one per page. This change keeps both requests: no endpoint changes, and the existing availability
check still decides whether the page offers the download.

**Copy action.** Neither page has a copy-for-support action. The only support action is the download of the zip. This change does
not add one (D11).

## D2. One page, one navigation entry

The two entries of the Support group in `src/navigation/registry.ts` become one: `key: 'system-info'`, `route: 'system-info'`,
`label: 'System and support'`. The description is the one of today's system information entry, extended with the words of the
support entry: "What this instance runs on and what a maintainer needs to look at. Secrets are withheld or masked, and the page
only reads." The keywords are the union of both, `version, java, environment, zip, debug`. The entry keeps its permission
(`ADMIN_NODES.console`) and its `requires` (`supportBundleOffered`). The `support` entry is removed.

The label is "System and support". It names both things the page holds and fits the group heading "Monitoring and support".

## D3. A general accessible tabs component

This change builds the tabs component that the page uses. It is general: it is placed in `src/components/tabs/` and knows nothing
about system information, so that other pages can reuse it. `rework-admin-header-and-repository-tabs` task 4.6 is planned to reuse it
for the repository page, in place of the primitive it names today (`migrate-content-to-components`); that change is not edited here.

**API.** The component takes:

- `tabs`: a list of `{ key, label }`, in order;
- `modelValue`: the key of the selected tab; the component emits `update:modelValue` when a tab is chosen;
- `queryParam` (default `tab`): the query parameter that holds the selected key; when it is set, the selected tab is read from and
  written to the address, and when it is not, the component is only controlled by `modelValue`;
- `label`: the accessible name of the tab list (`aria-label`), required.

The default slot receives the key of each tab and renders the panel of that tab. The page passes the content of each tab through named
slots keyed by the tab key, or through one slot called once per tab.

**Roles and ids.** The list has `role="tablist"` and `aria-label`. Each tab is a `button` with `role="tab"`, `aria-selected`,
`aria-controls` of its panel and an id. Each panel has `role="tabpanel"`, `aria-labelledby` of its tab, the id of the panel, and
`tabindex="0"` so the focus can enter it. Ids are made with Vue's `useId` (the project uses Vue 3.5), so two instances on one page do not
collide.

**Selection and focus.** Only the selected tab is in the tab order (roving `tabindex`: 0 on the selected tab, -1 on the others). Right
and Left move the focus to the next and the previous tab, and wrap at the ends. Home and End move to the first and the last tab. The
arrow keys and Home and End both select the tab they move to (automatic activation). Tab leaves the list and enters the selected panel.
Panels that are not selected are drawn with the `hidden` attribute, so their content keeps its state.

**Address.** When `queryParam` is set, the selected key is the query parameter of the current route. A missing or unknown value selects the
first tab. Selecting a tab replaces the history entry instead of adding one. A change of other query parameters does not change the selected
tab.

**What it does not do.** It does not read or write the fragment of the address. A page that maps a fragment to a tab does this itself (D4).

**Tests.** The component is tested with vitest and a memory router, one fresh mount per test: the roles and ids, the roving `tabindex`,
the arrow keys with wrap, Home and End, automatic activation, the query read (missing, unknown, known), the replace of the history entry,
and the hidden panels. It is also tested in one Playwright case on the system information page (task group 3).

## D4. The tab address of the system information page

The page uses the component of D3 with the keys `overview`, `plugins`, `environment` and `configuration`, and `queryParam` `tab`.

Section anchors are a second way in. A pure function maps a fragment to a tab:

| Fragment | Tab |
| --- | --- |
| `support-ingot`, `support-java`, `support-os`, `support-database`, `support-storage`, `support-cluster` | `overview` |
| `support-plugins` | `plugins` |
| `support-environment`, `support-properties` | `environment` |
| `support-configuration` | `configuration` |

When the address has a fragment from this table, the page selects the tab of the fragment, writes it into `tab` (replace), waits
for the panel to render, and scrolls the element with the fragment's id into view. A fragment from the table wins over a `tab`
parameter that disagrees with it. Any other fragment is left to the browser.

## D5. The Overview cards

The Overview is a grid of six cards, one per short section: Ingot, Java, Operating system, Database, Storage and Cluster. Each card
is an `article` labelled by its heading. The heading keeps its id `support-<section>`, so the anchor of D4 scrolls to the card.

Each card shows its few key values, for example the Ingot version and build, the Java vendor and version and the heap, the operating
system name and architecture, the database kind, the storage kinds, and the number of cluster members. The long values stay reachable
inside the card: the Java input arguments are a disclosure (`details`/`summary`) in the Java card, closed by default, with the label
"Input arguments". The cluster member list stays a list in its card.

Grid: one column below 600 pixels, two columns from 600 to 1239 pixels and three columns from 1240 pixels. The grid is a plain CSS grid with
`gap` from the existing spacing tokens. The cards use the surface token of the current theme, no new colour.

The Overview has no filter. It is not a list of rows.

## D6. Plugins and the tier labels

The Plugins tab is the plugin list of today, with the tier label of each plugin in the same part `plugin-tier`. It holds one list and is
short, so it has no filter. The tier labels and their fallback (`TIER_LABELS`, `community` when the tier is unknown) are unchanged.

## D7. Environment and Configuration, and the filter

Environment holds two lists, the environment variables and the system properties, each under its own heading (`support-environment`
and `support-properties`). Configuration holds the local configuration (`support-configuration`, subheading Local) and the shared
configuration (subheading Shared).

The Environment and Configuration tabs get one filter field above their content. The field is a text input with the label "Filter
by key or value", type `search`. It narrows the rows of the lists by a case-insensitive substring match against the key or the value.
Rules:

- the filter works on the strings the server sent. It never reads a value the page does not show, and it never asks the server for one.
  The masking of the server stays the only masking: `SystemInformation` is already redacted (`SupportInformation.kt`: environment
  variables withheld by name unless the contract allows the value; system properties the same; local values through the allowlist; shared
  settings through the existing masking). A filter on a masked value matches the masked text only;
- the filter applies to Environment (both lists) and to Configuration Local. The Shared configuration is a JSON block of nested values,
  not rows. It is not filtered: it is shown whole, with a note that the filter does not apply to it (D10);
- when no row matches, the list says "No entry matches the filter." and no list is hidden;
- the filter value is kept in the component only and is not written to the address, so a reload starts without a filter (D10).

The filter field carries `data-ingot-part="filter"`.

## D8. The download action and the masking note

The support bundle moves into the page header, beside the page heading, and is shown on every tab. It is a `button` with the label
"Download support bundle", the same text as today, styled as today, with the existing `busy` state while the zip is built. Its part is
`support-action`.

The short note on what is removed moves with it, in a disclosure labelled "What is removed" under the header. The text is the one of
`SupportView.vue`, unchanged in its words: secrets removed or masked before the bundle is built, values of settings that hold a secret
masked, log lines scrubbed of known secrets and of credentials in headers and URLs, and the reminder to read the zip before attaching it
to a public issue. Its part is `support-note`.

The page keeps the two states of the support page. When the availability check answers `off`, the page shows "The support pages are turned
off on this instance." and no tab. When it fails, the page shows "The support bundle cannot be checked right now." and no download action.
When the system information request fails, the Overview shows the message of that failure, as today.

Both availability and system information are requested on mount, as today. The loading state (`data-ingot-state="loading"`) remains on the
root until both have answered.

## D9. Old addresses and names

- `/settings/support` becomes a route with `redirect: { name: 'system-info' }`. It keeps its name `support`, so a route name in code, in a
  plugin or in a test still resolves, and no address answers 404. The redirect keeps any query and fragment, so
  `/settings/support#support-configuration` opens Configuration.
- The route order of `src/router/routes.ts` does not change: both names stay before the generic settings route, because the generic route
  would take `system-info` and `support` as domains. The test of the archived `add-support-bundle` (names must not collide with a settings
  domain id) stays and is extended to the redirect.
- The navigation tree has no `support` entry after the change. Its page key `page:support` disappears; the navigation test is updated in
  the same commit as the entry.

## D10. The anchor contract

The root of the merged page is `data-ingot-component="system-info-view"`, with the part `plugin-tier` as before, plus these parts:
`support-action` (the download button), `support-note` (the disclosure of D8) and `filter` (the field of D7). The state `loading` stays on
the root. The `error` state is used on the root when the system information request fails, and `empty` when the availability check answers
`off`. The closed set of states (`STATES` in `src/anchors/checks.ts`) already holds both.

The tabs component of D3 is a shared component. It is registered in the anchors registry as its own component, `app-tabs`, with its
parts (`tab`, `tabpanel`) only if a test selects them by part; otherwise it carries its `data-ingot-component` only.

`support-view` is a name that plugins may use today. The root of the merged page carries `data-ingot-component-alias="support-view"`, and
the registry declares it in `aliases`:

```json
{ "name": "support-view", "replacedBy": "system-info-view", "removeIn": "<set in the code pull request>" }
```

`aliasProblems` in `src/anchors/checks.ts` checks that `replacedBy` is a registered component and that the release of the registry has not
reached `removeIn`. The entry `support-view` is removed from `components`, because its file no longer exists. The reference page (`render.js`)
lists the alias with its removal version. `removeIn` is set to a concrete version in the code pull request (D11).

## D11. Decisions

These questions were open in the first version of this proposal. The maintainer decided them on pull request 572. Each decision is final for
this change. A later change to one of them is a new proposal.

1. **Navigation label.** "System and support" (D2).
2. **Tab primitive.** This change builds a general accessible tabs component in its own task group, before the page (D3). It is designed for
   reuse, and `rework-admin-header-and-repository-tabs` task 4.6 is planned to reuse it.
3. **Grid breakpoints.** One column below 600 pixels, two from 600 to 1239 pixels, three from 1240 pixels (D5).
4. **Shared configuration.** Not filtered. It is shown whole, with a note that the filter does not apply to it (D7).
5. **Filter in the address.** Not in the address. A reload clears the filter (D7).
6. **Release of the alias.** `removeIn` is set to a concrete version in the code pull request, once the release that ships the change is known.
   The alias is removed in the minor release that `removeIn` names, in its own pull request (D10).
7. **Copy action.** None. The page has only the download action (D1, D8).
8. **Old address.** `/settings/support` redirects to `/settings/system-info`, keeping query and fragment (D9).

## D12. Tests and checks

Every test is written first and seen to fail on the current build. Unit tests run with vitest, with no sleeps, no system clock, and a fresh
component and router per test. The pure functions (D4 fragment to tab, D7 filter) are tested without a component. The tabs component is tested
as in D3. Playwright tests cover the redirect, the address, the anchors, the keyboard model and layout at 320, 600 and 1440 pixels, and axe on
each tab. The anchor checks (`anchors.test.ts`, `source-tree.test.ts`) cover the alias, the parts and the new component.

## Risks

- **A second tabs component on `main`.** Not a risk here: this change builds the only one, and `rework-admin-header-and-repository-tabs`
  reuses it (D3). Its task 4.6 still names `migrate-content-to-components` as the primitive, which the maintainer must align.
- **Plugins that read `support-view` today.** Mitigation: the alias of D10 and the redirect of D9. The `support-view` element is the merged
  root, so a selector on it still matches.
- **The header gets heavier.** Mitigation: the note is behind a disclosure, and the header holds one button.
