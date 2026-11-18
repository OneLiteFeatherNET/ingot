# Spec Delta

## Purpose

The settings are exported to an encrypted file, restored from it with a review, and reset to their defaults. The export and the
restore live on the Settings backup tab of the Maintenance page, the factory reset on System and support. The passphrase strength
hint is not delivered yet (see tasks 3.1 and 3.10); the other requirements describe what is on `main`.

## ADDED Requirements

### Requirement: Settings backup tab on Maintenance

The Maintenance page SHALL have a tab "Settings backup" at `/maintenance?tab=backup`. The tab SHALL be listed only for a visitor
who may use the settings area (`ingot.admin.settings`). It SHALL hold the sections Download and Import, the unsaved changes bar
and the review sheet, and it SHALL share the one draft of the other settings pages.

#### Scenario: Visitor with the settings area

- **WHEN** a visitor with `ingot.admin.settings` opens Maintenance
- **THEN** the tabs include Settings backup, and `/maintenance?tab=backup` shows the Download and Import sections

#### Scenario: Visitor without the settings area

- **WHEN** a visitor without `ingot.admin.settings` opens `/maintenance?tab=backup`
- **THEN** the tab is not listed and the page shows its first tab instead

#### Scenario: Shared draft

- **WHEN** a visitor changes a value on a settings page and opens Settings backup
- **THEN** the unsaved changes bar still counts the change

### Requirement: Export always asks for a passphrase

The dashboard SHALL offer no download of the settings without a passphrase. The Download action SHALL ask for the passphrase
twice, SHALL refuse one shorter than 12 characters and a confirmation that does not match, and SHALL send one
`POST /api/settings/export` whose body holds the passphrase. The file SHALL carry the header `format: ingot-settings-export`.
The dashboard SHALL NOT offer an unencrypted download.

#### Scenario: Short passphrase

- **WHEN** the visitor enters 11 characters
- **THEN** the dialog sends no request and says the passphrase is too short

#### Scenario: Mismatch

- **WHEN** the confirmation differs from the passphrase
- **THEN** the dialog sends no request

#### Scenario: Export

- **WHEN** the visitor enters the same passphrase of 12 or more characters twice and confirms
- **THEN** one request is sent and an encrypted file with the header `format: ingot-settings-export` is downloaded, and one
  `SETTINGS_EXPORTED` event records the actor and the number of domains

#### Scenario: Request without a passphrase

- **WHEN** a client calls `POST /api/settings/export` without a `password`
- **THEN** the server answers `400` and records no event

### Requirement: The passphrase is not kept or logged

The passphrase SHALL NOT be stored in a store, a route, a URL, browser storage or an anchor, and SHALL be cleared when the request
settles or the dialog closes. The server SHALL NOT write the passphrase or the ciphertext to a log line, an audit event or an
error body. A successful export SHALL carry `Cache-Control: no-store`.

#### Scenario: After the request

- **WHEN** an export succeeds or fails
- **THEN** the dialog holds no passphrase, and no log line or audit event contains it

### Requirement: Import previews before it applies

Import SHALL take a settings file. When the file carries the `format` header, the dashboard SHALL ask for its passphrase and open
it through `POST /api/settings/import/open`. The file SHALL be validated against the schemas and overlaid on the draft, and the
review sheet SHALL list the changed values of each domain against the saved state, where each change can be reverted. A file that
fails validation SHALL list its errors and leave the draft unchanged.

#### Scenario: Preview

- **WHEN** the visitor imports an export that changes one field of one domain
- **THEN** the review sheet lists that change, and nothing is stored

#### Scenario: Wrong passphrase

- **WHEN** the passphrase does not match the file
- **THEN** the dialog shows one generic message, the draft is unchanged, and `SETTINGS_IMPORT_REFUSED` is recorded with reason
  `password`

#### Scenario: Invalid file

- **WHEN** the file fails validation
- **THEN** the errors are listed and the draft is unchanged

### Requirement: Storing stays with Save

The imported values SHALL reach the server only through the ordinary Save of the unsaved changes bar, with its review and its
conflict check. Reverting a change in the review SHALL change the draft only.

#### Scenario: Save after import

- **WHEN** the visitor imports a file and chooses Save
- **THEN** the values are stored with the existing `SETTINGS_CHANGED` audit event per domain

### Requirement: Factory reset on System and support

The factory reset SHALL stay on the System and support page in its Danger zone, behind its existing confirmation. The Settings
backup tab SHALL link to it with the text that sets every setting back to its default. Overview and the Settings backup tab SHALL
NOT show the reset action itself. The reset SHALL behave as before: it empties every domain and saves.

#### Scenario: Confirmation

- **WHEN** the visitor chooses the factory reset and cancels the confirmation
- **THEN** no domain changes and nothing is saved

### Requirement: Overview without backup actions

Overview SHALL NOT show "Import settings", "Download as JSON" or the factory reset in its header. The Download and Import actions
SHALL be reachable on the Settings backup tab only.

#### Scenario: Header

- **WHEN** a visitor with the settings area opens `/settings`
- **THEN** the header has no Import settings and no Download as JSON

### Requirement: Passphrase strength hint

The passphrase dialog for an export SHALL show a strength line computed from the text alone, without a dictionary or a
third-party library: "too short" below 12 characters, "acceptable" for 12 to 15 characters and "long" for 16 or more, counting
code points. The dialog SHALL recommend a passphrase of several words and a password manager, SHALL offer a show and hide control
for the passphrase, and SHALL use `autocomplete="new-password"`. The minimum stays 12 characters.

#### Scenario: Strength line

- **WHEN** the visitor types a passphrase of 14 characters
- **THEN** the dialog shows "acceptable", and the export is sent once the confirmation matches

#### Scenario: Show and hide

- **WHEN** the visitor chooses the show control
- **THEN** the passphrase is shown as text until the control is chosen again

### Requirement: Same file format and routes

The change SHALL NOT alter the export file format, its version, the routes `POST /api/settings/export` and
`POST /api/settings/import/open`, their rights, their request and response bodies, or any audit action. A file exported before
this change SHALL open after it, and a plain JSON file without a `format` header SHALL still be accepted by Import and go through
the same review.

#### Scenario: Older file

- **WHEN** a file exported by the previous build is imported with its passphrase
- **THEN** it opens and the review sheet lists its changes
