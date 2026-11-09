# Design

## D1. Visibility filter in the query, before the limit

**Decision.** The endpoint computes the caller's readable path prefixes (none for a manager, which means no filter) and
passes them to the repository. The repository adds them to the `WHERE` clause of `findResolvedRequestsByPhrase`, next to the
`gav like` condition, and only then applies `ORDER BY count DESC` and `LIMIT`.

**Why.** The limit answers "the top N entries this caller may see". Two other shapes fail that:

- Filtering the returned list after `LIMIT` can return fewer than N entries while readable entries exist further down the
  ranking. A caller with `limit=1` could get nothing for a path it can read.
- Fetching `limit + k` rows and filtering in memory bounds the cost only by a guess about how many rows are unreadable.

Filtering in the query keeps both the count and the order exact, at the cost of one more predicate on an indexed join.

**Prefix rule.** Reuse the rule that upstream applies: a READ route on `/<repository>/<prefix>` grants the GAV prefix
`<prefix>`, and a route on the repository root grants everything. An entry is visible when its GAV starts with one of the
prefixes. A caller with no READ route on the phrase path keeps the current `403`.

**Not chosen.** Leaving the lookup unfiltered and only honouring the limit. That is the smallest diff, but it exposes more
unreadable entries with each increase of the limit, so it stays out.

**Consequence for tests.** One unit test each for the query with and without prefixes, and one integration test where
`limit=1` and the highest-count entry is unreadable: the readable entry is returned, not an empty list.
