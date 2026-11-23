# Proposal

## Why

`add-search-attribute-facets` lets the search API filter on declared plugin attributes and return counts per value. The dashboard search
view (`reposilite-frontend/src/components/search/SearchView.vue`) still offers only the fixed facets: repository, extension, classifier,
origin and snapshot (`SearchFilters.vue`). A visitor cannot reach "Paper plugins for 1.21" from the dashboard, although the API can
answer it.

## What Changes

- The search view shows one filter group per declared attribute that is facetable, with the count beside each value, built from
  `facets.attributes` of the response.
- A chosen value narrows the results, and the choice lives in the page address like the existing filters, so a search can be shared.
- A chosen value never disappears from its own group, even when its count is zero (the rule the existing groups follow).
- When the backend refuses a request because it lacks a capability, the view hides the attribute groups and shows the plain search, not
  an error.
- The view is unchanged for instances where no enricher declares an attribute.

### Out of scope

- Sorting by an attribute (the API has it; a later change may add a control).
- Any change to the API, the existing filters or their order.
- A new configuration key, environment variable or route.

## Capabilities

### New Capabilities

- `search-facet-ui`: the attribute filter groups of the search view.

## Impact

- **Type and scope.** The change ships under `feat(ui)`. Its commits are `test(ui)` and `feat(ui)`, one type each. The pull request title
  is `feat(ui): filter the search by plugin attributes`. It is a visible change, so the pull request carries the label `ci:e2e`.
- **Breaking marker.** None. No API, configuration, volume or image changes.
- **Order.** Depends on `add-search-attribute-facets` (the code of its first pull request merged).

## Verification

- Component tests with the existing test setup: groups render from a fixture, a chosen zero-count value stays, the address round trips.
- One browser test of the flow, with screenshots at 320, 600 and 1440 pixels.
- `openspec validate add-search-facet-ui --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-search-facet-ui` prints nothing.
