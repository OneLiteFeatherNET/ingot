# Proposal

## Why

A plugin that serves pages to readers has to answer one question per request: may this viewer read this repository, this file, or list
this repository? The core answers it in several places, and a plugin can reach none of them with a stable API. The rules live in
`RepositorySecurityProvider` (`com/reposilite/maven/RepositorySecurityProvider.kt:34`, an `internal` class with `canAccessRepository`,
`canListRepository` and `canAccessResource`, lines 36 to 61). `MavenFacade` answers the same questions (`canAccessResource`,
`findRepositories`, `MavenFacade.kt:282-285`) but is constructed internally. The search access value that the portal passes with each
query is built by `SearchAccessCompiler.compile(subject, repositories)` (`net/onelitefeather/ingot/search/access/SearchAccessCompiler.kt:60`),
an `object` that is public in bytecode but documented for no one, and whose subject comes from `AccessSubjectSource`.

A plugin that copies these rules will drift from them. A private repository would then show in a list the core hides, or a guessed URL
would answer differently from the core. The Gradle and Minecraft portal designs both require the listing, the counts and the detail
pages to use exactly the core's decision. This change gives the plugin one facade that makes those decisions and nothing else.

The portal plans name this gap as G6 (Gradle) and G7 (Minecraft). The numbers differ between the two tables; the name is the same.

## What Changes

- A public, experimental facade `ViewerAccessFacade` in `net.onelitefeather.ingot.access`, marked `@ExperimentalIngotPluginApi`, which
  turns the token of a request into a `Viewer` value.
- The `Viewer` answers four questions and nothing else: may read a repository, may read a path in a repository, may list a repository,
  which repositories it may list, and the search access value for queries.
- The answers come from the core's existing rules through the same internal functions the routes use. A parity test compares every
  answer with the route's answer over a matrix of visibilities and viewers, so the two cannot drift.
- Paths are canonicalized the same way the routes will canonicalize them after `canonicalize-authorization-paths`. A path that is not
  canonical is refused, and the refusal is `false`.
- The answers use the current rules. A follow-up change, proposed when the built-in authorizer of the authorizer chain
  (`introduce-authorizer-chain`, PR 407) is on `main`, moves them to that authorizer. The public surface does not change, so no second
  rule is written here.
- Nothing of the chain, the evaluator or the token store is public. The viewer does not reveal whether a token exists.

### Out of scope

- Public authorization SPI (`com.reposilite.plugin.api.authorization`, `add-authorization-spi`). This change exposes answers, not rules,
  and no plugin can add a rule.
- Changing any rule, any route, any status code or any repository visibility.
- Writing, managing or listing tokens from a plugin.
- The search query engine itself beyond the access value (Q2).
- Caching of answers across requests.

## Capabilities

### New Capabilities

- `plugin-access-checks`: the facade, the viewer value, the four answers, the canonical path rule, the parity rule and the list of what
  stays internal.

## Impact

- **Type and scope.** The change ships under `feat(maven)`, as the portal tables name it. Its commits are `test(maven)`, `feat(maven)` and
  `docs(maven)`, one type each. The pull request titles are:

  `feat(maven): expose viewer aware access checks to plugins` (the facade, the viewer, the parity tests)

  `docs(maven): describe viewer aware access checks for plugins` (the plugin API reference)

- **Breaking marker.** No breaking change. No rule, route, status or configuration changes. No configuration key, environment variable,
  volume, manifest, image or plugin has to change.
- **Binary surface.** New public types in `net.onelitefeather.ingot.access`, marked experimental. The facade is registered like the other
  facades. The plugin API allowlist of `add-plugin-api-artifact` gains the new classes, and the search access types they return (Q2).
- **Order.** Depends on `add-plugin-api-artifact` for the allowlist. Depends on `canonicalize-authorization-paths` for the path rule: that
  change is accepted in openspec and implemented in its own pull request, and this change must not canonicalize differently. Coordinates
  with the authorizer chain (PR 407, and 411, 418 and 422 which touch the same token and listing files): this change delegates to the
  current rules, and the switch to the chain is a follow-up change with its own proposal, so that no change mixes types. Independent
  of `add-artifact-enrichment-hook`, `add-search-document-attributes` and `add-search-attribute-facets`.

## Verification

- Parity tests: for each visibility (PUBLIC, HIDDEN, PRIVATE) and each viewer (anonymous, a token without the right, a token with the right,
  a token of another repository, a session), the facade answers equal the answers of the route functions for the same input.
- Path tests: `..`, empty segments, encoded slashes and dot segments are refused with `false`; canonical paths are answered.
- A test that the public surface of `net.onelitefeather.ingot.access` contains no type of the authorization package and no token secret.
- `openspec validate expose-plugin-access-checks --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/expose-plugin-access-checks` prints nothing.
