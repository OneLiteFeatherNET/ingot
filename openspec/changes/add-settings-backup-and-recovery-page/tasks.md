# Tasks

Each group is one pull request, one Conventional Commits type each, in the order listed. Every test is written first and seen to
fail. Every test follows F.I.R.S.T.: no sleeps, no system clock (inject `Clock`, the dashboard clock key), no network, a fresh
store, fake client and router per test, assertions with messages. The codec tests use a fixed salt, a fixed nonce and a small
Argon2 cost. Heavy jobs run through `heavy`: Gradle with `--no-daemon --max-workers=2`, vitest with `--maxWorkers=2`, Playwright
with `--workers=2`. Code pull requests contain no file under `openspec/`. Task checkboxes are ticked in a follow-up pull request,
not in the code pull requests.

## 1. Rights in the HTTP reference (`docs(gitbook)`)

- [ ] 1.1 `docs(gitbook): name the settings area for the settings export routes`: in `docs/gitbook/reference/http-api.md`
  (the rows for `/api/settings/export` and `/api/settings/import/open`) replace "Instance admin" by the settings area
  (`ingot.admin.settings`), as the code requires. Verify: the text matches `managerOnly("settings")` in
  `SettingsExportEndpoints.kt`.
- [ ] 1.2 Open the pull request titled `docs(gitbook): name the settings area for the settings export routes`.

## 2. Passphrase never leaks (`test(settings)`)

- [ ] 2.1 `test(settings): guard that the export passphrase never reaches a log or an audit event`: in the backend, with a captured
  appender and the audit sink as a list, export with a 12 character passphrase and open with a wrong one, and assert that no log
  line, no audit event (including `details`) and no error body contains the passphrase or the ciphertext. Add the case of a
  passphrase with 11 characters (`400`, no audit event). Verify: it passes on `main`; it is the guard, and it fails if a request
  body logger is ever added to these two routes.
- [ ] 2.2 In the same group, a test that `POST /api/settings/export` answers `400` without a `password`, and carries
  `Cache-Control: no-store` on success. Verify: passes; guards the always-ask rule of the API.
- [ ] 2.3 Open the pull request titled `test(settings): guard that the export passphrase never reaches a log or an audit event`.

## 3. The page (`feat(ui)`)

Red tests first:

- [ ] 3.1 `src/components/settings/passphraseGuidance.test.js`: below 12 characters "too short" and not submittable, 12 to 15
  "acceptable", 16 or more "long", code points counted (an emoji is one), mismatch of the confirmation, empty value. Verify: it
  fails, the module does not exist.
- [ ] 3.2 `src/components/settings/BackupView.test.js` with a fake client: Export opens the dialog and sends one POST with the
  passphrase and nothing else; no request without a passphrase; the passphrase is cleared after success and after failure and is
  in no store, route or storage (assert on `localStorage`, the store state and the current route); the file name uses the
  injected clock. Verify: it fails.
- [ ] 3.3 Same file, Recovery: a file picker, the passphrase prompt only for a file with the `format` header, the summary lists
  unchanged, changed and added per domain with field names and counts and no value (assert a planted secret string is absent from
  the rendered text), "Apply to draft" fills the draft and makes no call, Cancel leaves the draft untouched, a wrong passphrase
  shows the generic message and keeps the file, a legacy plain file shows "This file is not encrypted". Verify: it fails.
- [ ] 3.4 Same file, Factory reset: the section has the danger styling and the existing confirmation, "Export first" links to the
  Export section, the reset calls `store.factoryReset()` once and only after confirmation. Verify: it fails.
- [ ] 3.5 `src/navigation/administration.test.js`: the entry `backup` exists under System for a visitor with the settings node,
  is absent without it, is found by the filter words "export", "restore", "reset", "passphrase", `currentTreeKey` gives
  `page:backup` for `/settings/backup`, and `backup` is a reserved page name. Verify: it fails.
- [ ] 3.6 `SettingsView.test.js` and `SettingsHub.test.js`: Overview has no "Import settings", no "Download as JSON", no Danger
  zone, and has one link to Backup and recovery for the settings node; `/settings/backup` renders the page inside the one shared
  store (a draft made on another settings page is still counted). Verify: it fails.
- [ ] 3.7 `e2e/backup.spec.js`: against a seeded server, export with a passphrase and read the downloaded file (header
  `ingot-settings-export`, no secret value as text), import it back through the page and see the summary with zero changes, change
  a setting, import again and see one changed field, apply, see the unsaved bar, save; a wrong passphrase shows the message; the
  page works by keyboard alone. Verify: it fails.
- [ ] 3.8 `e2e/accessibility.spec.js`: axe on `/settings/backup` in its resting state, with the export dialog open, with the
  summary shown and with the reset confirmation open, in the light and the dark theme, no violation. Verify: it fails.
- [ ] 3.9 `e2e/layout.spec.js`: at 320, 600 and 1440 pixels the page has no horizontal scroll, the dialog fits, and the entry is
  in the bottom navigation overflow and the side tree like the other pages. Verify: it fails.

Implementation:

- [ ] 3.10 `feat(ui)`: add `passphraseGuidance.js` and use it in `PasswordDialog.vue` (hint line, show and hide control,
  `autocomplete="new-password"`). Verify: 3.1 passes.
- [ ] 3.11 `feat(ui)`: add `BackupView.vue`, the route `/settings/backup`, the `backup` entry in `registry.js` (System, settings
  node, keywords), `RESERVED_SETTINGS_PAGES`, and the anchors `backup-page`, `backup-export`, `backup-recovery`,
  `backup-reset` in `src/anchors/registry.json` and `docs/gitbook/reference/dashboard-anchors.md`. Verify: 3.2, 3.3, 3.4 and 3.5
  pass.
- [ ] 3.12 `feat(ui)`: remove the two header buttons, `SettingsDownloadMenu.vue` and the Danger zone from Overview, add the link
  row. Delete the files nothing uses any more and their tests. Verify: 3.6 passes; `npm run lint` and the style gate pass.
- [ ] 3.13 Screenshots: before and after of Overview and of the new page (resting, export dialog, summary, reset confirmation) at
  320, 600 and 1440 pixels, light and dark, pushed to the `pr-screenshots` branch and linked in the pull request.
- [ ] 3.14 Verify the group with the targeted vitest files, `e2e/backup.spec.js`, `e2e/accessibility.spec.js` and
  `e2e/layout.spec.js`, through `heavy`.
- [ ] 3.15 Open the pull request titled `feat(ui): add a backup and recovery page to administration`.

## 4. Documentation (`docs(gitbook)`)

- [ ] 4.1 Add `docs/gitbook/how-to/back-up-and-restore-settings.md` (export, store the file and the passphrase apart, restore,
  what a reset does, that the file holds secrets in clear inside the encryption, that the passphrase cannot be recovered) and
  link it from `docs/gitbook/how-to/back-up-and-restore.md`, `settings-encryption.md` ("Export and import") and
  `docs/gitbook/reference/dashboard-pages.md`.
- [ ] 4.2 Update `docs/gitbook/reference/dashboard-pages.md` and the Overview description for the removed buttons.
- [ ] 4.3 Open the pull request titled `docs(gitbook): describe the backup and recovery page`.

## 5. Proposal

- [ ] 5.1 `docs(openspec): propose add-settings-backup-and-recovery-page`: this change directory, nothing else. Verify:
  `openspec validate add-settings-backup-and-recovery-page --strict` passes and the dash grep prints nothing.
