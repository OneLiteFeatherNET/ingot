# Design

## Context

Measured on `origin/main`:

- `reposilite-backend/.../configuration/shared/infrastructure/SettingsExportEndpoints.kt`: `POST /api/settings/export` and
  `POST /api/settings/import/open`, both `managerOnly("settings")`, so the right is the area node `ingot.admin.settings`. The
  HTTP reference says "Instance admin"; that is wrong and is fixed in group 1.
- `net/onelitefeather/ingot/settings/export/SettingsExportCodec.kt`: format `ingot-settings-export`, version 1, Argon2id
  (BouncyCastle) with a 16 byte salt, AES-256-GCM with a 12 byte nonce, the canonical header as additional data, a password of at
  least 12 characters, declared costs bounded on open, and one generic refusal text for every failure.
- `SharedConfigurationFacade.plainDocument()` gives the content: the saved state, secrets in clear, references (`${env:X}`)
  as written, and never the draft.
- `SettingsEncryptionCommands.kt`: `settings-reencrypt` and `settings-decrypt`, console commands for the key at rest. There is
  no console export or import.
- Audit: `SETTINGS_EXPORTED` (domain count), `SETTINGS_IMPORT_DECRYPTED`, `SETTINGS_IMPORT_REFUSED` (reason `password` or
  `file`), `SETTINGS_SECRETS_REENCRYPTED`, and `SETTINGS_CHANGED` per domain on a save.
- Frontend: `SettingsView.vue` puts `SettingsDownloadMenu` and an import button in the frame's actions slot of Overview
  (`overviewPage`). `PasswordDialog.vue` has the confirmation field and `MIN_PASSWORD_LENGTH = 12` in `settingsEncryption.js`.
  `settingsImport.js` validates and overlays, `settingsDiff.js` and `SettingsReviewSheet.vue` give the review.
  `FactoryResetModal.vue` and `store.factoryReset()` (empties every domain in the draft and saves at once) are used by the
  Danger zone. `registry.js` lists pages in `ADMIN_TREE` and has `RESERVED_SETTINGS_PAGES = ['repositories']` for page names
  that a settings domain may not take.
- Node areas that exist: `settings, users, teams, tokens, maintenance, audit, console, dashboard` (`NodePattern.ADMIN_AREAS`).
- No workflow, chart or e2e spec calls the two export routes. They are documented in the HTTP reference only.

## Goals

- One page that names the job: back up the settings, get them back, reset them.
- A passphrase is always required to export, in the dashboard and in the API, and its guidance is better than a length check.
- Recovery shows what will change before anything reaches the draft, and nothing is stored until the existing Save.
- No change that an existing script, token, file or deployment can notice.

## Non-goals

See "Out of scope" in proposal.md.

## Decisions

### D1. No fix group: the export does not leak secrets

The question was whether the plain export includes secrets in clear. Checked in the code and in the docs:

- The dashboard download calls `POST /api/settings/export` only. `downloadDraft` is a no-op and the menu disables the draft.
  The file is the codec's envelope, and `plainDocument()` is never returned unwrapped.
- `POST /api/settings/export` without a password is `400`. With a short one it is `400` and no audit event.
- The clear-text path is `GET /api/settings/domain/{name}` (per domain, a read, not an export). With `hideSettingsSecrets=false`
  and no `?secrets=hidden` it returns stored secrets, as it did in Reposilite. It needs the settings area. Scripts rely on it,
  and the drop-in rule says its default must not change.
- Values stored sealed at rest are opened before they are exported, so the file carries clear secrets inside the ciphertext, by
  design: that is what lets it restore an instance with another key.

Consequence: no `fix` group first. The trade-off is recorded as open question Q1 (a switch to hide secrets by default is
possible only as an opt-in).

### D2. The passphrase is typed in the browser and sent once to the server

Options:

- **Server side (chosen):** the browser sends `{ "password": ... }` in the body of one POST over the connection it already
  uses, and the server derives and seals. The saved state with secrets in clear exists only on the server. The dashboard never
  holds clear secrets, because it reads with `?secrets=hidden`; a client-side export would need a second endpoint that returns
  clear secrets to the browser, which widens exposure more than one passphrase in a request body does. The codec, its bounds and
  its tests exist already.
- Client side: the passphrase never leaves the browser, but the browser would need every secret in clear and a WebAssembly or
  library Argon2id (WebCrypto has none). Rejected: more exposure of the secrets, more code, and a second implementation of a
  format that must match.

Rules that follow: the passphrase lives in the dialog's component state only, is cleared when the request settles or the dialog
closes, is never put in a URL, a route query, `localStorage`, the store or an anchor, and is never logged by the server. Group 2
adds the test that proves the server side. The page states that the file is protected by this passphrase alone and that it cannot
be recovered when lost.

### D3. Reuse the existing scheme; the key at rest is not an option

The settings key at rest is 32 random bytes from the operator and encrypts per value with AES-256-GCM. It has no KDF because
it is not a password. A passphrase export needs a memory-hard KDF, which the codec already has (Argon2id, version 1.3). The
format is versioned and its header is authenticated, so a later change is a `version: 2` that the reader dispatches on. No new
format ships now.

### D4. Strength guidance without a dependency

A zxcvbn style estimator is large and its results move between versions, which breaks repeatable tests. The guidance is a pure
function of the text: below 12 characters "too short" blocks the action; 12 to 15 characters "acceptable"; 16 or more "long"; a
note that several unrelated words are stronger than a clever short one. Characters are counted as code points. The function is
tested with plain input and output. The server stays the authority on the minimum.

### D5. Recovery is draft first, with a dry-run summary

The draft is client state, so recovery cannot "apply" on the server and the existing model already gives the right checkpoint.
The page adds the step the operator lacked: after the file is opened and validated, a summary (per domain: unchanged, changed,
added; names of changed fields; counts; secrets listed as "changed" without values) from the same `settingsDiff` the review
sheet uses. Choices: "Apply to draft" or "Cancel". Then the unsaved bar and the review sheet take over. The audit trail is the
three import events plus `SETTINGS_CHANGED` on Save; no new event is needed.

Note for the operator on the page: the opened file reaches the browser with secrets in clear (placeholders would keep stored
values, which is the opposite of a restore). The summary never prints them, and the draft is discarded on leaving unless saved.

The legacy plain file keeps working and shows "This file is not encrypted" in the summary.

### D6. Placement: a settings page under System, not a new route family

`/settings/backup` is a page of the existing `SettingsView`, like `/settings/repositories`, so it shares the one configuration
store and draft with every other settings page. A route outside `SettingsView` would not see the draft. `backup` is added to
`RESERVED_SETTINGS_PAGES` (a settings domain with that id would be shadowed; none exists). The tree entry key is `backup`, label
"Backup and recovery", permission `ingot.admin.settings`, keywords "export, import, restore, reset, passphrase, download".

### D7. The factory reset moves, it does not change

`store.factoryReset()` stays as it is: it empties the draft of every domain and saves at once. Its modal, its text and its audit
trail (`SETTINGS_CHANGED` per domain) are unchanged. The page gives it a danger section (`error` roles from the theme, a
heading, the same confirmation), placed last, and a line "Export the settings first" linking to the Export section. A real
recovery from a reset is the import of an earlier export. The anchor `factory-reset` stays on the modal.

### D8. Rights: keep the settings area

Export and recovery already require `ingot.admin.settings`. Keeping it means no token, team or role changes, and the page is
visible to exactly those who could use the buttons before. A new area `backup` would need an entry in `ADMIN_AREAS`, the node
catalogue, the permission documentation and a decision about whether `settings` still implies export; a holder of the settings
area can already read every secret in clear through `GET /api/settings/domain/{name}`, so a separate area would add no
protection while that read stays. See Q3.

### D9. Overview

Overview keeps Needs attention and At a glance, loses the header actions and the Danger zone, and gets one link row to the page
for a visitor with the settings node. If #270 lands first, the link goes into the place of its Danger zone at the bottom.

## Risks and conflicts

| Open pull request or change | Touches | Conflict | Order |
| --- | --- | --- | --- |
| #270 `move-dashboard-into-administration-overview` | Overview layout, the Danger zone, the factory reset label (`fix(ui)` group), `registry.js` rail entries | Same files (`SettingsView.vue`, `SettingsHub.vue`, `registry.js`); both move or restyle the Danger zone | Its two `fix(ui)` PRs first, then its `feat(ui)` PRs, then group 3 here. Group 3 removes the Danger zone it leaves behind |
| #250 `rework-admin-header-and-repository-tabs` | `AdministrationFrame.vue` header, `SettingsView.vue` page switching, repository tabs | `SettingsView.vue` page branches and the frame's actions slot | Its header-stability fix first; the new page uses the header it leaves |
| `add-administration-overview` (change on main, code in flight) | Overview tree entry, filter keywords, the actions slot | The two header buttons are part of its Overview contract | Its code lands before group 3; this change then edits that behaviour |
| `add-online-database-backup` | Database snapshot endpoint, `maintenance` area | Name overlap only, no shared file | Independent. The page names its scope and links to the how-to |
| `retire-maven-settings-page`, `rework-administration-information-architecture` | `registry.js`, reserved page names | A one line edit in the same arrays | Whoever merges second rebases |
| Open `ui` PRs #262, #219, #214 | `MdIcon`, bottom bar label, menu trigger name | Small, in shared components | No ordering needed beyond rebasing |

## Open questions

- **Q1. Should secrets be hidden by default on the plain read?** Default: no. `hideSettingsSecrets` stays off and
  documented, because scripts read settings with it. Trade-off: an operator with a settings-area token can read every secret in
  clear without a passphrase, so the passphrase protects the file, not the instance. A later `feat` can add an opt-in switch with
  an `INGOT_` name; it is not part of this change.
- **Q2. Make the minimum length configurable?** Default: no, 12 stays a constant of the format (`MIN_PASSWORD_LENGTH`).
  Trade-off: an operator cannot demand more, but a larger floor in the dialog alone would not bind the API anyway.
- **Q3. A new area `ingot.admin.backup`?** Default: no, keep `settings`. Trade-off above (D8): more tokens to review, no gain
  while the plain read exists. If wanted, it ships as an additive `feat(identity)` where `settings` still implies it.
- **Q4. A console `settings-export` and `settings-import` for headless restore?** Default: no. The HTTP routes are scriptable
  and documented. A headless restore into an instance with a different key is a real disaster recovery need, so it is worth its
  own proposal once the page is in use.
- **Q5. Should the legacy plain file still be accepted?** Default: yes, with the notice. Old downloads exist, and refusing
  them would break recovery for people who made them.
- **Q6. Should Apply store at once instead of filling the draft?** Default: no, the draft and review stay the checkpoint.
  Trade-off: one more click for a restore, but a restore of the wrong file cannot reach other instances without a review.
- **Q7. Name of the page.** Default "Backup and recovery" as asked. The label in the tree and the page `h1` are the same.
