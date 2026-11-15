# Design

## Context

`attachmentHeaders` takes a Boolean `cache`. The answer is `public` or `no-cache, no-store`. Two call sites feed it:
`MavenEndpoints` through `MavenFacade.acceptsCachingOf`, and `MavenLatestApiEndpoints` through `ResolvedDocument.cachable`.
Both come from `Repository.acceptsCachingOf`, which also selects the ETag form, so that method must not change meaning.

## Decisions

### D1 The scope comes from the repository, not from the caller

The value depends on the visibility of the repository and of the local members it reaches. It does not depend on whether this
request carried credentials. Keying it on the request would not help: an authorized `public` answer is the leak itself, and a
shared cache cannot tell the two apart. A PUBLIC repository keeps `public` for every caller, as before.

### D2 Groups inherit the scope of the local members they reach

A group stores nothing, so its answer is the answer of the member that holds the file. The member is not known before the
lookup, so the rule is conservative: every local member the group can reach decides. The walk follows loopback references
only, which are read from the repository's own mirror list, and it keeps a set of visited names so that a cycle cannot loop.
Remote references are skipped. The walk is in-memory over the repository list, so it adds no I/O to a response.

Alternative considered: mark the answer by the member that served it. That is exact, but it needs the lookup to carry the
scope out of the loopback client, which changes the shape of `ResolvedDocument` for every caller. Rejected for this change.

### D3 HIDDEN is treated as access-controlled

A HIDDEN file answers anonymous requests by exact path, so `public` is not wrong for that caller. It is still set to
`private`, because the operator chose a visibility that is not PUBLIC, and one rule that follows the visibility setting is
easier to predict than a rule that depends on how a file is reached. The cost is a lower hit rate for HIDDEN repositories
behind a CDN. The escape hatch covers that case.

### D4 The escape hatch reads only the environment, and refuses unknown values

The setting is a boolean that loosens a security default. An unknown value such as `yes` or `on` must not silently mean
`false`, so it stops the start-up, the same way `INGOT_PROXY_UPSTREAM_STATUS` does. The reader takes the environment map as
an argument, so the tests do not touch the real environment. A system property is not added, because the roadmap names only
the variable.

## Non-goals

- No change to `Repository.acceptsCachingOf`, to the ETag selection or to `maxAge`.
- No change to who may read a repository.
