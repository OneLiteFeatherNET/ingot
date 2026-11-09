# Proposal

## Why

The phrase statistics endpoint takes a `{limit}` path segment and then ignores it. `StatisticsEndpoint.kt:52` on origin/main
always asks the facade for one record, so `GET /api/statistics/resolved/phrase/25/releases/com/example` answers with at most
one entry. A caller who asks for the top entries gets the first one and no hint that the rest were dropped. The value is
documented as "Amount of records to find", so the response contradicts the request.

Checked against origin/main (`c0f01846`) and upstream `dzikoysk/reposilite` (`b8e481ec`, 3.6.7):

- The hard-coded `1` is not deliberate. It came in with the Location-based API (`22bef555`, GH-927). Upstream replaced it in
  the dashboard work (`a6e0728e`, GH-2620): the route parses `{limit}`, answers `400` outside `1..MAX_PAGE_SIZE`, and passes
  the caller's token to the facade.
- The bundled dashboard does not call this endpoint. `reposilite-frontend/src/store/client.js` uses only
  `/api/statistics/resolved/all` and `/api/statistics/resolved/entries`, on both refs. No caller depends on getting one entry.
- Honouring the limit alone would widen a gap that already exists. The endpoint checks read access on the phrase path only,
  and the query matches any path that contains the phrase. Today a caller can see the sum and the top entry for paths it
  cannot read. With a larger limit it would see more of them. Upstream fixes this with a visibility filter that the
  origin/main code does not have, so this change brings the filter too.

## What Changes

- **Honour `{limit}`.** The endpoint passes the parsed limit on. The response holds at most that many entries, highest
  resolved count first. `sum` is the sum of the entries returned. A limit of `1` answers exactly as today.
- **Refuse invalid limits.** A `{limit}` that is not an integer, or is outside `1..100` (`MAX_PAGE_SIZE`), answers `400`
  with the `ErrorResponse` shape, and no lookup runs.
- **Filter before the limit.** For a token without the manager area, the lookup returns only entries under paths that the
  token may read. The filter applies in the query before the limit, so a readable entry is not pushed out by an unreadable
  one. Managers see all entries, as today. The existing `401` and `403` answers stay.
- **Documentation.** `docs/gitbook/reference/http-api.md` describes the limit, the `400` and the visibility rule.
  `docs/gitbook/reference/upgrade-changes.md` lists the invalid-limit change.

Design decision D1 in `design.md` explains why the filter runs in the query and not on the returned list.

### Out of scope

- Pagination with an offset or a `next` token for the phrase endpoint. The path shape stays as it is; the paged variant is
  `/api/statistics/resolved/entries`, which already exists.
- Changing the `/entries`, `/all` or `/unique` endpoints.
- Changing how resolved counts are recorded or aggregated.
- The dashboard. It does not call this endpoint, so the change does not touch `reposilite-frontend`.
- Other statistics filters, such as the one an upstream date range needs.

## Capabilities

### New Capabilities

- `statistics-phrase-lookup`: the phrase endpoint's limit, its validation and the visibility of its results.

## Impact

- **Type and scope.** The change ships under `fix(statistics)`. The pull request title, squash-merged onto main, is:

  `fix(statistics): honour the limit of the phrase statistics endpoint`

- **Breaking marker.** No breaking change for operators. No configuration key, environment variable, volume, manifest or
  plugin has to change. A Reposilite installation pointed at Ingot needs no edit, and Maven, Gradle and sbt clients are not
  affected, because they never call this endpoint.
- **Behaviour that changes for API clients.** A request with `limit` above `1` returns more entries, and a `sum` that covers
  them. A request with a `limit` that is not a number, or is `0` or above `100`, gets `400` where it got data before. A
  client that sends `limit=1` sees no difference. A client that can read only some paths sees fewer entries than an
  unrestricted lookup would have shown, which is the intent.
- **Code.** `reposilite-backend` (statistics: endpoint, facade, repository, tests) and `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate fix-statistics-limit --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-statistics-limit` prints nothing.
