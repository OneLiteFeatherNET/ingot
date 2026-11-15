# Proposal

## Maintainer decisions

1. **Breaking or warn first?** Decided: always enforced, no switch. Verification confirmed that a deny rule could be judged against a different spelling of the path than the one stored, so a `warn` default would leave that open. No released version is affected, so the title stays `fix(maven)` without `!`.
2. **Does the security impact hold?** Decided: yes, on unreleased builds that carry permission node denials. Legacy allow-prefix routes are not affected.
3. **Which switch and which values?** Decided: none. `INGOT_AUTHORIZATION_PATHS` is not added, so the protection cannot be turned off.
4. **Is an encoded slash refused or decoded?** Decided: refused with 400.

## Why

A file is uploaded to a repository path, and the access check judges the URL as the client wrote it. A read of the same file is judged on the normalized location. Encoded slashes, dot segments and doubled slashes can therefore be judged differently on the two sides. The legacy route rule, which matches prefixes case-insensitively with no segment boundary, sees whichever string it is handed, so the same request can match a route on one side and not on the other.

Verified in code:

- Uploads and deletes call `authorized {}`, which checks `ctx.uri()`, the raw `path()` (`RepositoryRouteDispatcher.kt:79-93`, `ContextDsl.kt:84`, `JavalinExtensions.kt:182-183`). The location is normalized only afterwards (`RepositoryRouteDispatcher.kt:98-100`).
- Reads and metadata check `"/${repository.name}/$gav"`, where `gav` is a `Location` (`RepositorySecurityProvider.kt:104`).
- The legacy route rule is `toPath.startsWith(path, ignoreCase = true)` (`token/Route.kt`).
- Artifact nodes compare normalized segments (`NodePattern.kt`).

## What Changes

- One canonicalizer is introduced for the path that authorization sees. It percent-decodes once, rejects dot segments, encoded slashes, backslashes, NUL and control characters, collapses repeated slashes and splits the path into repository and location.
- Uploads and deletes are judged by the canonical path, and the same repository and location are handed to storage. This is always on.
- Malformed paths are refused with 400 before authorization.
- Legacy route matching sees the canonical string.

### Out of scope

- The read path, which is already normalized.
- The PUBLIC and HIDDEN read gap, where read restrictions are ignored (`RepositorySecurityProvider.kt:37-38`, `:58-59`). It is a separate change.
- REST and dashboard upload endpoints, proxy caching and `/check`. The inventory task lists which of them judge the raw path. Those that do are listed in the pull request as follow-up unless they are a one-line change.
- The rule that `Location.of` rejects any two or more consecutive dots, even inside a name such as `a..b`. It stays as it is.

## Capabilities

### New Capabilities

- `maven-path-canonicalization`: the canonical path used by authorization, always enforced for writes, with no switch.

## Impact

- **Operators:** no configuration is needed and none is added. Paths without escapes, dot segments or doubled slashes are judged and stored exactly as before. An escaped spelling of an allowed path is now accepted and stored at its decoded location instead of answered with 403. Ambiguous encodings are answered with 400.
- **Code:** `reposilite-backend`, in the repository route dispatcher and a new canonicalizer.
- **Commits:** `fix(maven)` for the canonicalizer, its tests and the dispatcher. Pull request title, squash-merged onto main:

  `fix(maven): canonicalize repository paths before authorization`
