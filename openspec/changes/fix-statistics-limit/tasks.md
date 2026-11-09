# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `statistics`. Tests are written
first and seen to fail before the production code changes. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock (any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary
directory per test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never
the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-statistics-limit`: this change and nothing else. Verify:
  `openspec validate fix-statistics-limit --strict` passes and the dash grep from the proposal prints nothing.

## 2. Phrase lookup limit and visibility

- [ ] 2.1 `test(statistics)`: write failing tests. In `StatisticsFacadeTest` (in-memory repository): a limit of two returns the
  two highest entries and their sum; a limit of one with an unreadable highest entry returns the readable one; a prefix
  filter excludes `x/com/example/b`; no prefixes returns every entry; a limit of 0 and of 101 is refused. In the integration
  test `StatisticsIntegrationTest`: `phrase/2` returns two entries; `phrase/0`, `phrase/101` and `phrase/many` answer `400`;
  a token limited to `/releases/com/example` sees only its entries; a manager sees all; a token without read access answers
  `403`. Verify: they fail before 2.2, and the failure names the missing limit or the missing filter.
- [ ] 2.2 `fix(statistics)`: port upstream `dzikoysk/reposilite@a6e0728e` (GH-2620) with `git cherry-pick -x`, keeping its
  author, and reword the message to this type and scope with a `Ported from dzikoysk/reposilite@a6e0728e` footer. It parses
  `{limit}`, answers `400` outside `1..100` and filters by the caller's token. Where it differs from 2.1 (the prefix predicate
  must sit in the query before `ORDER BY` and `LIMIT`, a manager sees all), adapt it in a second `fix(statistics)` commit. Verify: the tests of 2.1 pass, and the existing
  `should return registered number of endpoint calls` test and `StatisticsFacadeTest` pass unedited.
- [ ] 2.3 `docs(gitbook)`: update the row for `/api/statistics/resolved/phrase/{limit}/{repository}/<gav>` in
  `docs/gitbook/reference/http-api.md` (limit, `400`, visibility), and list the invalid-limit change in
  `docs/gitbook/reference/upgrade-changes.md`. Verify: the page renders, and the dash grep prints nothing.

## 3. Pull request

- [ ] 3.1 Open the code pull request with the title `fix(statistics): honour the limit of the phrase statistics endpoint`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the reviewer or operator
  (a limit that was accepted and then ignored, and the visibility that had to come with it) without class names. It carries
  no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook
  accepts the title and every commit, and the body has no em or en dash.
