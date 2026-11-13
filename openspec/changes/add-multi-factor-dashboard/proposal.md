# Proposal

## Open maintainer questions

1. **Recovery codes.** Default: shown once in a dialog with two actions, copy and download as a text file, and a check
   box that must be ticked before the dialog closes. They are never shown again.
2. **Passkey sign-in button.** Default: shown only when the server announces `passkeyFirstFactor` on the login route. It is
   a separate button above the password fields and does not replace them.
3. **Step-up prompt.** Default: a sensitive action in the dashboard opens the step-up dialog when the last second factor is
   older than the step-up window, then retries the action. Nothing is stored in the browser.
4. **Grace banner.** Default: shown on every page of a covered user without a factor until the grace period ends. It is
   not dismissible, because it is the only signal before the session is restricted.
5. **Administrator reset.** Default: a row action "Reset second factors" on the user's page in the administration area,
   behind a confirmation that names the user and says that their sessions end. It is hidden without instance admin rights.
6. **Account page section.** Default: the section `Sign-in and security` is added to the account page provided by the
   change `rework-account-menu-and-profile`. That change is proposed in parallel and is not on main yet. If it lands later,
   this change's code pull request waits for it.

## Why

The backend change `add-multi-factor-authentication` adds second factors, a login challenge, step-up and a policy. None of
them is usable from the dashboard yet. Without a dashboard part, an administrator cannot enrol a factor, a member cannot
see the grace period, and a challenged user has no screen to complete the sign-in. This change builds those screens
against the contract in section 15 of the identity model.

## What Changes

- **Sign-in.** `LoginModal.vue` gains a second step after a password or LDAP sign-in that answers `mfaRequired`: a code
  field for authenticator codes, a "use a recovery code" link, and a passkey action. A passkey button appears above the
  password fields when the server announces the first-factor switch.
- **Account page.** A `Sign-in and security` section, provided by `rework-account-menu-and-profile`, with a `SecondFactorsCard`:
  the list of factors with rename and remove, enrolment of an authenticator app (the seed is shown as a QR code and as text,
  the code is confirmed) and of a passkey, the recovery codes with regeneration, and the grace banner when it applies.
- **Step-up dialog.** One dialog for every sensitive action in the dashboard, accepting a code, a recovery code or a
  passkey, then retrying the action.
- **Administration.** The user page in the administration area shows the factor summary and the reset action, and the
  access policy fields (`login.mfa`) are added to the login settings form in `components/access/loginSettings.js`.
- **Client.** New calls in `src/store/client.js` for the routes of `add-multi-factor-authentication`, and a WebAuthn helper
  for `navigator.credentials` that encodes and decodes the binary fields.
- **Tests.** Vitest unit tests for each component and for the WebAuthn helper, and end-to-end tests in the existing
  dashboard end-to-end suite for sign-in with a code and for enrolment of a recovery code set.

### Pull request

Ships under `feat(ui)`, because the change is dashboard work. Pull request title, squash-merged onto main:

    feat(ui): add second factor steps to the dashboard sign-in and account page

The backend part is the change `add-multi-factor-authentication`, under `feat(identity)`. Its code pull request is merged first.

## Breaking changes

**BREAKING**: none. The dashboard reads the new fields of the login response and the security status only when they are
present. Without a second factor or a policy, the sign-in, the account page and the administration area look and behave as
they do today. Nothing is stored in the browser beyond what the dashboard already stores.

## Out of scope

- The server contract, the factors, the policy and the audit events. They are in `add-multi-factor-authentication`.
- The account menu and the profile page. Provided by `rework-account-menu-and-profile`.
- A list of browser sessions and their revocation (O-20 of the identity model).
- Policy editing beyond the `login.mfa` fields in the login settings form.
- Visual design of new screens beyond the existing Material-based components of the dashboard.

## Capabilities

### New Capabilities

- `dashboard-multi-factor-authentication`: the second step of the sign-in, the factor management of the account page, the
  step-up dialog, the grace banner, and the administrator controls for factors and policy.

### Modified Capabilities

- None in the openspec sense.

## Impact

- **Users:** a new step after the password for users with a factor, and enrolment screens on the account page.
- **Operators:** no action. The administrator controls appear only for instance admins.
- **Code:** `reposilite-frontend` only: `components/header/LoginModal.vue`, `store/client.js`, `store/session.js`, the account
  components, `components/access/loginSettings.js` and the administration user page, plus new components and tests.
- **Dependencies:** none. WebAuthn uses the browser API. The QR code is rendered with a library only if the existing
  dependencies do not already provide one; the apply task checks that first.

## Verification

- `openspec validate add-multi-factor-dashboard --strict` passes.
- Vitest for the new components and the WebAuthn helper, run with the frontend test script.
- The end-to-end sign-in and enrolment tests run in the existing dashboard end-to-end workflow.
