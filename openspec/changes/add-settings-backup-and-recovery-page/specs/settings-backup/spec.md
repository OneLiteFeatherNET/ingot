# Spec Delta

## Purpose

The settings can be exported to an encrypted file, restored from it with a preview, and reset, all from one page.

## ADDED Requirements

### Requirement: Backup and recovery page

The Administration SHALL have a page at `/settings/backup` titled "Backup and recovery", listed in the Administration tree under
System for a visitor who may use the settings area (`ingot.admin.settings`) and for nobody else. It SHALL hold the sections
Export, Recovery and Factory reset, in this order. It SHALL share the draft of the other settings pages. Its root element SHALL
carry `data-ingot-component="backup-page"`.

#### Scenario: Visitor with the settings area

- **WHEN** a visitor with `ingot.admin.settings` opens Administration
- **THEN** the tree lists Backup and recovery under System, and `/settings/backup` shows Export, Recovery and Factory reset

#### Scenario: Visitor without the settings area

- **WHEN** a visitor without `ingot.admin.settings` opens `/settings/backup`
- **THEN** the page shows the no access state and the tree does not list it

#### Scenario: Shared draft

- **WHEN** a visitor changes a value on another settings page and opens Backup and recovery
- **THEN** the unsaved bar still counts the change

### Requirement: Export always asks for a passphrase

The dashboard SHALL offer no download of the settings without a passphrase. The Export action SHALL ask for the passphrase twice,
SHALL refuse one shorter than 12 characters (counted as code points) and a pair that does not match, and SHALL show a strength
line (too short, acceptable, long) computed from the text alone. The request SHALL be one `POST /api/settings/export` whose body
holds the passphrase.

#### Scenario: Short passphrase

- **WHEN** the visitor enters 11 characters
- **THEN** the dialog does not send a request and says the passphrase is too short

#### Scenario: Mismatch

- **WHEN** the confirmation differs from the passphrase
- **THEN** the dialog does not send a request

#### Scenario: Export

- **WHEN** the visitor enters the same 16 character passphrase twice and confirms
- **THEN** one request is sent, a file with the header `format: ingot-settings-export` is downloaded, no secret value appears in
  it as text, and one `SETTINGS_EXPORTED` event records the actor and the number of domains

#### Scenario: Request without a passphrase

- **WHEN** a client calls `POST /api/settings/export` without a `password`
- **THEN** the server answers `400` and records no event

### Requirement: The passphrase is not kept

The passphrase SHALL NOT be stored in a store, a route, a URL, browser storage or an anchor, and SHALL be cleared when the request
settles or the dialog closes. The server SHALL NOT write it to a log, an audit event or a response.

#### Scenario: After the request

- **WHEN** an export succeeds or fails
- **THEN** the dialog holds no passphrase, and no log line or audit event contains it

### Requirement: Recovery previews before it applies

Recovery SHALL take a file, ask for the passphrase when the file carries the `format` header, open it through
`POST /api/settings/import/open`, validate it against the schemas, and show a summary per domain (unchanged, changed, added), with
the names of changed fields and counts and without any value. A file without the `format` header SHALL still be accepted and
SHALL show the notice "This file is not encrypted".

#### Scenario: Preview

- **WHEN** the visitor opens a valid export that changes one field of one domain
- **THEN** the summary lists that domain as changed with the field name, the others as unchanged, no secret appears in the text,
  and the draft is unchanged

#### Scenario: Wrong passphrase

- **WHEN** the passphrase does not match the file
- **THEN** the page shows the one generic message, keeps the file selected, changes nothing, and `SETTINGS_IMPORT_REFUSED` is
  recorded with reason `password`

#### Scenario: Legacy file

- **WHEN** the visitor opens a plain JSON file without a `format` header
- **THEN** the summary is shown with the notice that the file is not encrypted

### Requirement: Apply fills the draft only

"Apply to draft" SHALL be the only recovery action that changes the draft. It SHALL make no request and SHALL leave storing to the
ordinary Save, its review and its conflict check.

#### Scenario: Apply

- **WHEN** the visitor chooses "Apply to draft"
- **THEN** the draft holds the file's values, the unsaved bar counts them, and nothing is stored until Save

### Requirement: Factory reset on the page

The Factory reset SHALL be a section of the page with the danger styling and the existing confirmation, and SHALL link to the
Export section with the text that an export should come first. It SHALL behave as before: it empties every domain and saves.
Overview SHALL NOT show it.

#### Scenario: Confirmation

- **WHEN** the visitor chooses Factory reset and cancels the confirmation
- **THEN** no domain changes and nothing is saved

### Requirement: Same file format and routes

The change SHALL NOT alter the export file format, its version, the two routes, their rights, their request and response bodies,
or any audit action. A file made before the change SHALL open after it.

#### Scenario: Older file

- **WHEN** a file exported by the previous build is opened on the new page with its passphrase
- **THEN** it opens and the summary is shown
