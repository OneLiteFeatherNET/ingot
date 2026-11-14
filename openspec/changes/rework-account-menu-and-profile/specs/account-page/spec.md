# Spec Delta

## Purpose

"My account" is ordered in four sections (Profile, Sign-in and security, Tokens, Your access). Each value is shown once. Your access
tells the signed-in principal what it may do, read only, and leaves a place for two-step sign-in.

## ADDED Requirements

### Requirement: Four sections in order

The account page SHALL show four sections, in this order, each with a heading: Profile, Sign-in and security, Tokens and Your access.
The must-change-password banner and the password card SHALL keep their current behaviour: when a new password is required, the password
card moves to the top of the page.

#### Scenario: Section order

- **WHEN** a signed-in user opens "My account" at 1440 pixels
- **THEN** the headings Profile, Sign-in and security, Tokens and Your access appear in that order in the document

#### Scenario: Password first when required

- **WHEN** the server requires a new password
- **THEN** the password card is the first section, and Your access is still drawn below Tokens

### Requirement: Each value once on the page

The Profile section SHALL show the display name once. It SHALL show the username only when it differs from the display name. A value SHALL NOT
appear in two rows or two sections of the page.

#### Scenario: Equal name and username

- **WHEN** the display name and the username are both "alice"
- **THEN** the Profile section shows "alice" once and no Username row

#### Scenario: Teams not repeated

- **WHEN** a principal is in the team "maintainers"
- **THEN** "maintainers" is listed in Your access and not in Profile

### Requirement: Placeholder for two-step sign-in

The Sign-in and security section SHALL contain a named slot, `data-ingot-slot="add-multi-factor-authentication"`, that says two-step sign-in
is not set up. The slot SHALL NOT offer a control that changes anything in this change. The change `add-multi-factor-authentication`
fills the slot and keeps its name.

#### Scenario: Slot present

- **WHEN** a signed-in user opens "My account" on a cookie session
- **THEN** the element with `data-ingot-slot="add-multi-factor-authentication"` exists inside the Sign-in and security section

### Requirement: Your access lists what the principal may do

The Your access section SHALL show, for the signed-in principal only:

- whether the principal is an instance administrator;
- its teams, each with its direct parent teams;
- its permissions in plain language, the allowed ones first and the denied ones in a separate list;
- its repository access: role, repository, path prefix, and the team the access comes through.

A token session SHALL state that the token narrows the list. Each permission and each repository access SHALL have an "Explain" link.

#### Scenario: Team with a parent

- **WHEN** the principal is in "releases-maintainers", which inherits from "maintainers"
- **THEN** Your access lists "releases-maintainers" with "inherits from maintainers"

#### Scenario: Permission in plain language

- **WHEN** the principal holds `ingot.repository.releases.deploy`
- **THEN** Your access shows "Deploy to releases" with the node text available as secondary text

#### Scenario: Denial listed apart

- **WHEN** the principal holds a denial of `ingot.repository.releases.delete`
- **THEN** Your access lists it in the denied list as "Denied: delete from releases"

#### Scenario: Token narrows

- **WHEN** the session is a token session whose token allows fewer nodes than its owner
- **THEN** Your access shows the note that the token narrows the list, and lists only the nodes the token allows

### Requirement: Read only, no self-escalation

Your access SHALL NOT contain a control that changes a team, a node, a grant, a token, a password or the instance administrator flag.
The page SHALL request only the signed-in principal's own summary and SHALL NOT take a principal from the address, the query string or a form.

#### Scenario: No change controls

- **WHEN** a full administrator opens Your access
- **THEN** the section contains no input, no form and no button other than the Explain links

#### Scenario: Own summary only

- **WHEN** the page requests the access summary
- **THEN** the request carries no principal parameter, and the answer is the summary of the signed-in principal

### Requirement: Explain link to the check

The Explain link of a permission or of a repository access SHALL open the check page at `/access/check` with the principal and the node filled in
when the signed-in principal may use the Access area. Otherwise it SHALL open the how-to page that explains the check.

#### Scenario: Administrator explains

- **WHEN** a principal with `ingot.admin.users` opens Explain on `ingot.repository.releases.deploy`
- **THEN** the browser opens `/access/check` with the principal and the node set to the signed-in principal and that node

#### Scenario: Other principal explains

- **WHEN** a principal without an Access area node opens Explain
- **THEN** the browser opens the how-to page for the check and not the check page

### Requirement: Plain language for every node form

Each node form SHALL have a plain-language sentence: repository actions, admin areas, `ingot.admin.*`, and artifact nodes with and without a
repository. A node form the page does not know SHALL be shown verbatim under "Other", and SHALL NOT be dropped.

#### Scenario: Unknown node shown

- **WHEN** the summary lists a node the page has no sentence for
- **THEN** the node text is shown under "Other" and nothing else changes

### Requirement: Extension by scope type

The repository access list SHALL be built from scopes that carry a type. A scope of a type the page does not know SHALL be shown as one
plain line with its text, so a server that adds a scope type does not break the page. Adding a scope type SHALL NOT change the layout of
the section.

#### Scenario: Unknown scope type

- **WHEN** the summary carries a scope of type "namespace" that the page does not draw
- **THEN** the page shows one line for it under Repository access, and the other rows are unchanged

### Requirement: States of Your access

While the summary loads, Your access SHALL show a loading line and the other sections SHALL NOT wait for it. A server that answers 404 to
the summary (an older server) SHALL show "The access summary is not available on this server". Any other failure SHALL show the error
text with a retry control, and that retry SHALL be the only control in the section.

#### Scenario: Older server

- **WHEN** the summary answers 404
- **THEN** Your access shows "The access summary is not available on this server" and the Profile, password and Tokens sections are unchanged

### Requirement: Accessibility

Each section SHALL have a heading that names it. The Explain links SHALL have an accessible name that includes the node or the repository
they explain. The page SHALL work at 320 pixels without a horizontal scroll.

#### Scenario: Explain name

- **WHEN** a screen reader lists the links of Your access
- **THEN** each link is named with the node it explains, for example "Explain ingot.repository.releases.deploy"

#### Scenario: Phone width

- **WHEN** the page is drawn at 320 pixels
- **THEN** no element of the page is wider than the window, and the four sections stack in one column
