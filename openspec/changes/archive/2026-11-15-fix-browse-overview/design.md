# Design

Written against origin/main after `dcd8294a6`. Paths are relative to the repository root. `reposilite-frontend/src` is
abbreviated `src` in this document. Line numbers were read on this worktree.

## Context

### The page and its layout

- `src/components/overview/RepositoryOverview.vue` renders the Browse page. The grid is at line 182:
  `lg:grid-cols-[minmax(0,1fr)_minmax(320px,400px)]`. The snippet card sits in its own column from `lg` on (lines 184-186,
  `lg:col-start-2 lg:row-start-1`). The list follows it in the DOM (line 187). Below `lg` the card is the first block, so
  it sits above the list.
- At 1920px the Tailwind container is 1536px wide, minus the 24px gutters of `src/style.css:166-180`, so the card column
  is its maximum of 400px. This matches the staging observation.
- Below 600px the gutter is 16px (`src/style.css:171`), and from 600px it is 24px (`src/style.css:174-176`).

### The snippet card

- `src/components/card/SnippetsCard.vue` is the card. The title is set in one place: line 65,
  `title.value = 'Repository details'`, for every repository path, with no repository name in it.
- An empty path (the root page) takes the generic branch at lines 86-90. `src/store/maven/repository.js:23-32` builds the
  snippet with `repoId` set to the instance id (`id`, from `{{REPOSILITE.ID}}`, filled in at
  `reposilite-backend/src/main/kotlin/com/reposilite/frontend/FrontendFacade.kt:127`) and the address
  `.../<repository>`, with a literal `<repository>`. The instance id is therefore what a user sees as the repository id.
  The comment at lines 77-80 says this is deliberate ("generic snippet to repository like domain.com/{repository}").
- The code block is `src/components/card/SnippetsCard.vue:228-232`: `whitespace-pre break-normal overflow-x-auto`. The
  commit `fa0c8d1a1` (PR #28) made it so on purpose: lines keep their indentation and are never broken, and long lines scroll
  sideways. Its message says the break-opportunity helper was removed because it had no effect without wrapping. This change
  changes that choice for the URL only (decision D2).
- The URL in the snippet is rendered by `src/components/card/RepositorySnippet.vue` (`XmlTag` for the XML url and the
  `CodeString` value for the others). `src/components/card/CodeString.vue:18` renders the value as a `span` with quotes.
- The copy action sits in its own row above the code at `SnippetsCard.vue:214-227`. The overlap seen on staging is therefore
  not on main: the test `SnippetsCard.test.js:124` ("keeps the copy button out of the scrolling code block") pins it.
- The status line is `SnippetsCard.vue:277-291`. Both messages ("Copied", and the selection hint) are `<p role="status">`
  elements created by `v-if` at the moment of the click. Assistive technology does not reliably announce a live region that
  is inserted together with its text, so the region must exist first.

### The repository filter

- `RepositoryOverview.vue:198-203` uses `SearchBar`, which renders `<form role="search">` (`src/components/search/SearchBar.vue:45`)
  with a magnifier icon, a pill of 56px height, and an input whose `aria-label` and `placeholder` are both the label
  (`SearchBar.vue`, the input element). The label is "Search by id, description or tag". The component is shared with the
  artifact search page (its header comment), so the change does not alter `SearchBar` itself.
- The filter text is passed to `filterRepositories` as the raw query, `RepositoryOverview.vue:130-133` (`text: text.value`).
  `parseSearch` (`src/store/maven/repositories.js:45-57`) is not applied, so `tag:ci` is searched as the literal word
  "tag:ci" in id, description and tags. The Administration page does apply it: `src/components/repositories/repositoryRows.js:84-86`
  calls `parseSearch` in `filterRows`, and its field says "Use tag:name for one tag." (`src/components/repositories/RepositoriesView.vue:190-196`).
- The field on the Administration page is an `MdTextField` with `variant="outlined"` and the label "Filter repositories"
  (`RepositoriesView.vue:190-196`). `src/components/md/MdTextField.vue` has a `leading-icon` slot (lines 168-173) and a
  supporting-text slot, which the Administration page uses.
- The empty state at `RepositoryOverview.vue:359-374` shows "No repository matches these filters." and a "Clear filters"
  button. It offers no link to the artifact search.
- The count is `<p role="status">` at lines 239-245, rendered only when not loading and not failed. It is announced on
  change, so the region exists after the first load. While loading it is absent.

### The type filter

- The chips are built from `TYPE_FILTERS` (`RepositoryOverview.vue:226-235`, `src/store/maven/repositoryTypes.js:49`). Their
  labels come from `TYPE_LABELS` (`repositoryTypes.js:28`): Hosted, Proxy, Group, Mixed.
- `typeBadgeOf` (`repositoryTypes.js:33-43`) gives the key `mixed` to every type that is not hosted, proxy or group, including
  an untyped repository whose server type is missing. The server derives the type of an untyped repository:
  `reposilite-backend/src/main/kotlin/com/reposilite/maven/RepositoryType.kt:54-61` (`effectiveRepositoryType`). A declared
  type wins. Otherwise no proxied reference means HOSTED, only local references means GROUP, only remote references means
  PROXY, and both means MIXED. `docs/gitbook/reference/repository-types.md:63` says the same ("Both local and external").
- The same word "Mixed" is a version policy on the Administration page: `src/components/repositories/RepositoriesTable.vue:71`
  (`VERSION_LABELS`, with `MIXED: 'Mixed'`).
- A row's badge shows the legacy hint when the type is not explicit (`src/components/tokens/RepositoryTypeBadge.vue`, lines
  with `legacy`).

### The row at compact widths

- Each row is the `RouterLink` at `RepositoryOverview.vue:284-355`: `flex min-w-0 gap-4 px-4`, so four 16px gaps separate
  five children, and the row has 16px of padding on each side.
- The avatar is 40px (`size-10`), the chevron 24px (`size-6`), and both are `shrink-0`. The type badge is `shrink-0` and
  `whitespace-nowrap` (`RepositoryTypeBadge.vue`, class of the root span), and the visibility label is `shrink-0`
  (`RepositoryOverview.vue:330-333`). Only the text column (line 303, `flex min-w-0 flex-1`) can shrink.
- The list is `overflow-hidden` (line 277), so a child that does not fit is clipped, not wrapped.
- Estimate, not measured: at 320px the list is 288px wide and the row content 256px. The avatar, gaps and chevron need
  128px. A legacy type badge with its hidden text and the visibility label need roughly 160px or more. The trailing marks
  therefore overflow and the chevron is clipped. At 1280px the list is about 808px wide (1280 minus 2 x 24, minus the 24px
  gap and the 400px card) and fits. At 1024px it is about 552px and fits. The defect is at compact widths only.

## Goals

- A visitor reads a whole repository URL on a 1920px and on a 320px screen, without a horizontal scrollbar.
- A visitor knows which repository the snippet is for, and what to replace when there is none.
- The filter is a filter of the list: its name, its styling and its announcements match the Administration page.
- A visitor who searched for an artifact name is one click from the artifact search with the name in it.
- "Mixed" is not read as a fourth declared type.
- The trailing marks of a row stay visible at 320px.

## Non-goals

- Changing the artifact search, the Administration page, the rail or the registry.
- Changing the server, the API, the derived type or its rule.
- Changing the copy content, the `<repository>` placeholder, or the repository id of the root snippet.
- A pixel or screenshot test in the unit suite (see tasks, the e2e note).

## Decisions

### D1. The card moves below the list below 1024px

The list comes first in the DOM, and the card follows it. The card keeps `lg:col-start-2 lg:row-start-1` and the grid
keeps `lg:grid-cols-[minmax(0,1fr)_minmax(320px,400px)]`, so from 1024px the layout does not change. Below 1024px the card
is the second block. Rationale: a phone visitor came for the list, and a build snippet above a hundred repositories pushes
the list below the first screen. A visitor who wants the snippet scrolls down to it.

Alternative rejected: a wider column at 1920px. A wider card only moves the cut-off point. The URL wrap in D2 fixes the
width problem at every column width, so the column keeps its size.

### D2. The URL wraps, nothing else does

The code block keeps `whitespace-pre`, so indentation, the line structure and the tags stay as they are. The value of the
URL (the `XmlTag` content for the XML repository, and the `CodeString` value for the others) is wrapped in a `span` with the
class `snippet-url`, which gets `white-space: normal` and `overflow-wrap: anywhere`. The browser then breaks the line only
inside that token.

The copied text is the `textContent` of the `pre` element that `RepositorySnippet.vue` exposes and `SnippetsCard.vue:137`
reads. Soft line breaks are not part of the
text, so the copied snippet is unchanged. This change inserts no zero-width space and no `<wbr>`, because either one would be
copied with the text. That rule was the point of PR #28, and it is kept.

The Kotlin and Groovy lines are short except for the URL, so only the URL wraps. The Maven and SBT lines have one URL each.
If a single line still exceeds the width with no URL in it, the block scrolls sideways as today.

### D3. The heading names the repository, the root says what to replace

- On a repository path (`/browse/<repository>` and below, when the card is for a repository and not an artifact) the
  heading reads `Repository details: <repository>`. The repository is the first segment of the path, the same segment
  `createRepositories` uses (`store/maven/repository.js:24`). The heading text is set in `SnippetsCard.vue:65` from the
  qualifier.
- On the root page the heading reads `Use a repository`. A caption under the heading reads: "Replace `<repository>` with the
  id of a repository from the list. The id in the snippet is a label in your build file, so the instance id works too." The
  caption is shown only on the root page and it is not part of the copied text.

The artifact card keeps "Artifact details" (`SnippetsCard.vue:70`). The snippet content is not changed. The test
`SnippetsCard.test.js:170` and `:197` assert "Repository details" and must be updated to the new heading, and the
collapsed header (`SnippetsCard.test.js:197`) still names the card.

### D4. One live region for the copy feedback

The status element is rendered at all times, below the code, with `role="status"`. Its text is set from `copyState`: "Copied",
or the selection hint, or empty. The `v-if` on the two `<p>` elements goes away. The visible result is the same. The region
is the one the test looks for, so `SnippetsCard.test.js:273-281` (status below the snippet, not in the editor) still holds.

The copy button keeps its `aria-label="Copy snippet"`. It does not change its label to "Copied", so the name of the button
is stable. The 2 second reset (`SnippetsCard.vue:141-143`) stays.

### D5. The repository filter is a filter

- `RepositoryOverview.vue` replaces `SearchBar` with an `MdTextField` with `variant="outlined"`, the label "Filter repositories",
  the supporting text "Search by id, description or tag. Use tag:name for one tag." and a `leading-icon` slot holding an
  inline filter icon (`aria-hidden="true"`, the same funnel as Material's `filter_list`). This is the field of
  `RepositoriesView.vue:190-196`.
- The field is not wrapped in a `role="search"` element. The rail's Search is the only search landmark of the dashboard.
- The filter text is passed through `parseSearch`: `tag:name` words become tags, and the rest is the text. The tags from the
  chips and the tags from the text are combined, so `tag:ci` and the chip `ci` select the same repositories. The address
  keeps the text in `q`, unchanged. This is the rule of `repositoryRows.js:84-86`, so both pages agree.
- The count `<p role="status">` (lines 239-245) is rendered during loading too, with empty text until the count is known.
  This makes its first announcement reliable.

### D6. The empty state links to the artifact search

When the list is empty and the text is not blank, the empty state keeps "No repository matches these filters." and adds a
`RouterLink` "Search artifacts for "<text>"" to `{ name: 'search', query: { q: text } }`. The search page reads `q`
(`SearchView.vue`, `parseSearchQuery(route.query)`), so the text is already entered there. "Clear filters" stays next to it.
When the text is blank (only tags or types are selected), there is no link, because the artifact search cannot express the
tag filter.

### D7. Mixed is a derived type, and the filter says so

- The chips are grouped under two headings. "Repository type" holds Hosted, Proxy and Group. "Derived type" holds Mixed.
  Each group is a `role="group"` with an `aria-label`.
- The Derived group has one line of supporting text: "Mixed: no type is declared, and the repository proxies both local and
  remote repositories." The text is shown under the chips and is linked to the group with `aria-describedby`.
- The address value stays `mixed` (`typesFromParam` and `TYPE_FILTERS` keys do not change). Only the presentation changes.
- The type badge of a row is not changed. A derived Mixed repository reports no declared type, so the badge keeps its legacy
  hint, which is accurate. The chip and the group carry the explanation of the word; the badge does not get a second one.

### D8. The trailing marks wrap below 600px

Below 600px the row keeps the avatar, the text column and the chevron on the first line. The type badge and the visibility
label move into a row under the text column, aligned with the text (the avatar width plus its gap, 56px, is the indent), so
they are never pushed out of the list. From 600px the row is the single line of today. The chevron stays `shrink-0`.

Implementation is in the markup, not in a helper: the trailing badges are wrapped in one element with
`flex flex-wrap gap-2`, placed under the text column on compact widths. A unit test checks the order of the elements and
the classes. The fit at 320px is measured in the e2e check (tasks, group 5).

### D9. Chips keep their state for assistive technology

`MdChip.vue:81` already sets `aria-pressed` on filter chips. No change. The tests assert it on the Derived group chip too.

## Accessibility summary

| Element | Requirement | Where |
|---|---|---|
| URL in the snippet | Readable without a horizontal scrollbar | D2 |
| Copy action | Not over code, name stable | D4 |
| Copy feedback | Live region exists before the text | D4 |
| Snippet heading | Names the repository | D3 |
| Filter field | Label "Filter repositories", not a search landmark | D5 |
| Result count | Announced, region present during loading | D5 |
| No-match state | Link to artifact search with the text | D6 |
| Type groups | Named groups, derived type explained | D7 |
| Row marks | Kept inside the row at 320px | D8 |

## Open questions

The defaults below apply until the maintainer answers. None blocks the implementation.

- **Q1. The word "Mixed".** Default: keep "Mixed" as the badge and chip text, in its own Derived group with the explanation of D7.
  Alternative: rename the derived type to "Local and proxy" in the badge, the chip and `docs/gitbook/reference/repository-types.md`.
  That is a visible rename of a value the server reports, and the Administration page keeps its own "Mixed" version policy.
- **Q2. Card placement below 1024px.** Default: below the list (D1). Alternative: above the list, with the URL wrap of D2 only.
- **Q3. Root heading.** Default: "Use a repository" with the caption of D3. The wording is the maintainer's.
- **Q4. The artifact card.** `ArtifactSnippet.vue` uses the same preformatted block for artifact coordinates. Default: not
  changed here. Alternative: the same D2 wrap in the same pull request.
- **Q5. Width checks.** Default: 320px, 1280px and 1920px checks run in `add-dashboard-e2e-tests` once it is on main. If it is
  not, the code pull request attaches one screenshot per width, taken by hand.
- **Q6. Staging build.** Default: the copy overlap is treated as already fixed on main (`fa0c8d1a1`). Before the code pull request
  is opened, the staging build is checked against main. If staging is on main, the overlap is a new defect and needs its own test.
- **Q7. Tag syntax on the Browse page.** Default: yes, the `tag:name` syntax applies (D5), so both repository pages behave the same.
