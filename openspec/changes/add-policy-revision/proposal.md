# Proposal

## Open maintainer questions

1. **Does a read check the revision on every request, or does a poll keep a snapshot?** Default: a background poll every 2 seconds (`INGOT_AUTHORIZATION_REFRESH`, fallback `REPOSILITE_AUTHORIZATION_REFRESH`), plus a read of the revision row before every identity write. Checking on every authenticated request would add one indexed SELECT to each request. The poll bounds the staleness of a revocation at the interval.
2. **Is the authentication cache made revision-aware, or only shortened?** Default: revision-aware. Each cached credential records the revision it was cached under and is dropped once the revision moves. The one-minute upper bound stays as it is today.
3. **Does a single node change?** Default: no. A single node writes the revision row and reads it back. It needs no network and no configuration.

## Why

Today a revocation on one replica can be ignored by another for up to a minute. The authentication cache keeps successful credentials for one minute per replica (`AuthenticationFacade.kt:71`, `:80-87`), and `forgetCachedCredentials` drops entries only on the instance that made the change (the documentation of that cache says so, `AuthenticationFacade.kt:74-78`).

Authorization is not cached in the same way. Every check reads the identity tables again (`AccessTokenFacade.subjectOf`, `AccessTokenFacade.kt:442`, and `IdentityAccessTokenRepository.subjectOfPrincipal`, `IdentityAccessTokenRepository.kt:61-63`). The staleness is therefore in authentication, not in the rights. The design calls it a "60-second group cache"; the cache in the code is the credential cache, and this proposal corrects the design on that point.

## What Changes

- A `policy_revision` row is incremented in the same transaction as every identity write: principals, tokens, teams, memberships, grants, nodes, team parents and instance roles. A rolled-back write leaves it unchanged.
- A background refresh reads the row on the configured interval and keeps the last value seen.
- Authentication cache entries record the revision they were made under. An entry from an older revision is dropped on its next use.
- Identity writes read the revision before deciding and reload their inputs when it is stale.
- Each authorization decision records the revision it was made under, for the decision log of `add-decision-log`.

### Out of scope

- PostgreSQL LISTEN/NOTIFY, and any cluster bus fan-out (`add-cluster-bus` is a separate proposal).
- Redis, which the high availability design rejects.
- The compiled snapshot of the design. Decisions still read the tables per call in this change.

## Capabilities

### New Capabilities

- `identity-policy-revision`: the revision counter, the refresh, the revision-aware cache and the bound on staleness.

## Impact

- **Operators:** revocations on one replica reach the others within one refresh interval (default 2 seconds) instead of up to a minute. A single-node installation sees no visible change. One new table, `policy_revision`. One new optional key, `INGOT_AUTHORIZATION_REFRESH`, with the `REPOSILITE_` fallback. Nothing here is BREAKING.
- **Code:** `reposilite-backend`, in the identity storage, the authentication facade and the identity facade.
- **Docs:** `docs/gitbook/explanation/multi-replica-operation.md` and the staleness statement in `docs/gitbook/explanation/permission-evaluation.md`, which describes the cache.
- **Commits:** `test(identity)` for the failing tests, `feat(identity)` for the revision, `docs(identity)` for the pages. Pull request title, squash-merged onto main:

  `feat(identity): invalidate authorization caches across replicas`
