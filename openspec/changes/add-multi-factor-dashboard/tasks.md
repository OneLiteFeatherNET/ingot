# Tasks

Each group is one Conventional Commits type and carries one type per commit. Tests are written first and seen to fail
before the component exists. Unit tests need no sleeps and no browser clock: time comes from the server answers, or from
an injected clock in the step-up helper. Every test has a fresh fixture and assertions with messages.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose multi-factor authentication`: this change ships in the same proposal pull request as
  `add-multi-factor-authentication`. Verify: `openspec validate add-multi-factor-dashboard --strict` passes.

## 2. Client and WebAuthn helper

- [ ] 2.1 `test(ui)`: failing tests for the new client calls in `store/client.js` (routes and bodies of the contract in
  section 15 of the identity model), and for `store/webauthn.js` (base64url to `ArrayBuffer` and back with fixed bytes, the
  browser-support check). Verify: the tests fail before 2.2.
- [ ] 2.2 `feat(ui)`: add the calls to `store/client.js` and the helper `store/webauthn.js`. Verify: 2.1 passes and the
  existing client tests still pass.

## 3. Sign-in second step

- [ ] 3.1 `test(ui)`: failing tests for `LoginModal.vue`: the second step appears on `mfaRequired` with the listed methods,
  the recovery option swaps the field, the passkey action appears only when announced and supported, and a wrong code keeps
  the step open. Verify: the tests fail before 3.2.
- [ ] 3.2 `feat(ui)`: add the second step and the passkey action to `LoginModal.vue`. Verify: 3.1 passes and the existing
  `LoginModal` behaviour tests still pass.

## 4. Account page section

- [ ] 4.1 `test(ui)`: failing tests for `SecondFactorsCard` (list, rename, remove, the authenticator enrolment with the seed
  shown once and confirmed by a code, the passkey enrolment, the recovery codes dialog with the confirmation check box, and
  no seed after the dialog closes), built with an injected `api` object like `PasswordCard`. Verify: the tests fail before 4.2.
- [ ] 4.2 `feat(ui)`: add `SecondFactorsCard` into the `Sign-in and security` section of the account page. Depends on
  `rework-account-menu-and-profile` being merged; if it is not, this task waits. Verify: 4.1 passes and `AccountView.test.js`
  still passes.

## 5. Step-up and restricted screen

- [ ] 5.1 `test(ui)`: failing tests for `withStepUp` in `store/session.js` (the action runs once, a `step_up_required` opens
  the dialog, the retry happens once, a second `step_up_required` closes the dialog with the error), for the grace banner
  (shown on every page with `graceUntil`, not dismissible), and for the restricted navigation with scope `enrol`. Verify: the
  tests fail before 5.2.
- [ ] 5.2 `feat(ui)`: add the step-up dialog, `withStepUp`, the grace banner and the restricted navigation. Verify: 5.1 passes.

## 6. Administration controls

- [ ] 6.1 `test(ui)`: failing tests for the factor summary on the user page, the reset action shown only to instance admins,
  the confirmation that names the user, and the `login.mfa` and `mfaAcrValues`/`mfaAmrValues` fields and the break-glass
  warning in `components/access/loginSettings.js`. Verify: the tests fail before 6.2.
- [ ] 6.2 `feat(ui)`: add the reset action and the summary on the user page, and the new fields to the login settings form.
  Verify: 6.1 passes and the existing login settings tests still pass.

## 7. Errors and end-to-end

- [ ] 7.1 `test(ui)`: failing tests that map `step_up_required`, `mfa_enrolment_required` and the `409` enrolment answer to
  their messages. Verify: the tests fail before 7.2.
- [ ] 7.2 `feat(ui)`: add the error mapping. Verify: 7.1 passes.
- [ ] 7.3 `test(e2e)`: end-to-end scenario in the existing dashboard end-to-end suite: sign in with an authenticator code and
  enrol a recovery code set. Verify: the scenario passes in CI through the existing workflow. Passkey ceremonies are covered
  by the helper tests, not by the browser test.

## 8. Pull request

- [ ] 8.1 Open the code pull request with the title `feat(ui): add second factor steps to the dashboard sign-in and account
  page`, after the code pull request of `add-multi-factor-authentication` is merged, and link it. Verify: the title passes
  the commit style hook and carries no attribution line.
