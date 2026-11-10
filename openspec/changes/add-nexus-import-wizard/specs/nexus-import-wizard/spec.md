# Spec Delta

## Purpose

An instance administrator can move a Nexus Repository 3 installation into Ingot through one guided dashboard page: connect,
discover, map, dry run with a loss report, sync with progress, verify, and a cut-over checklist. The page is a first-party page of the dashboard and a client of the
`nexus-import` plugin API, and appears only while that plugin is enabled.

## ADDED Requirements

### Requirement: Page availability

The dashboard SHALL offer `/settings/migration` as an entry of Administration, System, only when `enabledPlugins` in
`GET /api/frontend/plugin-ui` lists `nexus-import` and the visitor is an instance administrator. The page SHALL be part of the
dashboard and SHALL NOT be contributed through the plugin extension point. Otherwise the path SHALL show the not-found page.

#### Scenario: Plugin enabled and visitor permitted

- **WHEN** `enabledPlugins` contains `nexus-import` and an instance administrator opens the System group
- **THEN** a Migration entry is listed and opens `/settings/migration`

#### Scenario: Plugin disabled

- **WHEN** `enabledPlugins` does not contain `nexus-import` and a visitor opens `/settings/migration`
- **THEN** the dashboard shows its not-found page and no entry is listed

#### Scenario: Visitor without the permission

- **WHEN** a signed-in visitor who is not an instance administrator opens the page
- **THEN** no entry is listed and the API calls are not made

#### Scenario: Extension point switched off

- **WHEN** `INGOT_LOCAL_PLUGINUI=false` and the plugin is enabled
- **THEN** the Migration entry is still listed

### Requirement: Step order and gating

The wizard SHALL have the steps Connect, Discover, Mapping, Dry run, Sync, Verify and Cut-over, each at its own address under
`/settings/migration/<step>`. A step SHALL open only when its input exists (a connection test, a discovery, a mapping, an accepted dry run, an import job, a
verify job). A closed step SHALL redirect to the first open step with a message, and the stored jobs SHALL decide the step after a reload.

#### Scenario: Skipping ahead

- **WHEN** a connection test succeeded but no discovery has finished and the visitor opens the Sync address
- **THEN** the page redirects to Discover and says which step has to be finished first

#### Scenario: Reload during a sync

- **WHEN** the visitor reloads the page while an import job is unfinished
- **THEN** the Sync step opens and shows that job, read from the API

#### Scenario: Reload before any job

- **WHEN** the visitor reloads on the Mapping step and no job exists
- **THEN** the Connect step opens, because the mapping is held in the page only

### Requirement: Credentials stay out of the browser

A literal password SHALL be held only in memory outside reactive state while the wizard is open, sent only in bodies of calls to the
Ingot API, and dropped on leaving the wizard, sign-out, `401` or request. A secret reference SHALL be offered instead. The page
MUST NOT contact Nexus, and MUST NOT write the password to storage, the address, a log or the DOM. It SHALL have no TLS trust choice.

#### Scenario: Successful connect

- **WHEN** the visitor submits valid credentials
- **THEN** the page shows the Nexus version and what the credential may read, and the password field is empty

#### Scenario: Password after a reload

- **WHEN** the visitor reloads and then starts a verify job
- **THEN** the page asks for the password again before it sends the request

#### Scenario: Credentials needed

- **WHEN** a job is `PAUSED` with the reason `CREDENTIALS_NEEDED`
- **THEN** the page shows a form that sends the password with `PUT /api/migration/nexus/jobs/{id}/credentials` and no other call carries it

#### Scenario: Untrusted certificate

- **WHEN** the connection test fails because the certificate chain is not trusted
- **THEN** the page shows the problem and tells the operator to provide the CA through `INGOT_EXTRA_CA_CERTS`

### Requirement: Discovery and mapping

Discover SHALL list repositories and the format histogram from `POST /api/migration/nexus/discover`. Mapping SHALL set the target
name, the include flag and the conflict policy (`refuse` preselected, `abort`, `overwrite` after confirmation), and warn about empty or
duplicate names. Taken and reserved names SHALL come from the dry run report and the job start answer.

#### Scenario: Unsupported format

- **WHEN** discovery contains an npm repository
- **THEN** it is listed with its size, excluded and not selectable

#### Scenario: Duplicate name

- **WHEN** two repositories get the same target name
- **THEN** both rows show a warning and the dry run cannot be started

#### Scenario: Taken name

- **WHEN** the dry run report names a collision and proposes `releases-nexus`
- **THEN** the page offers to take the proposal, and taking it makes the acceptance stale

#### Scenario: Overwrite needs confirmation

- **WHEN** the operator chooses `overwrite`
- **THEN** a dialog says that existing files will be replaced and the choice applies only after confirmation

### Requirement: Dry run and accepted loss report

Dry run SHALL start a `DRY_RUN` job through `POST /api/migration/nexus/jobs` and show `GET .../jobs/{id}/report` as a filterable table
with JSON and CSV downloads. Sync SHALL stay disabled until the operator accepts that report, and a change of selection, target names
or conflict policy SHALL make the acceptance stale. The acceptance is a page gate and SHALL NOT be stored on the server.

#### Scenario: Report shown before any write

- **WHEN** the dry run finishes
- **THEN** the table, the status counts, the estimated size and the blockers are shown and nothing was written

#### Scenario: Mapping changed after acceptance

- **WHEN** the operator changes a target name
- **THEN** the acceptance is marked stale and the Sync start is disabled with the reason stated

#### Scenario: Blockers present

- **WHEN** the dry run reports a blocker
- **THEN** Sync cannot be started and the blocker links to its report row

### Requirement: Sync progress survives reload

Sync SHALL start an `IMPORT` job and show progress from `GET /api/migration/nexus/jobs/{id}`, with pause, resume and cancel, the
conflicts, and a paused job's reason in words. On load it SHALL attach to the unfinished import job. The polling interval SHALL start at two
seconds, grow to at most fifteen while nothing changes, and stop at a terminal state.

#### Scenario: Reload during a sync

- **WHEN** the visitor reloads while a job is running
- **THEN** the page attaches to that job and shows its current progress

#### Scenario: Read-only pause

- **WHEN** the job is `PAUSED` with the reason `READ_ONLY`
- **THEN** the page says that the instance is read-only and that the job resumes by itself, and offers no credential form

#### Scenario: Hidden tab

- **WHEN** the tab is hidden
- **THEN** polling pauses, and it polls once at once when the tab is visible again

#### Scenario: Poll failure

- **WHEN** a poll fails with a server error
- **THEN** a polite note says progress could not be read and polling continues at the longest interval

#### Scenario: Cancel needs confirmation

- **WHEN** the visitor presses Cancel
- **THEN** a dialog asks for confirmation and no request is sent until it is confirmed

### Requirement: Verify and cut-over checklist

Verify SHALL start a `VERIFY` job and show `VERIFIED` or `MISMATCH` per repository. Cut-over SHALL show Freeze Nexus writes, Verify all
repositories and Switch clients. The verify item SHALL derive from the newest verify job, SHALL NOT be tickable while a repository is
not `VERIFIED`, and the other ticks SHALL stay in the page. Maven and Gradle snippets SHALL carry no secret. No delta or alias control.

#### Scenario: Mismatch blocks the checklist

- **WHEN** one repository is `MISMATCH`
- **THEN** the verify item cannot be ticked and the repository is named as blocking

#### Scenario: Conflicted repository blocks the checklist

- **WHEN** a repository ended `CONFLICTED` under the policy `refuse`
- **THEN** it is not `VERIFIED`, the verify item cannot be ticked and its conflicts are listed

#### Scenario: Verify item after a reload

- **WHEN** the visitor reloads the Cut-over step after every repository is `VERIFIED`
- **THEN** the verify item is shown as done, derived from the verify job, and the manual items are unticked

### Requirement: Nothing is written to Nexus

The wizard MUST NOT call any Nexus address and MUST NOT offer an action that writes to Nexus. The freeze of Nexus writes SHALL be
shown as instructions only.

#### Scenario: Freeze step

- **WHEN** the operator opens the Cut-over step
- **THEN** the freeze item is text with instructions and no button calls Nexus

### Requirement: Accessible structure and status

Every step SHALL have one heading inside the main landmark, a step list marking the current step with `aria-current`, labelled
form controls with errors linked by `aria-describedby`, tables with captions and header scopes, and status shown by text and
icon as well as colour. A change of step SHALL move focus to the step heading.

#### Scenario: Step change moves focus

- **WHEN** the visitor goes from Connect to Discover
- **THEN** focus is on the Discover heading and the step list marks Discover as current

#### Scenario: Form error

- **WHEN** a submit fails validation
- **THEN** focus moves to an error summary that links to each invalid field

### Requirement: Accessible progress

The overall progress SHALL be exposed as a labelled progress element with its value, and a polite live region SHALL announce
it at most once every ten seconds, only when the state changed or the percentage moved by ten points or more. Animation SHALL
stop under `prefers-reduced-motion: reduce`.

#### Scenario: Announcements are throttled

- **WHEN** progress moves by one percent every second
- **THEN** the live region is updated no more than once in ten seconds

#### Scenario: Unknown total

- **WHEN** the job reports no total
- **THEN** the progress element is indeterminate and no percentage is announced

### Requirement: Error display

A failed call SHALL show the problem title and detail beside the action that failed, keep the entered values, and never show or
log a secret. A `401` SHALL use the existing sign-in flow and return to the same step, and a `403` SHALL show an access-denied
state without data.

#### Scenario: Problem body

- **WHEN** starting the dry run answers a problem document
- **THEN** its title and detail appear next to the button and the form is unchanged

#### Scenario: Session expired

- **WHEN** a call answers `401`
- **THEN** the visitor signs in and returns to the same step
