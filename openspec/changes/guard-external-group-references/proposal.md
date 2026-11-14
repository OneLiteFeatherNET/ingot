# Proposal

## Open maintainer questions

1. **Breaking or warn first?** Decided: enforce by default. `INGOT_IDENTITY_IDP_GUARD` defaults to `enforce`: linking a team to a provider group needs the IdP right, and a write without it is refused with 403. The commit is `fix(identity)!` with a `BREAKING CHANGE:` footer and a migration note, because automation that links teams with a token holding only `ingot.admin.teams` fails until it is granted `ingot.admin.idp`. As a temporary fallback, an operator can set `INGOT_IDENTITY_IDP_GUARD=warn`: linking proceeds as today, and a caller without the IdP right produces the audit event `EXTERNAL_REF_UNGUARDED` and a WARN line. The fallback is meant to be removed once operators have migrated.
2. **Which right guards the link?** Default: a new admin area `ingot.admin.idp`, added to the list of admin areas, so `ingot.admin.*` covers it and `ingot.admin.teams` alone does not. The design writes the right as `ingot.idp:manage`, but the colon action naming arrives with change `introduce-authorizer-chain`. Until then the node grammar is the one in `NodePattern.kt`. Decided: the default.
3. **Are existing links touched?** Default: no. Existing references are neither removed nor rewritten. The guard applies from the next create, update or clear of a reference. Decided: the default.

## Why

A team administrator can point a team at an administrative group of an identity provider. At the next login of a provider, every member of that group receives instance administration through the synchronized admin right. The path is:

- `createTeam` and `updateTeam` accept `externalRef`. The only check is the area `ingot.admin.teams` on the route (`IdentityApiEndpoints.kt`, the teams routes).
- `ExternalGroupSync.applyAdminRights` grants `ingot.admin.*` to every team whose reference has the provider prefix and whose group is an admin group (`ExternalGroupSync.kt:121-133`).

The grant endpoint already refuses to hand out rights the caller does not hold (`replaceGrants`, `IdentityFacade.kt:476-489`). The reference is the same kind of right, and it has no such check.

## What Changes

- A caller that sets, changes or clears `externalRef` on a team must hold `ingot.admin.idp`, or `ingot.admin.*`.
- In `enforce` (default), the write without the IdP right is refused with 403. In `warn`, a temporary fallback, the write proceeds and an `EXTERNAL_REF_UNGUARDED` audit event records the caller, the team and the reference.
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

- **Operators, default (`enforce`, breaking):** a token that links a team to a provider group needs `ingot.admin.idp` or `ingot.admin.*`. Automation holding only `ingot.admin.teams` gets 403 for such writes, so it must be granted the IdP right before the upgrade. Read-only calls and existing references are unaffected. This is why the commit carries `!`.
- **Operators, temporary fallback (`warn`):** set `INGOT_IDENTITY_IDP_GUARD=warn` to keep today's responses. A new audit event `EXTERNAL_REF_UNGUARDED` appears for links made without the IdP right.
- **Configuration:** one new optional key, `INGOT_IDENTITY_IDP_GUARD` (`off`, `warn`, `enforce`, default `enforce`), with the `REPOSILITE_IDENTITY_IDP_GUARD` fallback. A new node text, `ingot.admin.idp`, is accepted.
- **Docs:** `docs/gitbook/explanation/identity-model.md` (admin areas) and the identity provider guides under `docs/gitbook/`. Verify which guide describes external references.
- **Code:** `reposilite-backend`, in the identity facade, the node grammar and the audit actions.
- **Commits:** `test(identity)`, then `fix(identity)` for the node and the audit action, then `fix(identity)!` for the guard with its `BREAKING CHANGE:` footer, then `docs(identity)`. Pull request title, squash-merged onto main:

  `fix(identity)!: require idp rights to link teams to provider groups`
