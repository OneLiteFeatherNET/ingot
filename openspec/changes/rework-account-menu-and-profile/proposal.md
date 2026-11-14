# Proposal

## Open maintainer questions

Each question has a default. The change is written for the default; a different answer changes the named requirement and nothing else.

1. **Where the name is shown.** Default: the top bar trigger shows the avatar and the display name, as it does today from 900 pixels. The menu header shows the username and the kind and source line, never the display name again. Alternative: the trigger shows the avatar only and the menu header carries the full identity block (display name, username, kind, source).
2. **Username in the menu header.** Default: shown only when it differs from the display name, so a user whose display name is their username sees one line. Alternative: always shown.
3. **Placeholder for two-step sign-in.** Default: the "Sign-in and security" section keeps a named slot, `data-ingot-slot="add-multi-factor-authentication"`, with a short note that two-step sign-in is not set up. The change `add-multi-factor-authentication` does not exist yet and is not part of this pull request; it fills the slot when it is proposed. Alternative: the slot is absent until that change lands.
4. **Target of the "Explain" links.** Default: a principal who may use the Access area (the same nodes that open it) gets links to the in-dashboard check at `/access/check`, pre-filled with its own name and the permission; every other principal gets the link to the how-to page `check-access.md`. Alternative: the how-to page only, for everyone.
5. **Team parents.** Default: each team is listed with its direct parents ("inherits from ..."), which is what the principal's rights come through. Alternative: the full chain up to eight levels.
6. **Source of the sign-in.** Default: the menu and the profile name the providers the principal is linked to (`local`, `ldap`, `oidc`), read from the new access summary. Alternative: the provider the current tab signed in with, which needs no backend change for this part but is wrong for a principal linked to two providers.

## Why

The top right account control names the signed-in person twice. The trigger shows the avatar and the name, and the menu opened from it repeats the same name as its heading. The profile card on the account page does the same thing one level down: when a user has no separate display name, "Display name" and "Username" show the same value on two rows. A reviewer who opens the menu reads the name, then reads it again, and learns nothing new.

The account page also answers only half of the question an operator or a user asks about their own account. It shows the profile, the password, and the tokens. It does not show what the account may do. The permission system that is being built (teams with parents, permission nodes with denials, repository grants by role) decides every request, yet the person it applies to cannot see it without an administrator's help. Two-factor sign-in and passkeys have no place on the page either, so the page would need restructuring again when they arrive.

The alignment of the menu is off as well. The trigger keeps its avatar at the button's inner edge, the menu heading is inset by the menu padding plus its own padding, and the items use the same inset as the heading. The heading and the items line up with each other, but not with the avatar that opens them, and a long display name pushes the heading and the trigger out of the same grid. This is the defect the maintainer sees in the top right corner.

## What Changes

- **The name appears once in the bar and once on the page.** The trigger shows the avatar and the display name. The menu header is a small identity block: the username on its own line, only when it differs from the display name, and a second line with the kind (User or Machine) and the source (local, LDAP or OIDC). The display name is never repeated in the menu.
- **One inset grid for the menu.** The header and the items use one shared horizontal inset, and the trigger's avatar sits on the same left edge as the trigger's text. A long name truncates in the bar and in the page; the full name is the trigger's `title` and its accessible name.
- **Keyboard and screen readers keep the same behaviour, named better.** The trigger's accessible name becomes "Account menu, <display name>", so a screen reader user hears that the button opens a menu. Opening, arrow movement, Escape and focus return are unchanged.
- **The account page becomes four sections.** Profile (each value once: display name, username when it differs, kind, sign-in sources), Sign-in and security (the password card on cookie sessions, and the placeholder slot for two-step sign-in), Tokens (unchanged), and a new Your access section.
- **Your access is read only.** It lists the instance administrator flag, the teams with their direct parents, the permissions in plain language (allowed first, denials listed separately), and the repository access by role and path prefix, each with the team it comes through. A token session notes that the token narrows the list. It offers no control that changes anything, and it asks for the signed-in principal's own summary only; the page takes no principal from the address.
- **A read endpoint for the caller's own summary.** The existing `/api/auth/me` has no team parents, no instance administrator flag and no display name after a reload, and the team endpoint that has the parents needs `ingot.admin.teams`. So the page needs `GET /api/identity/me/access`, which is specified as a separate change, `add-own-access-summary-endpoint`, in the same pull request.
- **A view model between the response and the page.** The page renders a small view model, not the raw response. Repository access is a list of scopes, each with a type. Today there is one type, `repository`. Roles bound to Maven groupId namespaces, conditions and per-user signing keys can each add a scope or a row type later. A scope type the page does not know yet is shown as one plain line, never dropped and never a crash.

Not BREAKING. No configuration key, environment variable, deployment manifest, route, or stored data changes. The top bar, the account route and every existing API keep working. The new endpoint is additive. A Reposilite plugin that reads `/api/auth/me` sees no change.

## Capabilities

### New Capabilities

- `account-menu`: the account control in the top bar, its trigger, its menu header and its inset.
- `account-page`: the sections of "My account" and the read-only Your access section.

### Dependent Change

- `own-access-summary` in `add-own-access-summary-endpoint`: the one read endpoint the Your access section uses. The page can ship its menu and profile work without it; Your access waits for it.

## Impact

- **Operators:** nothing to change. The account page shows more, and only to the account's own holder.
- **Plugin authors:** nothing to change. The page adds no plugin extension point. A plugin dashboard page is not affected. The new view model lives in the frontend and is not a plugin API.
- **Code (frontend):** `reposilite-frontend/src/components/header/MenuPanel.vue` (trigger, header block, accessible name), `src/components/md/MdOverflowMenu.vue` (a `triggerLabel` prop and the shared inset of the header), `src/components/account/AccountView.vue` (sections), `ProfileCard.vue` (each value once), a new `src/components/account/YourAccess.vue`, a new pure `src/components/account/accessView.js` (the view model), and the identity client method for the endpoint in `src/store/client`. `src/anchors/registry.json` gets the new `data-ingot-*` anchors as `experimental` until they settle.
- **Code (backend):** none in this change. The endpoint is in `add-own-access-summary-endpoint`.
- **Documentation:** `reposilite-frontend/DESIGN.md` (the menu header block and the Your access pattern), and `docs/gitbook/how-to/check-access.md` (a pointer from the account page to the check).
- **Stable anchors:** the `data-ingot-component="menu-panel"`, `"account-view"` and `"profile-card"` names stay. New names are `data-ingot-slot="add-multi-factor-authentication"` and `data-ingot-component="your-access"`, both added to the registry.

## Verification

- Every red test in the tasks is seen to fail first.
- `npm test` in `reposilite-frontend` passes. The new Playwright spec `e2e/account.spec.js` passes at 320, 600 and 1440 pixels.
- Before and after screenshots of the menu open and of the account page are attached to the code pull request, at 320, 600 and 1440 pixels.
- `openspec validate rework-account-menu-and-profile --strict` passes, and the dash grep prints nothing.

## Out of scope

- Two-step sign-in, TOTP, passkeys and recovery codes. They belong to `add-multi-factor-authentication`, which fills the placeholder slot.
- Editing anything on the page: display name, password policy, team membership, nodes, grants. The page stays read only, and a user cannot grant themselves a permission from it.
- Roles bound to scopes beyond repositories and path prefixes, such as Maven groupId namespaces, conditions, and per-user signing keys. The authorization model is being redesigned; the view model leaves room for them, and no row for them is drawn now.
- Showing another principal's access. That is the Access area and the check, not the account page.
- Any change to `/api/auth/me`, to the login flow, or to the token list.
- Changing the 900 pixel breakpoint of the trigger's name.

## Shipping

Proposal PR `docs(openspec): propose the account menu and profile rework`, shared with the change `add-own-access-summary-endpoint`. Then two code pull requests, the endpoint first:

1. `feat(identity): add a read endpoint for the caller's own access summary` (from `add-own-access-summary-endpoint`). Type `feat`, scope `identity`.
2. `feat(ui): show who is signed in once and what they may do on the account page` (this change). Type `feat`, scope `ui`. Opened only after the endpoint pull request is merged, so the Your access section can call it. The menu and profile commits may be split off into a separate `feat(ui)` pull request if the maintainer prefers; the title above covers the whole change.

Commits in the code pull request: `test(ui)` for the red tests, `feat(ui)` for the view model, the menu, the page and the endpoint client, `docs(ui)` for `DESIGN.md`, `docs(gitbook)` for the how-to pointer. No file under `openspec/` is in either code pull request.

## Dependencies

- `add-own-access-summary-endpoint` must be merged before the Your access section is merged. The menu and the profile can ship before it; Your access then shows its empty state.
- `rework-administration-side-navigation` and `add-administration-overview` touch the same top bar and the same `DESIGN.md` section. No requirement overlaps; the merge order is by file, not by behaviour.
- `add-external-login-providers` decides the provider ids that the source line shows. This change names `local`, `ldap` and `oidc` as they appear in the access summary, and falls back to the raw id for an unknown provider.
