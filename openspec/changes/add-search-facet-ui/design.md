# Design

Paths are relative to `reposilite-frontend/src/components/search/`.

## Context

`SearchFilters.vue` renders fixed groups from `Facets` (`searchResult.ts`) and keeps a chosen value visible with `optionsOf`.
`searchQuery.ts` maps filters to the address and the request. The attribute facets arrive as `facets.attributes`, a map from key to
buckets of `{ value, count }`, only when the request names facet keys.

## Decisions

### D1. Groups come from the response

The view renders a group for each key present in `facets.attributes`, in the order the response gives, with the key as label. A
plugin-supplied display label is used when the API provides one, else the key.

### D2. One query parameter per key, like the API

The address carries `attribute=<key>:<value>` repeated, as the request does, so the address and the request share one parser.

### D3. Selected values stay

A selected value missing from the buckets is appended with count zero, using the existing `optionsOf` rule.

### D4. Refusal means plain search

`unsupported-search-capability` and `undeclared-attribute` problem responses make the view drop the attribute filters from the address
and request again without them.

## Open questions

- **Q1.** Settled (maintainer decision 267): the view learns which keys to ask facets for from a read-only list of facetable keys with
  labels, which joins `add-search-attribute-facets` as an amendment (D9 there, group 7 of its tasks). The view depends on that endpoint.
