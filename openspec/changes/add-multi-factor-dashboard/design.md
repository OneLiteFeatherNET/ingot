# Design

This change builds the dashboard side of `add-multi-factor-authentication`. It consumes that change's routes and fields
and adds nothing to the server. Where the backend decides, the dashboard shows the decision; it never decides itself.

## Current state (cited)

Paths are relative to `reposilite-frontend/src/`.

- **Sign-in.** `components/header/LoginModal.vue` offers the password providers (`:83-85`, the local account and LDAP with
  one form and a switch, `:35-37`), the single sign-on buttons (`:87`), and a setup mode (`:107`-`:118`). The login call
  is `store/client.js:230` (`axios.post(.../api/auth/login)`), and the endpoint list at `client.js:71` is used to skip the
  refresh logic for these calls.
- **Session.** `store/session.js` keeps the answer of `GET /api/auth/providers` (`:52`) and of `GET /api/auth/me` (`:95`),
  and the allowed nodes (`:377`). `store/nodeAccess.js` is the matcher behind every gate.
- **Account.** `components/account/AccountView.vue` (96 lines) holds `ProfileCard.vue` and `PasswordCard.vue`.
  `PasswordCard.vue:27` documents the injected `api` object (`changePassword`), which keeps the card testable. Its tests
  are in the same folder.
- **Tokens and secrets shown once.** `components/identity/SecretOnceDialog.vue` already shows a secret one time. Its
  pattern fits the recovery codes.
- **Access settings.** `components/access/loginSettings.js` builds the login settings form, including the callback URL
  from `publicUrl` (`:231`, `:259`).
- **Client calls.** `store/client.js:259-264` holds the own password and token endpoints.
- **Provider links.** `components/header/providerLinks.js` builds the redirect back to the page the visitor came from.
- **Provider icons.** `components/icons/providerIcons.js` lists the icons the provider list uses.

The server contract is in `add-multi-factor-authentication` (design D14): the login response with `mfaRequired` and
`methods`, `POST /api/auth/mfa/verify`, the passkey login routes, `GET /api/identity/me/security`, the factor routes, the
step-up route, and the administrator factor routes.

## Decisions

### D1. The second step is part of the sign-in dialog

When a password or LDAP login answers `mfaRequired`, `LoginModal.vue` replaces the password form with a second step in the
same dialog. The step shows the methods the server listed: a six-digit code field (for authenticator codes), a link "Use a
recovery code" that swaps the field, and a "Use a passkey" button when `passkey` is listed. Reason: one dialog keeps the
sign-in in one place, and the challenge cookie is already set by the server.

### D2. The WebAuthn helper is one module

`src/store/webauthn.js` encodes and decodes the binary fields of the WebAuthn options (base64url to `ArrayBuffer` and back)
and wraps `navigator.credentials.create` and `get`. Every ceremony passes through it, so the encoding is written once and
tested without a browser. If the browser has no WebAuthn support, the passkey actions are hidden, and the security status
says so.

### D3. The account section comes from the account menu change

The `Sign-in and security` section is provided by `rework-account-menu-and-profile`. This change adds a `SecondFactorsCard`
inside that section, built like `PasswordCard.vue` with an injected `api` object. If the section does not exist when the code
pull request is opened, the code pull request waits for it. Reason: the menu change owns the page structure, and this change
must not invent a second one.

### D4. Enrolment flows

- **Authenticator app.** Start, show the `otpauth` URI as a QR code and as text, ask for the first code, confirm. The seed is
  shown only in that step. The QR code uses a library only when the existing dependencies already provide one (the apply
  task checks; if none, the text form is the fallback and the QR code is left for a follow-up).
- **Passkey.** Ask for a name, run the registration ceremony, show the result in the list.
- **Recovery codes.** Shown once in `SecretOnceDialog`-style dialog with copy and download and a confirmation check box
  (open question 1). Regeneration asks for the step-up first.

### D5. The step-up dialog

Sensitive actions call a shared `withStepUp(action)` in `store/session.js`. The action runs. If the server answers
`step_up_required`, the step-up dialog opens with the methods the user has, and the action is retried after a successful
step-up. The dialog keeps nothing in the browser (open question 3). Reason: the server decides the window (`ath`), so the
dashboard only asks when the server says so, and does not run its own clock.

### D6. The grace banner and the restricted session

The security status (`GET /api/identity/me/security`) returns `required`, `graceUntil` and `scope`. When a grace period
applies, a banner appears on every page for the covered user without a factor (open question 4). When `scope` is `enrol`, the
dashboard shows only the account section with the enrolment flow, and every other navigation point is hidden, because the
server refuses those routes anyway. The dashboard does not hide anything it cannot be sure the server refuses; it only
simplifies the screen.

### D7. Administrator controls

- The user page in the administration area shows the factor summary (counts and kinds, no secrets) and the action
  "Reset second factors" (open question 5). The action is hidden without instance admin rights, uses a confirmation dialog,
  and calls the administrator route.
- The login settings form gets the `login.mfa` fields (policy, teams, grace days, step-up minutes, passkey first factor) and
  the `mfaAcrValues` and `mfaAmrValues` fields per OpenID Connect provider. The form warns that the last administrator may
  need the break-glass token when the policy is turned on.

### D8. Errors

Error answers are shown with the server message, as the sign-in does today. The dashboard maps the problem codes
`step_up_required`, `mfa_enrolment_required` and the `409` of TOTP enrolment to their own messages, so the user sees a
reason and a next step.

### D9. Tests

- Unit tests with the existing test runner for `LoginModal` (the second step, the recovery swap, the passkey button shown
  only when announced), the WebAuthn helper (encoding round trips with fixed bytes), `SecondFactorsCard`, the step-up
  helper (retries once, does not loop), the grace banner and the administrator reset action.
- No sleeps. Time comes from the server answer (`graceUntil`), not from the browser clock, except in the step-up
  countdown, which takes an injected clock.
- End-to-end: sign-in with an authenticator code and enrolment of a recovery code set, in the existing dashboard
  end-to-end suite. The passkey ceremony is covered by the helper tests, because the browser test environment has no
  authenticator.

## Risks

- **Account section dependency.** Mitigated by D3: this change waits for the menu change instead of duplicating its page.
- **Passkeys in other browsers.** Feature detection hides the passkey actions where WebAuthn is missing.
- **Visual consistency.** New dialogs reuse the existing dialog and card components, so no new design system is introduced.

## Verification

- `openspec validate add-multi-factor-dashboard --strict`.
- Unit tests named in D9, run with the frontend test script, and the lint step of the frontend.
- The end-to-end scenario of D9 in the existing suite.
