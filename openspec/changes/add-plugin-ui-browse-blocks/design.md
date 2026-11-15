# Design

Paths are relative to the repository root. Backend paths start at `reposilite-backend/src/main/kotlin/com/reposilite/`, frontend
paths at `reposilite-frontend/`.

## Context: what the block model can draw

- **Six blocks.** `PluginUiBlock` (`frontend/api/PluginUiModel.kt`) is a sealed interface with `Text`, `KeyValue`, `Table`, `Form`,
  `Action` and `Status`. Each is a data class with a `type` string. Plugins construct them; they never read them back.
- **Validation at registration.** `PluginUiValidator.blockProblems` checks lengths, methods and paths. `pathProblem` requires the
  prefix `/api/plugins/<plugin>/` and the characters `A-Za-z0-9._~/-` (`frontend/api/PluginUiValidator.kt`).
- **Drawing.** `PluginBlock.vue` draws each type. It calls only paths that `pathAllowed` (`components/plugins/pluginBlocks.ts`) accepts,
  and it draws a value with Vue interpolation. It has no `v-html`. An unknown type shows "Unsupported block".
- **No link today.** `docs/gitbook/references/plugins/plugin-api.md`, section "Blocks", says that there is no block with a link. The
  archived proposal (`add-plugin-ui-extension-point/design.md`, "Consequences") says that no address from a plugin is opened or loaded
  by the browser. This change keeps the rule for everything except one case: a same-origin path in a `Link` or a `List` row.
- **Copy.** `components/card/SnippetsCard.vue` copies with `copyToClipboard` and falls back to `selectText` (`copySnippet.ts`). Its
  status line is the "Copy feedback is announced" requirement of `openspec/specs/browse-overview`.
- **Rows.** `components/browser/FileList.vue` and `components/search/SearchResultRow.vue` draw rows that wrap at narrow widths. The
  browse specs require that a row mark stay inside its row at compact widths.
- **Filters.** The search page has filter controls (`components/search/SearchFilters.vue`). They are not reused: their state lives in
  the search query, and a plugin page needs its own state.

## Findings that shape the design

- The portal plugins need a list with chips, a copy snippet, a filter with counts and a same-host download link (the two portal
  proposals, "Context" and "Decisions"). No existing block gives them these.
- A list is data from a response at run time, not from the registration. So a link inside a list row is checked twice: at
  registration for the plugin's declared paths, and by the dashboard for each row before it binds the address. The manifest is not
  the only input the dashboard trusts, and the response is not trusted either.
- `form` already draws from a JSON schema with `JsonForms`, so the filter does not need a new form engine.

## Security model

### Trust boundaries

Unchanged from `add-plugin-ui-extension-point`: plugin code runs on the server, the browser runs only the dashboard's components, and a
block is data. A list row and a filter value come from the plugin's own responses and the visitor's input. Both are data.

### Threats and the control for each

| # | Threat | Control |
| --- | --- | --- |
| B1 | A link runs script (`javascript:`, `data:`, `vbscript:`) | A link is a path. It must start with a single `/`, so no scheme can appear. Checked at registration and again before binding, for manifest and response data alike. |
| B2 | A link leaves the host (`//evil.example`, `\\`, a full URL) | `//` and backslash are refused. The character set has no `:` and no `@`, so a URL with a host cannot be written. |
| B3 | A link calls a data route and shows its JSON as a page | `/api/` and `/assets/` are refused. Dashboard pages and repository paths are allowed. |
| B4 | A link with traversal (`..`) reaches another prefix | `..`, empty and `.` segments are refused, as for block paths. |
| B5 | Markup in a title, subtitle, chip, code text or option label | Vue interpolation only. No field is rendered as HTML, and the validator bounds the length of each. |
| B6 | A copy action writes something other than the text the page shows | It copies the `text` field and nothing else. A refused clipboard selects the text, as the snippets already do. |
| B7 | A filter key changes a parameter the plugin uses for paging or for another control | Filter keys match `[a-z][A-Za-z0-9]{0,31}` and are unique on the page. `cursor` is reserved for paging and cannot be a filter key. Values are sent as query parameters by the HTTP client, so they are encoded, and they are at most 200 characters. |
| B8 | A plugin floods the page with rows | A response shows at most 100 rows, and "Load more" asks for the next cursor. A table already says when it cuts; a list does the same. |
| B9 | A list shows an item the viewer may not read | The plugin filters the rows (`expose-plugin-access-checks`). The block does not decide visibility, and it says so in the documentation. |
| B10 | A filter count reveals the count of rows the viewer may not read | Counts come from the plugin's response, which the plugin computes after its access filter (`add-search-attribute-facets`, D5 of the Minecraft proposal). The block only draws them. |
| B11 | A name that is shown differently from the real artifact | A list row shows the title the plugin sends, and the plugin should send the coordinates in the subtitle. The rule is in the documentation, not enforced. |

## Decisions

### D1: four blocks, as data classes

```kotlin
data class ListBlock(val source: String, val pageSize: Int = 50)   // see D2
data class CodeBlock(val text: String, val label: String? = null, val language: String = "plain")
data class FilterBlock(val controls: List<FilterControl>)
data class LinkBlock(val label: String, val href: String)
```

Each is a new data class with defaults only where a field is optional. No existing class changes. The `Block` suffix keeps the names apart
from the Kotlin `List` type that `FilterBlock` uses. `FilterControl` is a sealed interface with `Text(key, label)` and
`Select(key, label, options)`, and `Option(value, label)` is the option of a select. The `type` strings are `list`, `code`, `filter` and
`link`.

### D2: the list

`ListBlock(source: String, pageSize: Int = 50)`.

The source is a GET path below the prefix. The response is an object:

```json
{ "items": [ { "title": "...", "subtitle": "...", "chips": ["..."], "href": "/..." } ], "next": "opaque or null", "facets": { "loader": [ { "value": "paper", "count": 12 } ] } }
```

- `items`: at most `pageSize` per response, and at most 100 drawn. Each item has a `title` (1 to 200 characters), an optional `subtitle`
  (at most 400), `chips` (at most 8 strings of 1 to 40 characters) and an optional `href` checked by D4.
- `next`: an opaque cursor. When it is a string, the list shows "Load more", which sends the same source with `cursor=<next>` and the
  current filter values. When it is null, the list is complete.
- `facets`: optional, read by the filter of D3.
- An item that does not have the expected shape is drawn as its text, and an item without a `title` is skipped with a note.

A list shows an empty state text of its own: "Nothing to show." It names no count it cannot know.

### D3: the filter

`FilterBlock(controls)`, with 1 to 6 controls. A `Text` control sends its value as the parameter named by its key. A `Select` control
sends its option value. An empty value is not sent. The filter applies to the next `List` block of the same page, and the page
validator refuses a filter that has no list after it.

Counts: a `Select` shows the count of each option from `facets[key]` when the list response has it, and no count otherwise. The
dashboard does not compute counts. An option that the response does not list is shown without a count.

Changes are sent after a short pause for the text field (a debounce of the dashboard, not a test timer). A change resets the list to its
first page and drops the cursor.

### D4: links

A link is a path, checked by `linkProblem` in the validator and by a function of the same name in `pluginBlocks.ts`. The rules, all of
which must hold:

1. It starts with `/` and the second character is not `/` and not `\`.
2. It does not start with `/api/` or `/assets/`.
3. Its characters are in `A-Za-z0-9._~/?=&-`. Percent signs are not allowed, so an encoded path cannot hide a `..` segment.
4. No segment is empty, `.` or `..`, counting the part before `?`.
5. It is at most 500 characters.

A `Link` block carries a `label` (1 to 60 characters) and an `href`. A list item carries an `href`. A row whose `href` fails the rules
is drawn without a link and with the note "Link not shown". The link opens in the same tab. There is no `target` attribute, so no new
browsing context is opened.

### D5: code

`CodeBlock(text, label, language)`. `text` is 1 to 4000 characters, drawn in a `pre` element with `white-space: pre`, and wrapped with
`overflow-wrap: anywhere` so it fits 320 px without a page scroll. `label` is at most 60 characters and is drawn as text above it.
`language` is one of `plain`, `kotlin`, `groovy`, `xml`, `properties` and only names the block for assistive technology. It does not
change how the text is drawn.

The copy action sits above the box, outside the text. It uses `copyToClipboard`, and when the clipboard refuses, `selectText`, as
`SnippetsCard.vue` does. The outcome goes to one status region that is present before the first click, so the announcement works.

### D6: the viewer and the reader pages

Reader pages (`add-plugin-reader-pages`) may use all four blocks. The read-only rule is unchanged: no `form`, `action` or `status`. A
`List` on a reader page is read with the same credentials as the rest of the page, and its source is a reader source of
`add-plugin-reader-pages`.

### D7: versions

The manifest stays at `version` 1. The four blocks are additive. A dashboard that is older than the server shows "Unsupported block" for
them, and the rest of the page is drawn. A plugin built against this API needs a server that has these blocks. The plugin documentation
says so, and the portal plugins name the Ingot release they need in their `compatibility` block (`add-plugin-store`).

### D8: the binary surface

`PluginUiBlock` stays sealed. Four subclasses are added. Plugins construct blocks, so a plugin compiled before this change keeps its
constructors. The binary report of `extend-plugin-abi-gate` shows the additions and no removed member. The change has to show that before
it is merged, and the test of D9 covers the constructors of the old blocks.

### D9: tests

- Backend unit tests (F.I.R.S.T.: no sleeps, no clock, no shared state): `linkProblem` accepts `/plugins/x/y`, `/browse/repo/a/b.jar`,
  and refuses `//h`, `/\h`, `https://h`, `/api/tokens`, `/assets/x`, `/a/../b`, `/a%2e%2e/b`, `/a b`, a 501-character path.
- Backend unit tests: a filter without a following list is refused; a control with the key `cursor` is refused; two controls with one key
  are refused.
- Backend test: a list block with a source outside the prefix is refused, and the constructors of the six old blocks are unchanged
  (a reflection test asserts the parameter lists, so a later edit that changes them fails).
- Frontend unit tests (Vitest, `PluginBlock.test.ts` and `pluginBlocks.test.ts`): a row with `href: "javascript:alert(1)"` draws no link;
  a title with `<img src=x onerror=alert(1)>` draws the characters; the copy action writes the code text and nothing else; a refused
  clipboard selects the text; "Load more" sends `cursor`; a `cursor` filter key is not drawn.
- Browser check (Playwright, `--workers=2`): a list page at 320 px has no horizontal scroll, and a code block wraps.

## Alternatives considered

- **Allow any `https` link.** Rejected: it lets a plugin send the administrator's browser to an address outside Ingot, and no
  requirement of the portal plugins needs it. A download on the same host is enough.
- **Allow `img` and icons.** Rejected for this change. It needs a route that serves plugin bytes safely, which is
  `add-plugin-asset-routes`, optional and not written.
- **Let the plugin send HTML for a snippet.** Rejected by the existing model. The code block shows text.
- **A generic `table` with a click action instead of a list.** Rejected: a table has no chips, no subtitle and no cursor, and a row is
  not a link.
- **Filter on the client over all items.** Rejected: the plugin, not the dashboard, decides which rows the viewer may see, and a client
  filter over rows the plugin did not filter would leak them to the browser's memory.

## Risks

- **A plugin that sends a long list without a cursor.** Mitigation: at most 100 rows are drawn and the cut is stated.
- **A block shape that the portal plugins need and this change lacks.** Mitigation: the blocks are additive, so a later block is a new
  change, not a break.
- **Counts that are wrong.** The dashboard draws the counts it is given. The correctness of counts belongs to the plugin and to
  `add-search-attribute-facets`.

## Open questions (each has a default)

- **Q1. Cards or rows.** Default rows. A grid of cards is a layout choice of the dashboard and can follow.
- **Q2. Reserved parameter names.** Default `cursor` only. A plugin that needs `limit` declares it as a filter.
- **Q3. Link to another reader page.** Default: a link is a same-origin path, so `/plugins/<plugin>/<page>?item=...` works without a
  special rule.
- **Q4. Language names.** Default the five names above. More names are a later, additive change.
