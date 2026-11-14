# Design

## Context

Read on `origin/main` (commit 751fe819b) in `reposilite-frontend/`:

- `src/components/header/MenuPanel.vue:39` computes `signedInName` as `principal.displayName || principal.name || token.name`.
  The trigger renders it at line 99 (`data-test="account-name"`, visible from 900 pixels), and the menu `#header` slot renders
  the same value again at lines 101-107 (`data-test="menu-heading"`).
- `MenuPanel.vue:85` passes `Account menu for <name>` as the label of `MdOverflowMenu`. `MdOverflowMenu.vue:189` drops that label
  when a `trigger` slot is used (`:aria-label="$slots.trigger ? undefined : label"`), so the button is named by its visible
  content only: the name, or nothing from the avatar, which is `aria-hidden`. The phrase "Account menu" reaches a screen reader
  only through the menu's own `aria-label` (`MdOverflowMenu.vue:204`), which is announced after the button is activated.
- `MdOverflowMenu.vue:210-216` wraps the `header` slot in `px-3 pb-2 pt-1` inside the menu's `p-2` (`:206`). The items use
  `px-3` as well (`:175`), so the heading text and the item labels share one inset, 20 pixels from the menu edge. The trigger
  keeps its avatar at 8 pixels (`MenuPanel.vue:87`, `!px-2`). The two insets are not one grid; that is the misalignment the
  maintainer reports. Not confirmed on screen: no browser run was made for this proposal.
- `ProfileCard.vue:48` computes `displayName` as `displayName || name`, and lines 76-87 always draw a "Username" row. When the
  display name is missing, both rows print the same value.
- `AccountView.vue` draws the password card, the profile card and the token list in a two-column grid from 960 pixels. There are
  no sections and no heading for the access.
- `src/store/session.js` keeps `details` as the answer of `GET /api/auth/me`. `instanceAdmin` (line 364) reads
  `details.instanceAdmin`, which the backend's `SessionDetails` (`reposilite-backend/src/main/kotlin/com/reposilite/auth/api/SessionDetails.kt:23-32`)
  does not send; the fallback is the legacy `access-token:manager` permission. `applyMe` (line 96-104) replaces `principal` with
  the `SessionPrincipal` of `/api/auth/me`, which has `id`, `kind` and `name` and no `displayName`.

Backend, for what "what may I do" can be answered from today:

- `/api/auth/me` gives the team names (`teams`), the grants (`team`, `repository`, `pathPrefix`, `role`) and the effective nodes,
  token narrowing included (`nodes`). It gives no team parents, no instance admin flag, no display name, and no provider.
- `GET /api/identity/teams/{name}` returns `parents`, but needs `ingot.admin.teams`.
- `GET /api/identity/principals/{name}` returns the provider list and the display name, but needs `ingot.admin.users`.
- `GET /api/identity/check` answers one node or path per call and needs a node name to ask about. It cannot list.

So the page needs one call that returns the caller's own summary. That is `add-own-access-summary-endpoint`.

## Goals

- The name is drawn once in the bar and once in the page, and never repeated inside the menu.
- The menu's heading, its items and the trigger use one inset rule.
- Keyboard and screen reader behaviour stays; the trigger's name says what the button does.
- The account page has four sections, and Your access is read only.
- The Your access view model accepts a new scope type without a new page.

## Non-goals

- A second evaluator. The page shows the decision the server already made (`/api/auth/me` and the summary endpoint both use
  `PermissionEvaluator.effectiveNodes`). The browser never decides a permission itself beyond the existing `canUse` gate.
- A permission matrix. The page lists the rights a principal holds, not the rights of the whole instance.

## Decisions

### D1. The bar shows the name, the menu shows the identity line

The trigger is `avatar + display name` from 900 pixels, `avatar + sr-only name` below, as today. The header block of the menu
shows, in this order:

1. The username, only when it differs from the display name (`md-body-small`, `text-on-surface-variant`).
2. A line with the kind and the source: `User · Local`, `Machine · Token` and so on (`md-label-small`).

The display name is not shown in the header. If the display name equals the username, the header shows only the kind line.

The source is the list of provider ids from the access summary (`local`, `ldap`, `oidc`, ...), joined with a comma, or the word
"Local" when the list is empty and the kind is User (a password account). An unknown provider id is shown as the id.

### D2. One inset for the menu

`MdOverflowMenu` gets a single exported inset class, used by the header wrapper and by every item:

- The menu keeps `p-2` (8 pixels) and every row, header or item, adds `px-3` (12 pixels). The header and the items therefore
  share the same text edge (20 pixels from the menu edge), and the state layers of the items cover the full row from 8 pixels.
- The header is not a menu item, so it gets `role="none"` (already so) and no state layer.
- The trigger's avatar keeps `!px-2` only when the trigger is an account trigger. Its left edge is the bar's inset, and the
  menu opens from the trigger's right edge (`MdOverflowMenu.vue:76-77`), so the menu's right edge and the trigger's right edge
  are one line. This is documented; the menu's text is not aligned to the avatar.

The header text truncates with `truncate` (`min-w-0`), and its full value is the `title` of the text.

### D3. A trigger label that names the button

`MdOverflowMenu` gets an optional `triggerLabel` prop. When a `trigger` slot is used and `triggerLabel` is given, the button gets
`aria-label="triggerLabel"`. The account menu passes `Account menu, <display name>`. Without the prop the existing behaviour is
kept (the button is named by its content), so no other caller changes.

### D4. Long names

The trigger's name span keeps `min-w-0 truncate`. It gets `title` set to the full display name, so the full name is available
on hover and to a pointer user. The accessible name is the full `triggerLabel`, not the truncated text.

### D5. Account page sections

`AccountView.vue` draws four `section`s in this order, each with a heading:

1. **Profile** (`ProfileCard.vue`): the display name, the username only when it differs, the kind and the sign-in sources. The
   teams and the instance admin tag move out of it, to Your access, so no value appears twice on the page.
2. **Sign-in and security**: the password card (cookie sessions; a token session gets the note it has today) and the placeholder
   slot `data-ingot-slot="add-multi-factor-authentication"` with the note "Two-step sign-in is not set up". The slot is a
   `section` with its heading; the later change replaces its content.
3. **Tokens**: `TokenList` as today.
4. **Your access** (`YourAccess.vue`): the read only view described in D6.

The must-change-password behaviour is kept: the password card moves to the top, and Your access is drawn below Tokens.

### D6. Your access and its view model

`src/components/account/accessView.js` exports a pure function `accessView(summary)`. It takes the response of
`GET /api/identity/me/access` (or `null` while it loads, or an error) and returns:

```
{
  status: 'loading' | 'ready' | 'unavailable',
  instanceAdmin: boolean,
  teams: [{ name, parents: [name] }],
  permissions: { allowed: [{ node, meaning }], denied: [{ node, meaning }] },
  scopes: [{ type: 'repository', role, repository, pathPrefix, via, meaning }],
  tokenNarrowed: boolean,
  unknown: [{ type, text }]
}
```

- `meaning` is one plain sentence for a node, built by a table of the forms in `docs/gitbook/reference/permission-nodes.md`:
  repository actions ("Deploy to releases"), admin areas ("Manage users"), `ingot.admin.*` ("Full instance administration"),
  artifact nodes ("Deploy artifacts under net.example"), and any node the table does not know, which is shown verbatim.
- `scopes` is the extension point. Each scope has a `type`. The `repository` type is the only one drawn now. A scope with a type
  the view does not know goes to `unknown` and is drawn as one line with its text, so a server that adds scopes first does not
  break the page. Roles bound to Maven groupId namespaces, conditions and signing keys add a type, a renderer and a test; the
  page layout does not change.
- `YourAccess.vue` renders the view model only. It has no fetch of its own; the account view passes the summary in, and the
  client call sits in the store next to `details`.

The access summary is requested only for the signed-in principal. The page never takes a principal from the address or from the
form, and it has no control that writes anything.

### D7. The explain link

Each permission and each repository scope has an "Explain" link. For a principal who may use the Access area (the nodes in
`accessNodes`, the same as `src/router` uses), it points to `/access/check?principal=<name>&node=<node>`. Otherwise it points to the
how-to page, `docs/gitbook/how-to/check-access.md`, using the documentation's address. The default is in the open questions.

### D8. Placeholder for two-step sign-in

The slot is a `section` with `data-ingot-slot="add-multi-factor-authentication"` and an `id` for its heading. Nothing else in this
change depends on it. When `add-multi-factor-authentication` lands, it replaces the note with its own card and keeps the slot name.

## Risks

- **The name is still on the page twice in one case.** The bar shows the display name, and the Profile section shows it too. The
  maintainer's wording is "the name is shown twice" on the top right. The bar and the page are two places; the menu, which is
  the reported place, shows it once. Open question 1 asks whether the bar should show only the avatar.
- **A slow summary.** The Your access section shows a loading line; the rest of the page does not wait for it.
- **Plain-language drift.** The `meaning` table is covered by a test per node form, so a new node form fails a test rather than
  printing raw text silently.

## Migration

None. No stored data, no configuration, no route changes. The page reads one more endpoint, and a server that is older than the
endpoint answers 404; the section then shows "The access summary is not available on this server" and nothing else changes.
