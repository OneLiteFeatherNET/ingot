# Design

## Context

`Context.error(ErrorResponse)` in `shared/extensions/JavalinExtensions.kt` is the one writer for `ErrorResponse` answers.
It picks `application/problem+json` or `application/json` from the request's `Accept` header and writes no caching headers.
Repository errors reach it as well, through `ReposiliteRouting`, except the Maven `404` page, which `MavenEndpoints.kt`
writes as HTML.

## Decisions

### D1. Vary: Accept on every ErrorResponse, not only on problem answers

The header is added whenever the answer is negotiated, not only when the client gets problem details. If it were added only
when `prefersProblemJson` is true, an `application/json` answer would be stored without `Vary`, and a later client that asks
for problem details could be served that stored answer. The check that decides the content type is the same check that
needs the header, so both live in `Context.error`.

### D2. One generic Vary merge, shared with the dashboard

`DashboardRouteDecision.varyHeader` already keeps earlier values, avoids duplicates in any letter case and keeps `*`. Its
only specific part is the list of two fetch metadata headers. The merge moves to a shared function that takes the names to
add, and `DashboardRouteDecision` calls it with `Sec-Fetch-Mode` and `Sec-Fetch-Dest`. The dashboard result does not change,
and `DashboardRouteDecisionTest` keeps passing as the proof.

### D3. Cache-Control decided by path, in Context.error

The header is decided in `Context.error(ErrorResponse)` by the request path, not in the Maven endpoint. Refusals on repository
paths (`401`, `403`) also come through `Context.error`, so a mark placed only in `MavenEndpoints.findFile` would miss them.
Paths under `/api` are left alone because `bypassExternalCache` already decides their caching and an operator can turn it
off. Any other error path gets `no-cache` when the response has no `Cache-Control` yet. The Maven `404` HTML branch gets the
same value at its own call site, since it does not go through `Context.error`.

### D4. The value is `no-cache`, not `no-cache, no-store, max-age=0`

`no-cache` lets a cache keep the answer but forces it to revalidate before use, which is what a miss needs: a later upload
becomes visible at once. `no-store` would also stop caches from keeping the answer at all, and that adds nothing for an
error. The repository file headers use the stricter value for files that may change, and this change does not reuse it.

## Not decided here

- Whether Javalin's own default `404` for a path that no route matches should get `Vary` and `no-cache`. Javalin writes that
  answer itself, so it needs a separate change.
