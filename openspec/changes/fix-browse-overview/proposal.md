# Proposal

## Why

The Browse page at `/` is the first thing a visitor sees, and it does two jobs at once: it lists the repositories, and it
shows the snippet a build tool needs to use one of them. Both jobs are hard to do on a wide screen today.

On a 1920px window the snippet card, "Repository details", takes a column of about 400px on the right. Its build snippet
(Maven, Gradle Kotlin, Gradle Groovy, SBT) is preformatted and never wraps, so a repository URL runs past the card and is
cut off mid-address with a horizontal scrollbar. A user cannot read the URL in full before copying it. On the root page the
card does not say which repository it is for: it is headed "Repository details" and shows a template with a literal
`<repository>` in the address and the instance id as the repository id. A user cannot tell that they must replace a
placeholder, or which repository the snippet describes.

The repository filter at the top of the list is styled and labelled like a global search: a large pill, a magnifier icon,
the words "Search by id, description or tag", and a search landmark. The rail already has a Search entry for artifacts. A
visitor who types an artifact name into the field gets "No repository matches these filters" and no way forward. The
Administration Repositories page uses the same filter under the label "Filter repositories", so the two pages disagree. The
filter also ignores the `tag:name` syntax that the Administration page describes in its supporting text.

The type chips offer "Hosted, Proxy, Group, Mixed". Hosted, Proxy and Group are types a maintainer declares. "Mixed" is a
type the server derives for a repository that has no declared type and proxies both local and remote repositories. The same
word also names a version policy on the Administration Repositories page. A chip beside the three declared types reads as a
fourth kind of repository, and an operator who looks for a Mixed version policy finds the wrong thing.

On a phone the snippet card comes before the list, so the first screen shows a build snippet above the repositories. On a
narrow screen the trailing marks of a row (the type badge, the visibility label and the chevron) have little room at 320px
and are at risk of being pushed out of the row.

Facts checked against the worktree (origin/main after `dcd8294a6`). The file and line references are in `design.md`:

- The page at `/` is labelled Browse and lists the repositories through `RepositoryOverview.vue`. No openspec change and no
  baseline spec describes this page (`openspec/specs` does not exist), so the requirements of this change are ADDED under the
  capability `browse-overview`. Nothing is MODIFIED.
- The snippet card's copy action already has a row of its own above the code, and a test pins that (commit `fa0c8d1a1`,
  `SnippetsCard.test.js:124`). The overlap reported from staging is therefore not reproduced on main, and the staging build is
  probably older. The cut-off is real: the same commit made the snippet block preformatted and scrolling sideways on purpose.

## What Changes

- **Snippet readable at every width.** The repository URL inside the snippet wraps at its own characters when the card is
  narrow, so no URL needs a horizontal scrollbar at 320px, 1280px or 1920px. Only the URL token may wrap. Indentation and
  tags stay as they are, and the copied text contains no added characters or line breaks.
- **Snippet placement.** Below 1024px the snippet card moves below the repository list. From 1024px it keeps its column on
  the right, at its current width.
- **Heading names the repository.** On a repository path the heading reads "Repository details: <id>" with the repository the
  snippet is for. On the root page the heading reads "Use a repository", with one line that says what to replace.
- **Copy feedback for screen readers.** The status line that reports "Copied", or that the text is selected, is one live
  region that exists before the message changes, so a screen reader announces it. The copy action keeps its place above the
  code.
- **Repository filter, not a global search.** The field is labelled "Filter repositories", with the outlined field and the
  filter icon of the Administration Repositories page, and the supporting text "Search by id, description or tag. Use
  tag:name for one tag.". It is not a search landmark. It reads the `tag:name` syntax the same way that page does. The
  result count is announced.
- **Link to artifact search.** When the typed text matches no repository, the empty state offers a link "Search artifacts
  for <text>" to the Search page with the text already entered. "Clear filters" stays.
- **Type filter explained.** The declared types Hosted, Proxy and Group stay as chips in one group. Mixed moves into its own
  group, "Derived type", with one line of text that says what it means. Its value in the address stays `mixed`.
- **Rows at 320px.** Below 600px the type badge and the visibility label move to a line below the repository id, and the
  avatar, the id and the chevron stay on the first line. From 600px the row is as it is today.

The address of every filter (the `q`, `tag` and `type` parameters), the API calls and the stored data do not change.

### Shipping

One code pull request, `fix(ui): make the browse snippet readable and scope the repository filter`. Its commits use the
types `test` and `fix` with the scope `ui`, one type per commit. The proposal pull request is
`docs(openspec): propose fix-browse-overview`. It contains only the files under `openspec/changes/fix-browse-overview/`,
and its tasks stay unchecked.

### Operator impact

Nothing to change. No configuration key, environment variable, volume, container path, port, manifest, route or API changes.
This change is not BREAKING and carries no `!` and no `BREAKING CHANGE:` footer.

### Out of scope

- The artifact search page itself (`components/search/SearchView.vue`). The new link only opens it with a query.
- The Administration layout, the rail and the navigation registry. Those belong to `fix-administration-navigation` and to the
  navigation changes already on main. This change touches only the Browse page and its snippet card.
- The Administration Repositories page filter. It already has the label and the behaviour this change asks for.
- Renaming the derived type to "Local and proxy" everywhere. The default keeps the word Mixed (open question Q1).
- The artifact details card (`ArtifactSnippet.vue`) on deeper paths, which has the same preformatted block (open question Q4).
- Visual width checks in a real browser. They are covered by `add-dashboard-e2e-tests` (see tasks), which is not on main yet.
  Unit tests here cover the classes, labels, order and states that the code controls.
- Server, API, configuration, and documentation beyond the Browse page.

### Dependencies

- `add-repositories-admin-page` (on main as `dcd8294a6`) gave the page its label "Browse". No requirement of that change is
  modified here.
- `add-dashboard-e2e-tests` covers the 320px, 1280px and 1920px checks. It is not on origin/main; a local branch
  `docs/openspec-add-dashboard-e2e-tests` exists in another worktree. If it does not land, the width checks are a screenshot
  attached to the code pull request (open question Q5).

## Capabilities

### New Capabilities

- `browse-overview`: the repository list and the snippet card of the Browse page at `/` and at `/browse/<repository>`: the
  snippet layout and wrapping, the placement, the heading, the copy feedback, the repository filter, the type filter and the
  compact row.
