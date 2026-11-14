# Spec Delta

## Purpose

The dashboard screens for the second factors of `add-multi-factor-authentication`: the second step of the sign-in, the
factor management on the account page, the step-up dialog, the grace banner, and the administrator controls. The dashboard
shows the server's decisions and never makes them.

## ADDED Requirements

### Requirement: Second step of the sign-in

When a password or LDAP sign-in answers `mfaRequired`, the sign-in dialog SHALL show a second step with the methods the
server listed. The step SHALL offer a code field, a recovery code option, and a passkey action when the server lists a
passkey. The passkey action SHALL be hidden where WebAuthn is not supported.

#### Scenario: A user with an authenticator code signs in

- **WHEN** a password sign-in answers `mfaRequired` with the method `totp`, and a valid code is entered
- **THEN** the dialog closes and the dashboard shows the signed-in state

#### Scenario: A wrong code keeps the step open

- **WHEN** a wrong code is entered
- **THEN** the step stays open with the server's message and the code field is cleared

### Requirement: Passkey sign-in button

The sign-in dialog SHALL show a passkey sign-in action above the password fields only when the server announces the
first-factor passkey switch.

#### Scenario: The button appears only when announced

- **WHEN** the login providers answer without the passkey first-factor switch
- **THEN** no passkey sign-in action is shown

### Requirement: Factor management on the account page

The account page section `Sign-in and security` SHALL list the user's factors with their names and kinds, allow renaming
and removal, and allow enrolment of an authenticator app and of a passkey. Enrolment of an authenticator app SHALL show the
seed once, as a QR code and as text, and SHALL confirm the factor with a code before it is stored.

#### Scenario: Enrolment of an authenticator app is confirmed

- **WHEN** a user starts enrolment and enters the code shown by the app
- **THEN** the factor appears in the list and the recovery codes are shown once

#### Scenario: The seed is not kept after enrolment

- **WHEN** the enrolment dialog is closed
- **THEN** the seed is no longer shown anywhere in the dashboard

### Requirement: Recovery codes shown once

The recovery codes SHALL be shown in one dialog after enrolment or regeneration, with copy and download actions, and the
dialog SHALL NOT close before the user confirms that they stored the codes.

#### Scenario: The codes are not shown again

- **WHEN** the recovery code dialog is closed and the account page is opened again
- **THEN** no recovery code is shown

### Requirement: Step-up before sensitive actions

A sensitive action SHALL open the step-up dialog only when the server answers `step_up_required`. After a successful
step-up the action SHALL be retried once. The dashboard SHALL NOT keep a clock of its own for the step-up window.

#### Scenario: The action is retried after step-up

- **WHEN** a token creation answers `step_up_required` and the user completes a second factor in the dialog
- **THEN** the token creation is retried once and its result is shown

#### Scenario: The retry does not loop

- **WHEN** the retried action answers `step_up_required` again
- **THEN** the dialog closes and the error is shown

### Requirement: Grace banner and restricted screen

When the security status reports a grace period for a user without a factor, every page SHALL show a banner with the end
date, which the user cannot dismiss. When the session scope is `enrol`, the dashboard SHALL show only the account section
and its enrolment flows.

#### Scenario: The banner shows the end of the grace period

- **WHEN** the security status reports `graceUntil` for a covered user without a factor
- **THEN** the banner shows that date on every page

#### Scenario: A restricted session sees only the enrolment screen

- **WHEN** the session scope is `enrol`
- **THEN** only the account section with the enrolment flows is reachable from the navigation

### Requirement: Administrator controls

An instance administrator SHALL see each user's factor summary, with counts and kinds and no secrets, and SHALL be able to
reset a user's second factors after a confirmation that names the user and states that the user's sessions end. Other users
SHALL NOT see these controls. The login settings form SHALL show the `login.mfa` fields and the `mfaAcrValues` and
`mfaAmrValues` fields of each OpenID Connect provider, with a warning about the break-glass token when the policy is turned on.

#### Scenario: A non-admin does not see the reset

- **WHEN** a user without instance admin rights opens another user's page
- **THEN** no reset action is shown

#### Scenario: The reset asks for confirmation

- **WHEN** an administrator starts a reset
- **THEN** a confirmation dialog names the user and the administrator must confirm before the request is sent

### Requirement: Errors are shown with their reason

The dashboard SHALL map `step_up_required`, `mfa_enrolment_required` and the `409` answer of an authenticator enrolment
without a settings key to messages that name the next step. Other errors SHALL show the server message.

#### Scenario: A TOTP enrolment without a key explains itself

- **WHEN** the server answers `409` for an authenticator enrolment
- **THEN** the dialog shows the server's reason and does not start the enrolment
