# Proposal

## Why

The maintainer asked for two things: downloading the settings should get its own menu mode, recovery should get one too, and
before that a password must always be entered for the encryption.

What exists today, checked on `main`:

- Administration > Overview carries two buttons in its header, "Import settings" and "Download as JSON", and the Danger zone at
  the bottom holds "Factory reset". Backup, restore and reset are three actions of one job, and they sit in two places and under
  a name that says nothing about it. "Download as JSON" in particular promises a plain file. It does not deliver one.
- The download is already encrypted and already asks for a password. `POST /api/settings/export` seals the saved settings with
  Argon2id and AES-256-GCM, a password of at least 12 characters is required, the dashboard asks for it twice, and the file is a
  versioned, authenticated envelope (`format: ingot-settings-export`, `version: 1`). So the maintainer's "always a password" is
  true for the dashboard today, but the label, the place and the guidance do not show it: the dialog says "at least 12
  characters" and nothing more, and the menu still offers the word "JSON".
- Recovery is the import of that file into the draft. The dashboard decrypts it through `POST /api/settings/import/open`,
  validates it against the schemas, overlays it on the draft, and the operator reviews and saves it like any edit. That is a
  dry run already, but it is invisible: the operator sees "Import settings" and a file picker, then an unsaved bar.
- Nothing in the dashboard export leaves the server with a secret in clear. The plain text is inside the ciphertext. The one
  clear-text read of secrets is not an export: `GET /api/settings/domain/{name}` returns stored values, secrets included, unless
  `hideSettingsSecrets` is on (default off, the behaviour scripts have always had) or the dashboard asks for `?secrets=hidden`.
  That is a documented script contract and the drop-in rule keeps it. This proposal flags it and leaves it alone, see the
  open questions.

## What Changes

- **A page "Backup and recovery"** in Administration, group System, at `/settings/backup`, with its own entry in the
  Administration tree and in the filter. Three sections:
  - **Export:** one action, "Export settings". It always asks for a passphrase, twice, with guidance, and downloads the
    encrypted file. There is no unencrypted download in the dashboard, and no menu that implies one.
  - **Recovery:** choose a file, enter its passphrase, see a summary of what the file would change (per domain: unchanged,
    changed, added, with counts and names of fields, never values), then "Apply to draft". Applying fills the draft only. The
    existing review sheet and Save are the checkpoint, so the existing `SETTINGS_CHANGED` audit event and the save conflict
    check carry over.
  - **Factory reset:** moves here, with the danger styling, behind its existing confirmation, with a line that points at Export
    first.
- **Overview loses the two header buttons.** "Import settings" and "Download as JSON" leave the header. Overview keeps its
  Needs attention and At a glance content and gets one link to the new page. The Danger zone leaves Overview; the factory reset
  lives on the new page only.
- **Passphrase guidance.** A length-based hint that works offline (no dictionary or third-party library): the minimum stays 12
  characters, the dialog recommends a passphrase of several words and a password manager, shows a plain-language strength line
  (too short, acceptable, long), offers a show and hide control, and uses `autocomplete="new-password"`.
- **No new cryptography.** The envelope, Argon2id (64 MiB, three passes, one lane, declared costs bounded at 256 MiB, ten passes,
  four lanes) and AES-256-GCM with the header as additional data are reused unchanged. See design.md D3 for why the key of
  settings encryption at rest is not suitable.
- **A guard test.** The passphrase never appears in a log line, an audit event or an error body, asserted through a captured
  appender and the audit sink.
- **Documentation.** A page for backup and recovery, and a correction of the rights named for the two export routes in the
  HTTP reference (it says instance admin, the code requires the settings area).

Nothing is BREAKING. No endpoint, request, response, permission node, environment variable, configuration key, volume or file
format changes. A file exported before this change opens after it, and a file exported after it opens in the previous build.
The legacy plain JSON file (no `format` header) is still accepted by recovery, with a notice that it is not encrypted.

Ships as: `docs(gitbook)` for the rights fix, `test(settings)` for the guard, `feat(ui)` for the page, `docs(gitbook)` for the
page description. The proposal pull request is `docs(openspec): propose add-settings-backup-and-recovery-page`. There is no
`fix` group, because no secret leaves the dashboard export in clear (design.md D1 shows the check).

### Code pull request titles

1. `docs(gitbook): name the settings area for the settings export routes`
2. `test(settings): guard that the export passphrase never reaches a log or an audit event`
3. `feat(ui): add a backup and recovery page to administration`
4. `docs(gitbook): describe the backup and recovery page`

## Out of scope

- A new encryption scheme, a new file format version, or a configurable minimum length.
- A new permission area (`ingot.admin.backup`). Default is the settings area, see the open questions.
- Changing `GET /api/settings/domain/{name}`, `hideSettingsSecrets`, or any script-facing route.
- A console or command line export and import (`settings-export`). Only `settings-reencrypt` and `settings-decrypt` exist and
  they stay as they are.
- Database snapshots, the subject of `add-online-database-backup`. The page says in one sentence that it covers the shared
  settings only and links to the how-to on backing up the database.
- Scheduling, retention, or storing exports on the server.
- Moving the Dashboard into Overview (#270) and the header and tab rework (#250).
