# Design

The design refers to the files of `reposilite-frontend/src` on `main`. The change builds on `rework-admin-header-and-repository-tabs`
(the tab list and its keyboard model, which this change reuses; not yet merged), `add-support-bundle` (the endpoints and the
redaction, archived) and `ui-component-anchors` (the registry, the parts, the states and the alias rule).

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

**Copy action.** Neither page has a copy-for-support action. The only support action is the download of the zip. This design
does not invent one. Open question 7 asks whether a copy action is wanted.

## D2. One page, one navigation entry

The two entries of the Support group in `src/navigation/registry.ts` become one: `key: 'system-info'`, `route: 'system-info'`,
`label: 'System and support'`. The description is the one of today's system information entry, extended with the words of the
support entry: "What this instance runs on and what a maintainer needs to look at. Secrets are withheld or masked, and the page
only reads." The keywords are the union of both, `version, java, environment, zip, debug`. The entry keeps its permission
(`ADMIN_NODES.console`) and its `requires` (`supportBundleOffered`). The `support` entry is removed.

The label "System and support" is the default. It names both things the page holds and fits the group heading "Monitoring and
support". Open question 1 lists the alternatives.

## D3. The tab list and the tab address

The tab list is the one of `rework-admin-header-and-repository-tabs` (its D5 and D8): a WAI-ARIA `tablist` with `tab` controls
and one `tabpanel` per tab. This change adds no tab component. The tabs are, in this order, `overview` (Overview), `plugins`
(Plugins), `environment` (Environment) and `configuration` (Configuration).

The selected tab is the query parameter `tab` of `/settings/system-info`:

- a missing or unknown value opens `overview`;
- a change of tab replaces the history entry, so the back button leaves the page and does not step through the tabs;
- the parameter is written when a tab is opened and read when the page is entered, so a link and a reload keep the tab.

Section anchors are a second way in. The function that maps a fragment to a tab is a pure function with a table:

| Fragment | Tab |
| --- | --- |
| `support-ingot`, `support-java`, `support-os`, `support-database`, `support-storage`, `support-cluster` | `overview` |
| `support-plugins` | `plugins` |
| `support-environment`, `support-properties` | `environment` |
| `support-configuration` | `configuration` |

When the address has a fragment from this table, the page selects the tab of the fragment, writes it into `tab` (replace), waits
for the panel to render, and scrolls the element with the fragment's id into view. A fragment from the table wins over a `tab`
parameter that disagrees with it. Any other fragment is left to the browser.

## D4. The Overview cards

The Overview is a grid of six cards, one per short section: Ingot, Java, Operating system, Database, Storage and Cluster. Each card
is an `article` labelled by its heading. The heading keeps its id `support-<section>`, so the anchor of D3 scrolls to the card.

Each card shows its few key values, for example the Ingot version and build, the Java vendor and version and the heap, the
operating system name and architecture, the database kind, the storage kinds, and the number of cluster members. The long values
stay reachable inside the card: the Java input arguments are a disclosure (`details`/`summary`) in the Java card, closed by default,
with the label "Input arguments". The cluster member list stays a list in its card, and is not put in a disclosure, because it is
short in practice.

Grid: one column below 600 pixels, two columns from 600 to 1239 pixels and three columns from 1240 pixels. The maintainer asked for
three columns and one column at phone width; the two-column step between them is a choice of this design (open question 2).
The grid is a plain CSS grid with `gap` from the existing spacing tokens. The cards use the surface token of the current theme,
no new colour.

The Overview has no filter. It is not a list of rows.

## D5. Plugins and the tier labels

The Plugins tab is the plugin list of today, with the tier label of each plugin in the same part `plugin-tier`. It holds one
list and is short, so it has no filter. The tier labels and their fallback (`TIER_LABELS`, `community` when the tier is unknown)
are unchanged.

## D6. Environment and Configuration, and the filter

Environment holds two lists, the environment variables and the system properties, each under its own heading (`support-environment`
and `support-properties`). Configuration holds the local configuration (`support-configuration`, subheading Local) and the shared
configuration (subheading Shared).

The Environment and Configuration tabs get one filter field above their content. The field is a text input with the label "Filter
by key or value", type `search`. It narrows the rows of the lists by a case-insensitive substring match against the key or the
value. Rules:

- the filter works on the strings the server sent. It never reads a value the page does not show, and it never asks the server for
  one. The masking of the server stays the only masking: `SystemInformation` is already redacted (`SupportInformation.kt`:
  environment variables withheld by name unless the contract allows the value; system properties the same; local values through the
  allowlist; shared settings through the existing masking). A filter on a masked value matches the masked text only;
- the filter applies to Environment (both lists) and to Configuration Local. The Shared configuration is a JSON block of nested
  values, not rows, so it stays whole and the block says that the filter does not apply to it (open question 4);
- when no row matches, the list says "No entry matches the filter." and no list is hidden;
- the filter value is kept in the component only. It is not written to the address, so a reload starts without a filter.
  Open question 5 asks whether it should be in the address.

The filter field carries `data-ingot-part="filter"`.

## D7. The download action and the masking note

The support bundle moves into the page header, beside the page heading, and is shown on every tab. It is a `button` with the label
"Download support bundle", the same text, styled as today, with the existing `busy` state while the zip is built. Its part is
`support-action`.

The short note on what is removed moves with it, in a disclosure labelled "What is removed" under the header. The text is the one of
`SupportView.vue`, unchanged in its words: secrets removed or masked before the bundle is built, values of settings that hold a
secret masked, log lines scrubbed of known secrets and of credentials in headers and URLs, and the reminder to read the zip before
attaching it to a public issue. Its part is `support-note`.

The page keeps the two states of the support page. When the availability check answers `off`, the page shows "The support pages are
turned off on this instance." and no tab. When it fails, the page shows "The support bundle cannot be checked right now." and no
download action. When the system information request fails, the Overview shows the message of that failure, as today.

Both availability and system information are requested on mount, as today. The loading state (`data-ingot-state="loading"`) remains on
the root until both have answered.

## D8. Old addresses and names

- `/settings/support` becomes a route with `redirect: { name: 'system-info' }`. It keeps its name `support`, so a route name in code, in a
  plugin or in a test still resolves, and no address answers 404. The redirect keeps any query and fragment, so
  `/settings/support#support-configuration` opens Configuration.
- The route order of `src/router/routes.ts` does not change: both names stay before the generic settings route, because the generic
  route would take `system-info` and `support` as domains. The test of the archived `add-support-bundle` (names must not collide with a
  settings domain id) stays and is extended to the redirect.
- The navigation tree has no `support` entry after the change. Its page key `page:support` disappears; the navigation test is updated
  in the same commit as the entry.

## D9. The anchor contract

The root of the merged page is `data-ingot-component="system-info-view"`, with the part `plugin-tier` as before, plus these parts:
`support-action` (the download button), `support-note` (the disclosure of D7), `filter` (the field of D6). The state `loading` stays on
the root. The `error` state is used on the root when the system information request fails, and `empty` when the availability check
answers `off`. The closed set of states (`STATES` in `src/anchors/checks.ts`) already holds both.

`support-view` is a name that plugins may use today. The root of the merged page carries `data-ingot-component-alias="support-view"`, and
the registry declares it in `aliases`:

```json
{ "name": "support-view", "replacedBy": "system-info-view", "removeIn": "<the minor release after the release that ships this change>" }
```

`aliasProblems` in `src/anchors/checks.ts` checks that `replacedBy` is a registered component and that the release of the registry has not
reached `removeIn`. The entry `support-view` is removed from `components`, because its file no longer exists. The reference page
(`render.js`) lists the alias with its removal version. `removeIn` gets a concrete version in the code pull request, once the release
number of the change is known (open question 6). The alias is removed in the minor release that `removeIn` names, in its own pull request.

## D10. Tests and checks

Every test is written first and seen to fail on the current build. Unit tests run with vitest, with no sleeps, no system clock, and a
fresh component and router per test. The pure functions of D3 (fragment to tab, query to tab) and of D6 (filter) are tested without a
component. Playwright tests cover the redirect, the address, the anchors, the keyboard model and layout at 320, 600 and 1440 pixels, and
axe on each tab. The anchor checks (`anchors.test.ts`, `source-tree.test.ts`) cover the alias and the parts.

## Risks

- **The tab list is not on `main` yet.** Mitigation: the code pull request waits for `rework-admin-header-and-repository-tabs` (open
  question 3). Building a second tab component here is not the default.
- **Plugins that read `support-view` today.** Mitigation: the alias of D9 and the redirect of D8. The `support-view` element is the
  merged root, so a selector on it still matches.
- **The header gets heavier.** Mitigation: the note is behind a disclosure, and the header holds one button.

## Open questions

Each question has a default. The tasks implement the default. An answer that differs changes the matching decision and nothing else.

1. **Navigation label.** Default: "System and support". Alternative: "System information" (keeps the older name, drops the word support from
   the label) or "Support" (the shorter name, which hides the system information from the label).
2. **Grid breakpoint.** Default: one column below 600 pixels, two from 600 to 1239 pixels and three from 1240 pixels. Alternative: one
   column below 600 pixels and three from 600 pixels, which is cramped at 600.
3. **Tab primitive.** Default: wait for the tab list of `rework-admin-header-and-repository-tabs` and use it. Alternative: build the list
   here, which puts a second tab component on `main` until one is removed.
4. **Shared configuration and the filter.** Default: the filter applies to Environment and Configuration Local, the Shared block stays whole.
   Alternative: the Shared block is flattened into dotted key rows, which changes how it is read.
5. **Filter in the address.** Default: not in the address; a reload clears it. Alternative: `?filter=` for a shareable filtered view, which
   puts the text of a value in the address.
6. **Release of the alias.** Default: `removeIn` is the minor release after the one that ships this change, set to a concrete version in the
   code pull request. Alternative: a fixed version named now by the maintainer.
7. **Copy action.** Default: none is added; the page has only the download. Alternative: a copy of the system information as text for a
   support request, which is a new feature with its own requirement and is not part of this change.
8. **Redirect of the old address.** Default: `/settings/support` redirects to `/settings/system-info`, keeping query and fragment. Alternative:
   the redirect keeps the old page's address as an entry of its own, with the download only, which keeps two pages.
