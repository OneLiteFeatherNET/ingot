# Tasks

Settled on 2026-11-18. The page is not a route: the settings backup lives on the Settings backup tab of Maintenance
(`/maintenance?tab=backup`), and the factory reset on System and support. Tasks already delivered on `main` are ticked. The
passphrase strength hint is the only work left, so this change is not archived yet.

Each group is one pull request, one Conventional Commits type each. Every test is written first and seen to fail. Every test
follows F.I.R.S.T.: no sleeps, no system clock (inject `Clock`, the dashboard clock key), no network, a fresh store, fake client
and router per test, assertions with messages. Heavy jobs run through `heavy`: Gradle with `--no-daemon --max-workers=2`, vitest
with `--maxWorkers=2`. Code pull requests contain no file under `openspec/`. Task checkboxes are ticked in a follow-up pull
request, not in the code pull requests.

## 1. Rights in the HTTP reference (`docs(gitbook)`)

- [x] 1.1 `docs(gitbook): name the settings area for the settings export routes`: the rows of `/api/settings/export` and
  `/api/settings/import/open` in `docs/gitbook/references/http-api/http-api.md` name the settings area (delivered).
- [x] 1.2 Pull request titled `docs(gitbook): name the settings area for the settings export routes` (merged).

## 2. Passphrase never leaks (`test(settings)`)

- [x] 2.1 `test(settings): guard that the export passphrase never reaches a log or an audit event`: delivered in
  `SettingsExportPassphraseIntegrationTest.kt`, including the 11 character refusal without an export event.
- [x] 2.2 The `400` without a `password` and the `Cache-Control: no-store` check are delivered in the same test class.
- [x] 2.3 Pull request titled `test(settings): guard that the export passphrase never reaches a log or an audit event` (merged).

## 3. Passphrase strength hint (`feat(ui)`), remaining

Red test first:

- [x] 3.1 `src/components/settings/passphraseGuidance.test.js`: below 12 characters "too short" and not submittable, 12 to 15
  "acceptable", 16 or more "long", code points counted (an emoji is one), mismatch of the confirmation, empty value. Verify: it
  fails, the module does not exist.

Implementation:

- [x] 3.10 `feat(ui)`: add `passphraseGuidance.js` and use it in `PasswordDialog.vue` (strength line, recommendation of several
  words and a password manager, show and hide control; `autocomplete="new-password"` is already there). Verify: 3.1 passes and
  the dialog tests pass.
- [x] 3.16 Open the pull request titled `feat(ui): show a passphrase strength hint in the export dialog`.

Superseded by the delivered Maintenance tab (not checked; the route and the page of the first draft are gone):

- 3.2 to 3.9 (`BackupView` tests, Factory reset section tests, `backup` navigation entry and `/settings/backup` route tests,
  Overview tests, `e2e/backup.spec.js`, axe and layout checks of the page). Any test that is still wanted for the Settings backup
  tab is a new task in a new change.
- 3.11 (`BackupView.vue`, the route and the anchors `backup-*`): replaced by the `SettingsBackupPanel.vue` tab.
- 3.13 to 3.15 (screenshots and the group verification of the page): not part of the settled scope.

Delivered in a different form, ticked:

- [x] 3.12 Overview has no Import settings, no Download as JSON and no Danger zone (`SettingsView.vue`). The download and import
  moved to the Settings backup tab (`SettingsBackupPanel.vue`). The factory reset is on System and support
  (`SystemInfoView.vue`, `FactoryResetModal.vue`). `SettingsDownloadMenu.vue` stays, since the tab uses it.

## 4. Documentation (`docs(gitbook)`)

- [x] 4.2 `docs/gitbook/references/http-api/dashboard-pages.md` describes the Settings backup tab and the removed Overview
  buttons (delivered).
- [x] 4.1 Add `docs/gitbook/manage/operate/back-up-and-restore-settings.md` (export, store the file and the passphrase apart,
  restore through the review, what a factory reset does, that the file holds secrets in clear inside the encryption, that the
  passphrase cannot be recovered) and link it from `docs/gitbook/manage/operate/back-up-and-restore.md` and
  `docs/gitbook/references/settings/settings-encryption.md` ("Export and import"). Verify: links resolve in `.gitbook.yaml`.
- [x] 4.3 Open the pull request titled `docs(gitbook): describe the settings backup in the operator guide`.

## 5. Proposal

- [x] 5.1 `docs(openspec): propose add-settings-backup-and-recovery-page` (merged, pull request 273).
