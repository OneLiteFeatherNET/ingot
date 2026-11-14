# Proposal

## Open maintainer questions

1. **Breaking or warn first?** Default: warn first. `INGOT_AUTHORIZATION_PATHS` defaults to `warn`: decisions stay on the raw path exactly as today, and every request whose raw and canonical decisions differ is logged at WARN and counted. The commit is `fix(maven)` without `!`, which keeps the drop-in rule. The alternative is `enforce` as the default, traversal sequences refused before authorization, and the title `fix(maven)!: canonicalize repository paths before authorization` with a `BREAKING CHANGE:` footer. Trade-off: warn first leaves the upload check on the raw path until an operator sets `enforce`. That is the open risk, and the maintainer weighs it against the drop-in rule.
2. **Does the security impact hold?** Default: unproven until the verify tasks run. `Location.of` already rejects dot segments and illegal characters (`Location.kt`), but it runs after the upload check (`RepositoryRouteDispatcher.kt:79-85`). Whether an encoded form reaches storage with a meaning the check did not see depends on how Javalin decodes path parameters, and that is a verify task. If the impact is confirmed, the maintainer may prefer the breaking title.
3. **Which switch and which values?** Default: `INGOT_AUTHORIZATION_PATHS` with `warn`, `enforce` and `off`, read with the `REPOSILITE_AUTHORIZATION_PATHS` fallback as CLAUDE.md requires. `off` runs no comparison at all.
4. **Is an encoded slash refused or decoded?** Default: in `enforce`, `%2F` inside a segment is refused with 400, as the design says. A client that sends it today is refused only after the switch is set. Alternative: decode it into a separator, which keeps such clients working but changes which path is authorized.

## Why

A file is uploaded to a repository path, and the access check judges the URL as the client wrote it. A read of the same file is judged on the normalized location. Encoded slashes, dot segments and doubled slashes can therefore be judged differently on the two sides. The legacy route rule, which matches prefixes case-insensitively with no segment boundary, sees whichever string it is handed, so the same request can match a route on one side and not on the other.

Verified in code:

- Uploads and deletes call `authorized {}`, which checks `ctx.uri()`, the raw `path()` (`RepositoryRouteDispatcher.kt:79-93`, `ContextDsl.kt:84`, `JavalinExtensions.kt:182-183`). The location is normalized only afterwards (`RepositoryRouteDispatcher.kt:98-100`).
- Reads and metadata check `"/${repository.name}/$gav"`, where `gav` is a `Location` (`RepositorySecurityProvider.kt:104`).
- The legacy route rule is `toPath.startsWith(path, ignoreCase = true)` (`token/Route.kt`).
- Artifact nodes compare normalized segments (`NodePattern.kt`).

## What Changes

- One canonicalizer is introduced for the path that authorization sees. It percent-decodes once, rejects dot segments, encoded slashes and NUL, collapses repeated slashes and splits the path into repository and location.
- Uploads, deletes and metadata writes are judged by the canonical path when the switch is `enforce`. In `warn`, they keep the raw decision, and a difference is logged and counted.
- Traversal-looking paths are refused with 400 before authorization in `enforce`.
- Legacy route matching sees the string the decision uses.
- The corpus rows of change `record-decision-corpus` that name this change are updated in this change's commit.

### Out of scope

- The read path, which is already normalized.
- The PUBLIC and HIDDEN read gap, where read restrictions are ignored (`RepositorySecurityProvider.kt:37-38`, `:58-59`). It is a separate change.
- REST and dashboard upload endpoints, proxy caching and `/check`. The inventory task lists which of them judge the raw path. Those that do are listed in the pull request as follow-up unless they are a one-line change.
- The rule that `Location.of` rejects any two or more consecutive dots, even inside a name such as `a..b`. It stays as it is.

## Capabilities

### New Capabilities

- `maven-path-canonicalization`: the canonical path used by authorization, its three modes and the switch.

## Impact

- **Operators, default (`warn`):** no response changes. A WARN line and a counter appear for requests with encoded slashes, dot segments or doubled slashes whose two decisions differ. No configuration is needed.
- **Operators, `enforce` (opt-in):** uploads and deletes are judged on the canonical path. An upload accepted today may get 403 if its canonical form is not allowed. Traversal-looking paths get 400. A legacy route can match a decoded string it did not match before. Operators who use encoded paths must review their routes before switching.
- **Operators, breaking variant:** `enforce` becomes the default, so the same changes reach every installation without a switch. This is why the variant carries `!`.
- **Configuration:** one new optional key, `INGOT_AUTHORIZATION_PATHS`, with the `REPOSILITE_` fallback. Docs: `docs/gitbook/explanation/permission-evaluation.md` and `docs/gitbook/reference/access-tokens-and-routes.md`.
- **Code:** `reposilite-backend`, in the maven repository security provider, the route dispatcher and the legacy route matcher.
- **Commits:** `test(maven)` for the failing tests, `fix(maven)` for the canonicalizer and the switch, `docs(maven)` for the pages. Pull request title, squash-merged onto main, default variant:

  `fix(maven): canonicalize repository paths before authorization`

  Breaking variant, only if question 1 is answered that way:

  `fix(maven)!: canonicalize repository paths before authorization`
