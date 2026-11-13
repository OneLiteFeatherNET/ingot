# Tasks

Each group is one or more commits of one Conventional Commits type. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock, no network, a fresh component and router per test, the access summary passed in as a fixture, assertions with
messages. The view model is tested as a pure function with fixed fixtures. `npm test` runs in `reposilite-frontend`, targeted with a
file pattern while working, through `heavy`. Playwright runs through `heavy`. No file under `reposilite-backend/` changes in this
change. The pull request starts only after `add-own-access-summary-endpoint` is merged, because Your access calls it.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose the account menu and profile rework`: this change and `add-own-access-summary-endpoint`, nothing
  else. Verify: `openspec validate rework-account-menu-and-profile --strict` passes and the dash grep prints nothing.

## 2. Red tests

- [ ] 2.1 `test(ui)`: in a new `src/components/account/accessView.test.js`, with fixed summary fixtures, assert that `accessView`
  lists a team with its direct parents, builds one plain sentence for each node form (repository action, admin area, `ingot.admin.*`,
  artifact with and without repository), lists denials apart, passes an unknown node under "Other", and shows an unknown scope type as
  one line. Verify: it fails, because the module does not exist.
- [ ] 2.2 `test(ui)`: in `src/components/header/MenuPanel.test.js`, assert the display name appears once (trigger only), the header
  shows the username only when it differs, the kind and source line reads "User, LDAP" for a LDAP principal, the trigger's accessible
  name is "Account menu, <name>", and the name text has a `title` with the full name. Verify: it fails at `MenuPanel.vue:106` (name
  repeated) and at `MenuPanel.vue:85` (label dropped by the trigger slot).
- [ ] 2.3 `test(ui)`: in `src/components/md/MdOverflowMenu.test.js`, assert that a `triggerLabel` names the button when a trigger slot is
  used, that the header wrapper and the items carry the same horizontal inset class, and that Escape, Home, End and wrapping arrows are
  unchanged. Verify: it fails, because `triggerLabel` does not exist and the header has its own inset.
- [ ] 2.4 `test(ui)`: in `src/components/account/AccountView.test.js`, assert four headings in the order Profile, Sign-in and security,
  Tokens, Your access; the placeholder `data-ingot-slot="add-multi-factor-authentication"` inside the second section; no input and no
  button in Your access other than the Explain links; the summary is requested with no principal parameter; the password card moves
  to the top under `mustChangePassword`. Verify: it fails.
- [ ] 2.5 `test(ui)`: in `src/components/account/ProfileCard.test.js`, assert that equal display name and username show one value and no
  Username row, and that Teams and the instance admin tag are not drawn. Verify: it fails at `ProfileCard.vue:82-87`.
- [ ] 2.6 `test(ui)`: in `e2e/account.spec.js`, at 320, 600 and 1440 pixels: the name appears once in the menu flow, Enter opens the
  menu and Escape returns focus to the trigger, no horizontal scroll, Your access visible, Explain links named with their node. Selectors
  are roles and accessible names; no timeouts. Verify: it fails against the current build.

## 3. Implementation

- [ ] 3.1 `feat(ui)`: add `src/components/account/accessView.js` (the pure view model and the plain-language table) and the identity
  client call for `GET /api/identity/me/access` in `src/store/client`. Verify: 2.1 passes.
- [ ] 3.2 `feat(ui)`: `MdOverflowMenu.vue` gets `triggerLabel` and one shared inset for the header and the items; `MenuPanel.vue` shows
  the name once, the identity block with `data-ingot-component="account-identity"`, and the full name as `title`. Verify: 2.2 and 2.3 pass.
- [ ] 3.3 `feat(ui)`: `ProfileCard.vue` shows each value once; `YourAccess.vue` renders the view model with the states of the spec; the
  placeholder slot is added to `AccountView.vue`; the four sections are ordered there. Verify: 2.4 and 2.5 pass.
- [ ] 3.4 `feat(ui)`: register `data-ingot-component="account-identity"`, `data-ingot-component="your-access"` and the placeholder slot
  in `src/anchors/registry.json` as `experimental`. Verify: the anchors tests pass.

## 4. Documentation

- [ ] 4.1 `docs(ui)`: in `reposilite-frontend/DESIGN.md`, describe the menu header block, the shared inset and the Your access pattern
  with its read-only rule. Verify: the dash grep prints nothing.
- [ ] 4.2 `docs(gitbook)`: in `docs/gitbook/how-to/check-access.md`, add a short paragraph that the account page shows the caller's own
  access under Your access and links to this check. Verify: the dash grep prints nothing.

## 5. Check

- [ ] 5.1 Run `npm test` and `e2e/account.spec.js` through `heavy`. Take before and after screenshots of the open account menu and of
  the account page at 320, 600 and 1440 pixels, and attach them to the pull request. Verify: all pass; the screenshots are attached.

## 6. Pull request

- [ ] 6.1 Open the code pull request under the title `feat(ui): show who is signed in once and what they may do on the account page`,
  from the remote main branch that already contains the merged proposal and the merged `feat(identity)` endpoint, with no file under
  `openspec/`. Fill the repository's pull request template.
