---
description: Reference for ViewerAccessFacade and Viewer, the experimental plugin API that answers who may read, list and search a repository with the same decisions as the routes.
---

# Viewer access checks

A plugin that serves repository data on a route of its own has to decide what the caller may see. `ViewerAccessFacade` gives
the plugin the answers of the routes for one caller, so the plugin does not copy the rules and does not drift from them. The
plugin asks a question and gets a `Boolean`, a set of names or a search access value. It never gets a rule, a status code or the
token itself.

The API is experimental. Every type on this page is marked `@ExperimentalIngotPluginApi`
(`net.onelitefeather.ingot.plugin`), and a Kotlin plugin opts in with `@OptIn(ExperimentalIngotPluginApi::class)`. The types are
in the slim plugin API artifact, listed in
[plugin-api-classes.txt](https://github.com/OneLiteFeatherNET/ingot/blob/main/reposilite-backend/plugin-api-classes.txt). The
[consumer fixture](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-test/plugin-api-consumer) shows a plugin that
compiles against that artifact alone. For coordinates and the lifecycle, see [Plugin API](plugin-api.md).

## Get a viewer

The built-in `maven` plugin registers the facade. Declare `maven` in `dependencies` of `@Plugin`, then turn the token of a request
into a `Viewer`. Pass the token that `accessed { }` hands to its block. `null` is an anonymous caller.

Inside `initialize()`:

```kotlin
val access = facade<ViewerAccessFacade>()

event { event: RoutingSetupEvent ->
    event.register(
        ReposiliteRoute<Set<String>>("/api/example/repositories", GET) {
            accessed {
                response = access.viewerOf(this).listedRepositories().asSuccess()
            }
        }
    )
}
```

Add the imports, and `@OptIn(ExperimentalIngotPluginApi::class)` on the plugin class:

```kotlin
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.community.routing.Route.GET
import net.onelitefeather.ingot.access.ViewerAccessFacade
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import panda.std.asSuccess
```

In Java, get the facade with `extensions().facade(ViewerAccessFacade.class)` and call `viewerOf(token)` with the token or `null`.

| Type (package `net.onelitefeather.ingot.access`) | Member | Meaning |
| --- | --- | --- |
| `ViewerAccessFacade` | `viewerOf(token: AccessTokenDto?): Viewer` | The viewer of a request. `AccessTokenDto` is in `com.reposilite.token.api`. |
| `Viewer` | see [The answers](#the-answers) | The access decisions of one caller. It has no public constructor. |

A `Viewer` caches nothing. Every call evaluates the rules again, as a route does, so a changed token or repository setting
takes effect on the next call.

## The answers

Each answer is computed by the function that the matching route uses. A parity test compares each answer with that function for
the visibilities `PUBLIC`, `HIDDEN` and `PRIVATE` and for anonymous callers, tokens without rights, tokens with the read right,
tokens with rights on another repository and sessions.

| Method | Answers | Same decision as |
| --- | --- | --- |
| `canReadRepository(repository: String): Boolean` | Whether the repository appears to the caller in the root listing at `/`. | The root listing |
| `canReadPath(repository: String, path: String): Boolean` | Whether the caller may read the file at `path` in the repository. | The resource read route |
| `canListRepository(repository: String): Boolean` | Whether the repository appears in the repository list for the caller. | The repository list |
| `listedRepositories(): Set<String>` | The names of the repositories in the repository list for the caller. | The repository list |
| `searchAccess(): SearchAccess` | The rows of the artifact search index the caller may read. | The search routes |

The rules behind the answers are those of [Visibility](../repositories/repository-settings.md#visibility):

| Visibility | `canReadRepository` | `canReadPath` | `canListRepository` |
| --- | --- | --- | --- |
| `PUBLIC` | Always | Always | Always |
| `HIDDEN` | The caller can see the repository | Always, as for anyone who knows the path | Only with the right to list hidden repositories, see [Artifact permission nodes](../identity/artifact-permission-nodes.md) |
| `PRIVATE` | The caller can see the repository | The caller holds the read right on the path | The caller can see the repository |

Reading and listing are separate questions on purpose. A caller can read a `HIDDEN` repository without seeing it in the list,
so `canReadRepository` is `true` while `canListRepository` is `false`. `listedRepositories()` and `canListRepository` always
agree: every name in the set is one that `canListRepository` answers `true` for.

`canReadPath` is the decision for reading a file. It is not the decision for opening a directory listing, which in a `HIDDEN` or
`PRIVATE` repository also opens a directory on the way to a part the caller can read.

## The path rule

`canReadPath` judges a path only in its canonical form, the form the routes judge. `path` is the location inside the repository,
without the repository name and without a leading slash, for example `com/example/app/1.0/app-1.0.jar`. The answer is `false`
for a path that:

* starts with a slash, or has an empty segment such as `com//example`,
* contains an escaped slash (`%2F` or `%2f`),
* contains a `.` or `..` segment, also when it is percent-encoded (`%2e%2e`),
* contains a backslash or a control character, or a malformed escape.

The path is percent-decoded once, as a request path is. The repository name must not contain a slash. A path that leaves the
repository it names, such as `../internal/...`, is refused, so it never reaches the rule of another repository.

## The same false for every refusal

For a repository that does not exist, a repository the caller may not read and a path the caller may not read, every answer is
the same `false`. A plugin cannot tell the three apart, so it cannot discover private repositories by asking.

The routes answer such requests with `401`, `403` or `404`. The facade does not expose these codes, because the difference
between them shows whether a repository exists. A plugin that needs a status code maps `false` to one itself, and `404` keeps
the existence hidden.

## The search access value

`searchAccess()` returns the value the search engine filters its rows with. It is compiled from the same subject and the same
repository visibilities as the search routes, so a filter with it keeps the rows a search route returns to the caller.

| Type (package `net.onelitefeather.ingot.search.access`) | Meaning |
| --- | --- |
| `SearchAccess(repositories: List<RepositoryAccess>)` | What the caller may see in the search index. A repository missing from the list is not visible at all. `matches(repository, path)` answers for one row. `SearchAccess.NONE` sees nothing. |
| `RepositoryAccess(repository: String, path: PathPredicate)` | The rows of one repository, as a condition on the path inside it. |
| `PathPredicate` | A sealed condition on a path: `All`, `Never`, `Exact(prefix)`, `Subtree(segments)`, `Legacy(prefix)`, `Conjunction(parts)` and `Precedence(rules)`. |
| `PrecedenceRule(condition: PathPredicate, allowed: Boolean)` | One step of `Precedence`. The first rule whose condition holds decides, and a path no condition holds for is denied. |

The facade exposes the value only. The search engine itself, and the query path of `SearchFacade`, are not part of the plugin
API.

## What stays internal

The facade answers questions. It does not hand out the rules behind them. These stay internal and are not reachable through any
public member of `net.onelitefeather.ingot.access`:

* the authorization chain, its authorizers, their decisions and their requests (`AuthorizationChain`, `Authorizer`,
  `AuthorizerDecision`, `AuthorizationRequest`),
* `AccessTokenIdentifier` as a value a plugin can construct, and the identifier and subject a `Viewer` holds,
* token secrets and hashes. A `Viewer` shows neither the secret nor the identifier of its token, also not in `toString`,
* the permission evaluator and the session store,
* the status codes of the routes, as described above.

A plugin cannot add a rule through this API. A test of the access package fails when a public member names one of the internal
types above, and an architecture test keeps the package free of the authorization package.

## Compatibility

The facade adds nothing to a route, a status code, a configuration key or an environment variable. A server without a plugin
that uses it behaves as before, and existing plugins compile without change.
