# Proposal

## Why

The plugin page model (`add-plugin-ui-extension-point`, archived) has six blocks: `text`, `keyValue`, `table`, `form`, `action` and
`status`. They draw administration pages well. They do not draw a catalogue. The two portal plugins that the repository plans
(`add-gradle-plugin-portal-plugin`, `add-minecraft-plugin-portal-plugin`) need four things that the set lacks:

- a list of items with a title, a line of text and a few tags, with a way to open an item (a card or row list);
- a code snippet a reader can copy, such as the `plugins { id(...) }` block of a Gradle build;
- a filter bar whose choices come from the plugin, such as a loader or a game version, with counts;
- a link to an address on the same host, such as the Maven path of a download.

The model says today that it has no link at all: `docs/gitbook/references/plugins/plugin-api.md`, "Blocks", states that there is
no block that carries a link. That rule is what keeps a plugin from opening an arbitrary address in the administrator's browser, so
it is changed on purpose here, not by accident: a link is accepted only when it is a path on the same host, under rules that the
server and the dashboard both check.

Facts checked against `main` at `3614657d0`:

- `PluginUiBlock` is a sealed interface with six data classes (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/api/PluginUiModel.kt`).
- `PluginUiValidator` checks each block's paths with `pathProblem` and refuses unknown shapes (`frontend/api/PluginUiValidator.kt`).
- `PluginBlock.vue` draws the six blocks and re-checks every path with `pathAllowed` before it calls anything
  (`reposilite-frontend/src/components/plugins/PluginBlock.vue`, `pluginBlocks.ts`). A block type it does not know shows
  an "Unsupported block" note.
- The copy action of a snippet already exists: `components/card/SnippetsCard.vue` and `components/card/copySnippet.ts` write to
  the clipboard, and select the text when the clipboard is refused. The status region of `openspec/specs/browse-overview` states
  the requirement for the announcement.
- The browse views draw lists as rows (`components/browser/FileList.vue`, `components/search/SearchResultRow.vue`). The widths of
  `openspec/specs/browse-overview`, "Row marks stay inside the row at compact widths", and of `fix-browse-overview` apply here.

## What Changes

- **Four block types**, added to `PluginUiBlock` as new data classes. No existing class changes its constructor.
  - `List`: a GET source that returns items with `title`, `subtitle`, `chips` and an optional `href`. A page of items with a
    `next` cursor is loaded by a "Load more" control. At most 100 items per response are drawn.
  - `Code`: plain text, an optional label and a language name that only labels it. It has a copy action that uses the existing
    clipboard helper and the existing fallback.
  - `Filter`: one or more controls (a free text field and select fields with options that the plugin declares). It applies to the
    `List` block that follows it on the same page. The values become query parameters of that list's source. Option counts come
    from the list response, computed by the plugin.
  - `Link`: a label and a same-origin `href`.
- **Link rules**, checked by the server at registration and by the dashboard before it binds an address, including an address that
  arrives in a list item at run time: a path that starts with a single `/`, that has no `//`, no scheme, no `..`, no empty segment,
  that does not start with `/api/` or `/assets/`, and that uses the characters `A-Za-z0-9._~/?=&-`. Links open in the same tab.
- **Reader pages** (`add-plugin-reader-pages`) may use the four blocks. Their read-only rule holds: no `form`, `action` or `status`.
- **Manifest version stays 1.** The new blocks are additive, and a dashboard that does not know one shows the existing "Unsupported
  block" note.
- **Documentation.** The block table of `docs/gitbook/references/plugins/plugin-api.md` and the example of
  `docs/gitbook/manage/plugins/extend-the-dashboard-from-a-plugin.md` change. No configuration key changes.

### Out of scope

- Images, icons and any `img` or `src` attribute. Icons belong to `add-plugin-asset-routes`, which is optional and not written.
- Cards in a grid. A list draws rows. A grid is a later layout choice, not a new block.
- Client-side search across all items. A filter is sent to the plugin's source, which answers with the rows it allows.
- Facet computation, search attributes and access checks. They belong to `add-search-attribute-facets` and
  `expose-plugin-access-checks`. This change draws the counts it is given.
- Syntax highlighting. A code block is monospace text with a label.
- Any change to the browser policy, to the signed-in manifest, or to permissions.

## Capabilities

### New Capabilities

- `plugin-ui-browse-blocks`: the list, code, filter and link blocks, their limits, the link rules and the copy behaviour.

### Modified Capabilities

- `plugin-ui-extension`: the block types of a page and the rule that a block carries no address except a same-origin path.

## Impact

- **Type and scope.** The change ships under `feat(frontend)`. Test commits use `test(frontend)`, docs commits `docs(frontend)`. The pull
  request title, squash-merged onto main, is:

  `feat(frontend): add list, code, filter and link blocks to plugin pages`

  Its first two pull requests carry `feat(frontend): accept code and link blocks in plugin pages` and `feat(frontend): accept list blocks
  in plugin pages`, which are the same type and scope.
- **Breaking marker.** No breaking change. No operator changes a configuration key, a variable, a volume, a manifest, an image or a
  plugin. A plugin that uses the new blocks needs a server that has them. It is a new feature of the plugin API, and an older server
  rejects that plugin's new block constructors at load time, which is the expected way to learn about a missing feature. The title
  carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None. No page of an existing plugin uses the new blocks. The "Declarative pages only" rule still
  holds for every block: no HTML, script or style.
- **Dependencies.**
  - `add-plugin-ui-extension-point` (merged, archived): the sealed block model and its validator.
  - `add-plugin-reader-pages` (other agent, soft): the readers can use the blocks. The blocks also work on admin pages, so the two can
    land in either order.
  - `add-search-attribute-facets` (other agent, Minecraft numbering G4, soft): facet counts for the filter. Until it lands, a filter
    shows no counts.
  - `expose-plugin-access-checks` (other agent, G7, soft): the list responses are filtered by the viewer, and the blocks do not
    decide that.
  - `extend-plugin-abi-gate` (G8, soft): the new classes show in the binary report. The report must show only additions to the sealed
    interface and no removed or changed constructor.
  - Consumers: `add-gradle-plugin-portal-plugin` (the snippet and the list), `add-minecraft-plugin-portal-plugin` (the list, the filter
    and the download link).
- **Code.** `reposilite-backend` (the four data classes, the validator), `reposilite-frontend` (four block components or branches in
  `PluginBlock.vue`, `pluginBlocks.ts`, the copy helper), `docs/gitbook/`.

## Verification

- Each code pull request runs its targeted tests through `heavy` with `--max-workers=2`. Backend unit tests cover the validator, and
  integration tests cover a list source with a cursor. Frontend tests cover the blocks and run with `npm test` in `reposilite-frontend`.
- Each code pull request is checked at 320, 600 and 1440 px in a browser, with no horizontal page scroll.
- `openspec validate add-plugin-ui-browse-blocks --strict --no-interactive` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-ui-browse-blocks` prints nothing.
