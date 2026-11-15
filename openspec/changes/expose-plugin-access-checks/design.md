# Design

Paths are relative to `reposilite-backend/src/main/kotlin/` unless stated otherwise. Line numbers are from `origin/main` at
`3614657d0`.

## Context

- **Visibility and the rules.** `RepositoryVisibility` is `PUBLIC`, `HIDDEN` and `PRIVATE` (`com/reposilite/maven/RepositoryVisibility.kt:24-28`).
  `RepositorySecurityProvider` (`:34`, `internal class`) decides:
  - repository read (`canAccessRepository`, `:36-40`): `PUBLIC` always; `HIDDEN` and `PRIVATE` need the `canSee` right on `/<repo>`;
  - repository listing (`canListRepository`, `:46-51`): `PUBLIC` always; `PRIVATE` needs `canSee`; `HIDDEN` needs `canListHidden`;
  - resource read (`canAccessResource`, `:56-61`): `PUBLIC` and `HIDDEN` always; `PRIVATE` needs `READ` through `hasPermissionTo`
    (`:101-109`). A missing token is `401`, a token without the right is `403`.
  So a `HIDDEN` repository is readable by anyone who knows the path and is not listed to anyone without `canListHidden`. The two questions
  must stay separate in the facade.
- **Facade entry points.** `MavenFacade` is a public class with an internal constructor (`maven/MavenFacade.kt:59`). It offers
  `findFile` and `findData` (`:98`, `:102`), which do not check rights themselves, `canAccessResource(accessToken, repository, gav)`
  (`:282`) and `findRepositories(accessToken)` (`:285`). The read route checks rights in the wrapper of `RepositoryService.kt:185`.
- **Viewer resolution.** A request becomes an `AccessTokenDto?` in the DSL (`shared/ContextDsl.kt:67-70`, `accessed { }`; `null` is anonymous).
  `AccessTokenDto` is in `token/api/AccessTokenDto.kt:24`. The identifier that the rules take is `AccessTokenIdentifier`
  (`token/AccessToken.kt:40`), and the subject of the identity model is produced from it by `AccessTokenFacade.subjectOf`
  (`token/AccessTokenFacade.kt:457`) and `AccessSubjectSource.subjectOf` (`identity/evaluation/AccessSubjectSource.kt:25`).
- **Search access.** `SearchAccessCompiler.compile(subject: AccessSubject?, repositories: Map<String, RepositoryVisibility>): SearchAccess`
  (`search/access/SearchAccessCompiler.kt:60`). `SearchAccess` is a public data class (`search/access/SearchAccess.kt:25`).
  `AccessSubject` is a public data class in `identity/model/IdentityModel.kt:150`.
- **Authorizer chain (open, PR 407 family).** The chain `AuthorizationChain` with the built-in authorizer is `internal` in
  `net.onelitefeather.ingot.identity.authorization`, and PRs 407, 411, 418 and 422 route token access, admin checks and permission checks
  through it. The public authorization SPI is a proposal for later (`add-authorization-spi`).
- **Canonical paths (accepted, PR 389 open).** `canonicalize-authorization-paths` decides that encoded slashes are refused with `400`, dot
  segments are normalised before a rule is judged, and no switch exists. The facade follows the same rule.
- **Plugin access to facades.** Extensions injects annotated fields by reflection (`plugin/Extensions.kt:44-51`). Facades are marker
  interfaces (`plugin/api/Facade.kt:19`).

## Goals and non-goals

Goals: one facade for the four answers; parity with the routes by test; no second rule; canonical paths; nothing secret on the surface.

Non-goals: a rule that a plugin adds; a token API; listing tokens; caching; changing the status codes of any route.

## Decisions

### D1: the surface

```kotlin
package net.onelitefeather.ingot.access

@ExperimentalIngotPluginApi
interface ViewerAccessFacade : Facade {
    fun viewerOf(token: AccessTokenDto?): Viewer     // null is anonymous
}

@ExperimentalIngotPluginApi
class Viewer internal constructor(/* identifier and subject, not public */) {
    fun canReadRepository(repository: String): Boolean
    fun canReadPath(repository: String, path: String): Boolean
    fun canListRepository(repository: String): Boolean
    fun listedRepositories(): Set<String>
    fun searchAccess(): SearchAccess
}
```

`Viewer` has no public constructor, no public property of the token or the subject, and no `toString` that shows either. Every method is
a question with a boolean or a set answer. Errors of the routes (`401`, `403`, `404`) are not exposed; a plugin that needs a status code
maps `false` itself.

### D2: parity with the routes

Each answer delegates to the function the route uses:

| Answer | Delegates to |
| --- | --- |
| `canReadRepository` | `RepositorySecurityProvider.canAccessRepository` |
| `canReadPath` | `RepositorySecurityProvider.canAccessResource`, mapped to `isSuccess` |
| `canListRepository` | `RepositorySecurityProvider.canListRepository` |
| `listedRepositories` | the repositories of `findRepositories` (`MavenFacade.kt:285`) |
| `searchAccess` | `SearchAccessCompiler.compile(subject, visibilities)` with the subject of `AccessTokenFacade.subjectOf` |

A parity test runs the route function and the facade method over a matrix: visibilities `PUBLIC`, `HIDDEN`, `PRIVATE`; viewers anonymous,
a token without rights, a token with the read right on the repository, a token with rights on another repository, and a session.
Any difference fails the test with the input in its message.

### D3: canonical paths

`canReadPath` canonicalizes its input with the function that `canonicalize-authorization-paths` introduces for the routes. If the input is
not canonical (an encoded slash, a dot segment, an empty segment, a leading slash that the canonical form does not allow), the answer is
`false`, and the test set of that change is the test set of this one. The facade never judges a path the routes would not judge in that
form. This change depends on that function (see the proposal, Order); it does not write its own.

### D4: no existence leak

For an unknown repository, a repository the viewer may not read and a repository that does not exist, every answer is the same `false`. A
plugin therefore cannot discover private repositories by asking.

### D5: the chain

The answers are computed by the current rules (D2). When the built-in authorizer of PR 407 is on `main`, a follow-up change proposed on
its own moves the same answers to `BuiltInAuthorizer` through the chain. The public surface does not change, and the parity test of this
change is the test that the follow-up must keep green. Keeping the switch out of this change means each change has one type. No plugin is
ever handed the chain, an authorizer or a decision.

### D6: what stays internal

Not public in this change: `AuthorizationChain`, `Authorizer`, `AuthorizerDecision`, `AuthorizationRequest`, `AccessTokenFacade`,
`AccessTokenIdentifier` as a value a plugin can construct, token secrets and hashes, the evaluator and the session store. The surface test
of the change asserts that no public member of `net.onelitefeather.ingot.access` has a type from those packages.

### D7: the search access value

`searchAccess()` returns the `SearchAccess` of `search/access/SearchAccess.kt:25`. It is the value the search engine takes as a parameter
(`ArtifactSearchEngine.facets(query, access)`, `ArtifactSearchEngine.kt:36`). Returning it makes `SearchAccess`, `RepositoryAccess` and the
`PathPredicate` hierarchy part of the plugin API surface. They are added to the allowlist of `add-plugin-api-artifact` with the experimental
marker, and the closure check of that change decides whether the hierarchy closes without further types (Q2).

### D8: operations and drop-in

No route, status code, configuration key or environment variable changes. Existing plugins compile without change. The facade is one more
facade in the plugin context; installations without a plugin never call it.

## Plugin API gaps closed and not closed

| Gap | Closed here | Note |
| --- | --- | --- |
| Viewer access value built in an undocumented, ungated class | yes | D1, D7 |
| Rules a plugin can ask without copying them | yes | D1, D2 |
| Plugin can read a search query engine | no | Q2 names the question; the query path is `SearchFacade.engine` |
| Plugin can add a rule | no | `add-authorization-spi`, not proposed here |

## Alternatives considered

- **Copy the rules into the plugin.** Rejected: drift is the failure this change prevents.
- **Expose `MavenFacade` whole.** Rejected: it has methods that read and write, and its internal constructor is the boundary.
- **Expose the authorizer chain.** Rejected: the chain is not a stable contract, and a plugin would then depend on the order of authorizers
  (`introduce-authorizer-chain`, chain order).
- **Return status codes.** Rejected: that exposes the difference between `401`, `403` and `404`, which is the existence leak of D4.

## Risks

- **Drift between the parity test and the routes.** Mitigation: the test calls the route functions, not copies of them.
- **Search access types grow the closure.** Mitigation: D7 and Q2 measure it before merge.
- **Merge order with the authorizer chain.** Mitigation: D5, a switch that changes no public type.

## Open questions (each has a default)

- **Q1. Where the viewer comes from.** Default: the plugin passes the `AccessTokenDto?` of `accessed { }` to `viewerOf`. Alternative: a
  request-scoped helper in the web facade that resolves the viewer once per request.
- **Q2. Read path of the search engine.** Default: this change exposes the access value only; the portal's query path is `SearchFacade.engine`,
  a public property of a facade whose constructor is internal, and its allowlisting is decided after the closure of D7 is measured.
  Alternative: a separate read-only search facade with only the query, facets and files methods.
- **Q3. Result type.** Default: `Boolean` and `Set<String>`. Alternative: a result type with a reason, which would reintroduce the status
  distinction of D4.
- **Q4. Hidden repositories in listings.** Default: `canListRepository` answers as the route does, so a `HIDDEN` repository is not listed
  without `canListHidden`.
- **Q5. Caching.** Default: none. Each call evaluates the rules, which is what the routes do.
