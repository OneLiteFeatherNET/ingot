# Proposal

## Open maintainer questions

1. **Breaking or warn first?** Default: warn first. `INGOT_IDENTITY_IDP_GUARD` defaults to `warn`: linking a team to a provider group proceeds as today, and a caller without the IdP right produces the audit event `EXTERNAL_REF_UNGUARDED` and a WARN line. `enforce` refuses such a link with 403. The commit is `fix(identity)` without `!`. The alternative is `enforce` as the default and the title `fix(identity)!: require idp rights to link teams to provider groups` with a migration note, because automation that links teams with a token holding only `ingot.admin.teams` then fails. Trade-off: warn first leaves the escalation open until an operator sets `enforce`.
2. **Which right guards the link?** Default: a new admin area `ingot.admin.idp`, added to the list of admin areas, so `ingot.admin.*` covers it and `ingot.admin.teams` alone does not. The design writes the right as `ingot.idp:manage`, but the colon action naming arrives with change `introduce-authorizer-chain`. Until then the node grammar is the one in `NodePattern.kt`.
3. **Are existing links touched?** Default: no. Existing references are neither removed nor rewritten. The guard applies from the next create, update or clear of a reference.

## Why

A team administrator can point a team at an administrative group of an identity provider. At the next login of a provider, every member of that group receives instance administration through the synchronized admin right. The path is:

- `createTeam` and `updateTeam` accept `externalRef`. The only check is the area `ingot.admin.teams` on the route (`IdentityApiEndpoints.kt`, the teams routes).
- `ExternalGroupSync.applyAdminRights` grants `ingot.admin.*` to every team whose reference has the provider prefix and whose group is an admin group (`ExternalGroupSync.kt:121-133`).

The grant endpoint already refuses to hand out rights the caller does not hold (`replaceGrants`, `IdentityFacade.kt:476-489`). The reference is the same kind of right, and it has no such check.

## What Changes

- A caller that sets, changes or clears `externalRef` on a team must hold `ingot.admin.idp`, or `ingot.admin.*`.
- In `warn` (default), the write proceeds and an `EXTERNAL_REF_UNGUARDED` audit event records the caller, the team and the reference. In `enforce`, the write is refused with 403.
- The node grammar accepts `ingot.admin.idp`.
- Existing references stay as they are.

### Out of scope

- Adding members to a team (`manage-members`) and creating trusted publishers. The design lists them as escalation points, but they belong to the delegation and CI changes (`add-scoped-delegation`, `add-ci-token-exchange`).
- Group mapping rules (`add-group-mapping-rules`).
- The sync behaviour, which stays as it is.
- The "machine:" reserved prefix rule.

## Capabilities

### New Capabilities

- `identity-external-group-guard`: the IdP right, the guard of external references and its warn and enforce modes.

## Impact

- **Operators, default (`warn`):** no response changes. A new audit event appears for links made without the IdP right. No configuration is needed.
- **Operators, `enforce` (opt-in):** a token that links a team to a provider group needs `ingot.admin.idp` or `ingot.admin.*`. Automation holding only `ingot.admin.teams` gets 403 for such writes. Read-only calls are unaffected.
- **Operators, breaking variant:** `enforce` becomes the default for every installation. This is why that variant carries `!`.
- **Configuration:** one new optional key, `INGOT_IDENTITY_IDP_GUARD` (`off`, `warn`, `enforce`), with the `REPOSILITE_IDENTITY_IDP_GUARD` fallback. A new node text, `ingot.admin.idp`, is accepted.
- **Docs:** `docs/gitbook/explanation/identity-model.md` (admin areas) and the identity provider guides under `docs/gitbook/`. Verify which guide describes external references.
- **Code:** `reposilite-backend`, in the identity facade, the node grammar and the audit actions.
- **Commits:** `test(identity)`, then `fix(identity)`, then `docs(identity)`. Pull request title, squash-merged onto main, default variant:

  `fix(identity): require idp rights to link teams to provider groups`

  Breaking variant, only if question 1 is answered that way:

  `fix(identity)!: require idp rights to link teams to provider groups`
