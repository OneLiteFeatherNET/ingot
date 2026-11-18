# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (inject
`java.time.Clock` where a clock is read), no shared mutable static state (the registry is built per test, never taken from the
companion object of `StorageProviderSerdes`), a temporary directory per test, assertions with messages. Unit tests run as
`:reposilite-backend:test --tests '<pattern>'` and the frontend tests with `npx vitest run <pattern>` in `reposilite-frontend`.
Backend integration tests run through `heavy ./gradlew :reposilite-backend:integration --tests '<pattern>' --max-workers=2`.

Fake storage plugins in the tests are small settings classes and factories declared in the test source set. They are not the
real Google Cloud Storage or Azure jars, which keeps the tests fast and independent of the plugin release.

## 0. Verify before code

- [ ] 0.1 Read where `StorageFacade` and `StorageProviderRegistry` are constructed, and where plugin jars are discovered and
  initialised. Record the answer (the owner of the registry, and the order of plugin loading against schema serving) in the
  commit body of group 2. Confirm that `JsonSchemaLoader().loadGeneratedSchemes()` supplies the scheme of the repository settings
  type, so the merge seam of D3 is the right one. If either answer differs from `design.md`, correct the design before group 1.

## 1. Served schema (red, then green)

- [ ] 1.1 `test(storage)`: failing unit tests of the variant list, with a fake registry and no plugin jar: no plugin factory gives
  the served `storageProvider` `oneOf` with exactly the two built-in entries; a fake plugin factory of type `fake-store` adds one
  entry whose `type` is the constant `fake-store`; two plugin factories are sorted by type id; a factory whose settings schema
  cannot be generated is left out and a warning names its type id and its source (D8). Verify: they fail on the missing class.
- [ ] 1.2 `test(storage)`: failing test that the served repository schema with no plugin is byte-identical to the schema
  of `main` (a golden file taken from `main` before the change, stored under the test resources). Verify: it fails only if the
  transform changes the built-in part of the schema; the golden file's origin is recorded in the commit body.
- [ ] 1.3 `feat(storage)`: add the variant transform of D3 and call it from the served-schema path of `SharedConfigurationFacade`
  for the `storageProvider` property only. Verify: the tests of 1.1 and 1.2 pass.

## 2. Registry and secrets (red, then green)

- [ ] 2.1 `test(storage)`: failing test that the `type` enum and the subtype resolution of `StorageProviderSettings` contain a
  fake plugin type when the registry is given that factory, and do not contain it when the registry is empty. The companion
  map is not used. Verify: they fail.
- [ ] 2.2 `feat(storage)`: hand the live registry to `StorageProviderSerdes` and to the schema generator, as decided in task 0.1.
  The companion map stays as the built-in fallback. Verify: the tests of 2.1 pass and the parse tests of
  `add-storage-provider-registration` still pass unchanged.
- [ ] 2.3 `test(storage)`: failing test that a fake plugin settings class with a `@Sensitive` field under `storageProvider` puts that
  field into the secret shape, so a placeholder in an update keeps the stored value, and that a built-in S3 key keeps its current
  shape. Verify: the first assertion fails.
- [ ] 2.4 `feat(storage)`: include the offered plugin variants in the secret shape of `SettingsSecrets.kt` (D4). Verify: the tests of
  2.3 pass.
- [ ] 2.5 `test(storage)`: a drop-in test that with no plugin the secret shape of `storageProvider` equals the shape of `main`
  (the `fs` and `s3` secrets only). It is written before 2.4 and must pass both before and after 2.4, because it guards the
  built-in path. Verify: it passes at both points.

## 3. Saved configuration (red, then green)

- [ ] 3.1 `test(storage)`: failing test that a saved block of a type that the served list does not offer is kept as an
  `UnresolvedStorageProviderSettings` and written back unchanged on an unrelated save (D7). Verify: it fails if the write path
  rewrites the type; if it already passes, record that in the commit body and keep the test.
- [ ] 3.2 `feat(storage)`: only if 3.1 fails, make the save path keep the raw block. Verify: the test passes and the saved bytes of a
  built-in repository are unchanged, checked by the existing configuration round-trip tests.

## 4. Dashboard and wizard (frontend, red, then green)

- [ ] 4.1 `test(storage)`: failing frontend test that the Storage tab renders the variants of a served schema that contains a
  plugin variant, and selects the plugin variant from a stored `type`. Verify: it fails until the schema fixture carries the variant.
  If it passes without any frontend change, record that in the commit body and do not change `OneOfRenderer.vue`.
- [ ] 4.2 `test(storage)`: failing tests of `repositoryWizard.ts` that the storage kinds equal the served variants (`fs`, `s3`, then
  the plugin types sorted by id), that a plugin variant validates its `required` fields and writes `{ type, ...fields }` with blank
  optional fields left out, and that `fs` and `s3` write the blocks they write today. Verify: they fail.
- [ ] 4.3 `test(storage)`: failing test that a stored plugin type that the schema does not list is shown with the note "not installed"
  and is not replaced by `fs` when the tab is saved (D7). Verify: it fails.
- [ ] 4.4 `feat(storage)`: take the kinds of the wizard from the served schema, render a plugin variant's fields from its schema, and
  show the unavailable note in the Storage tab. Verify: the tests of 4.1 to 4.3 pass and the wizard test suite
  (`repositoryWizard.test.ts`, `RepositoryWizard.test.js`, `SettingsView.wizard.test.js`) passes unchanged for `fs` and `s3`.
- [ ] 4.5 `feat(storage)`: when PR #791 is on `main`, apply its layout registry to the fields of a plugin variant (D6). Until then
  this task stays open and the variant uses the settings class order. Verify: the field order test of that registry passes.

## 5. Documentation

- [ ] 5.1 `docs(storage)`: add one sentence to `docs/gitbook/how-to/` for the Google Cloud Storage and Azure Blob pages that the
  dashboard offers the provider once the plugin is installed, and that the saved block does not change. Verify: the links resolve
  and no environment variable or configuration key is named that does not exist.

## 6. Code pull request

- [ ] 6.1 Open the code pull request under the title `feat(storage): offer plugin storage providers in the dashboard`, from a
  branch of the remote main branch that already contains the merged proposal, with no file under `openspec/`. The description
  fills `.github/PULL_REQUEST_TEMPLATE.md`.
