# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first and seen to fail
before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (inject `java.time.Clock` where a
time is read), no fixed ports, no shared mutable static state (the registry is an instance passed to its users, never a companion value), a
temporary directory per test, bounded waits through latches, and assertions with messages. Gradle runs go through `heavy`, with
`--max-workers=2`, targeted and never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-storage-provider-registration`: this change and nothing else. Verify:
  `openspec validate add-storage-provider-registration --strict` passes and the dash grep from the proposal prints nothing.

## 2. Discovery and registry (red, then green)

- [ ] 2.1 `test(storage)`: failing unit tests of the registry with a fixed set of factories: a factory of the current API version registers;
  another version is refused with a warning naming the class and both numbers; a plain factory from a plugin source is refused naming the jar;
  a duplicate id is refused naming both sources; `fs` and `s3` cannot be replaced; an invalid id is refused; the built-ins are registered before any
  plugin. Verify: they fail on the missing types.
- [ ] 2.2 `test(storage)`: failing test with the fixture plugin jar of the `pluginFixture` source set, copied into a `@TempDir` and loaded through
  `URLClassLoader` with the SPI as parent: the application loader finds no `fixture` factory, and the registry finds exactly one through the plugin
  loader. Verify: it fails because the jar does not exist yet.
- [ ] 2.3 `feat(storage)`: add `net.onelitefeather.ingot.storage` with `IngotStorageProviderFactory`, `StorageProviderContext`,
  `@ExperimentalIngotStorageApi` and `STORAGE_PROVIDER_API_VERSION`; add the internal `StorageProviderRegistry` and the generic service helper that
  takes a list of class loaders and reports each source. Verify: the tests of 2.1 and 2.2 pass.
- [ ] 2.4 `feat(storage)`: keep each plugin's class loader in `PluginLoader` and call the registry after the `load()` loop and before the first
  `initialize()`. `StorageFacade` keeps its no-argument constructor and its `createStorageProvider` signature, and looks up through the registry.
  Verify: a test that a plugin registered in `load()` is visible to a second plugin's `initialize()`, with the existing `PluginLoader` tests unedited.

## 3. Context and composition (red, then green)

- [ ] 3.1 `test(storage)`: failing tests of the context: `environment` reads `INGOT_` before `REPOSILITE_`; `secret` reads the `_FILE` variant, strips
  trailing line breaks and prefers the plain variable when both are set; a value returned by `secret` is replaced by `***` in a logged message; an
  unreadable `_FILE` names its variable and not its value; `createProvider` of another registered type returns its provider; a cycle is refused with
  the chain in the message; `createProvider` of an unregistered type throws `StorageProviderNotInstalledException` naming the type. Verify: they fail.
- [ ] 3.2 `feat(storage)`: implement the context, the redaction set of the context logger, and `createProvider` with the chain check. Verify: the tests of
  3.1 pass.

## 4. Shared configuration (red, then green)

- [ ] 4.1 `test(storage)`: failing tests in the serdes: a storage block with an unknown type round-trips to the identical document (field order, numbers
  as their source text, nested objects); the parse of a shared configuration that contains such a block passes and keeps the other blocks; the guess for a
  block without `type` gives `s3` with `bucketName` and `fs` without. The first failing test documents the current behaviour of an unknown type, which is
  expected to fail the parse of the whole configuration. Verify: they fail on the missing class.
- [ ] 4.2 `feat(storage)`: replace the companion map of `StorageProviderSerdes` by the registry instance, add `UnresolvedStorageProviderSettings` with
  `@JsonValue` serialisation of the raw node, and keep the guess unchanged. Verify: the tests of 4.1 pass and the existing serdes tests pass unedited.

## 5. Unavailable repository (red, then green)

- [ ] 5.1 `test(storage)`: failing integration tests with a temporary working directory and a configuration that names the type `fixture` without the
  plugin: the server starts; the repository is listed with state `UNAVAILABLE` and the reason; a download from it answers `503` with problem details and
  `Retry-After: 30`; a download from the `fs` repository of the same configuration succeeds; no file of the unavailable repository is created or removed;
  `/ready` does not fail because of it; a fixture decorator whose nested `createProvider` names a missing type leaves the repository `UNAVAILABLE` with
  the inner type in the reason; a factory that throws any other exception leaves the repository unloaded and reported, as today. Verify: they fail.
- [ ] 5.2 `feat(storage)`: change `RepositoryFactory` to create the `UNAVAILABLE` repository instead of throwing, add the reason to the repository state and
  the routes' `503` answer, and add the unavailable count to the readiness answer. Verify: the tests of 5.1 pass.
- [ ] 5.3 `test(storage)` then `feat(storage)`: failing test that a restart with the fixture plugin present builds the repository from the same raw block and
  serves its files, and that saving the settings with the plugin missing writes the block back unchanged. Then the implementation. Verify: the tests pass.

## 6. Dashboard schema (red, then green)

- [ ] 6.1 `test(storage)`: failing test that the repository settings schema lists the `fixture` type with its own fields, that a `@Sensitive` field of the
  plugin type is marked, and that the OpenAPI document is byte for byte unchanged. Verify: they fail.
- [ ] 6.2 `feat(storage)`: construct `StorageProviderEnumResolver` and `StorageProviderSubtypeResolver` with the registry, remove their two service entries,
  and keep the `@OneOf` annotation of `MavenSettings` unchanged. Verify: the tests of 6.1 pass and the existing schema tests pass unedited.

## 7. Contract test kit (red, then green)

- [ ] 7.1 `test(storage)`: move the nine cases of `StorageProviderIntegrationTest` into the abstract `StorageProviderContract` in `src/testFixtures` with a
  `provider(temporaryDirectory)` method, and add the cases of design D9 as failing tests against the `fs` provider. Verify: the new cases fail where the
  behaviour is not yet documented, and the nine cases still pass.
- [ ] 7.2 `test(storage)`: make the `fs` provider run the contract as a unit test with `@TempDir`, and the `s3` provider run it in the integration source set with
  its container through `heavy ./gradlew :reposilite-backend:integration --tests '*StorageProviderContract*' --max-workers=2`. Verify: the failures of 7.1 are
  explained in the test messages.
- [ ] 7.3 `test(storage)`: switch off each case that `fs` or `s3` fails today for that provider only, through a JUnit assumption whose message names the
  gap and the follow-up `fix(storage)` change. Do not change `fs` or `s3` code in this change. Verify: every case passes or is skipped with its reason,
  and the skipped cases are listed in the pull request.

## 8. Build

- [ ] 8.1 `build(storage)`: keep `java-test-fixtures` on `reposilite-backend` (it is already applied on main; verify before the task), add the `pluginFixture`
  source set and its `jar` task, and make the test task depend on it with its path passed as a system property. Verify:
  `heavy ./gradlew :reposilite-backend:compileTestFixturesKotlin :reposilite-backend:pluginFixtureJar --max-workers=2` passes.

## 9. Documentation

- [ ] 9.1 `docs(storage)`: update `docs/gitbook/reference/storage-providers.md` with the registry, the built-in precedence, the refusal rules, the unknown-type
  behaviour (`UNAVAILABLE`, `503`, readiness) and the list of registered types; the table of providers stays with `fs` and `s3` as the built-in pair.
- [ ] 9.2 `docs(storage)`: add `docs/gitbook/how-to/write-a-storage-plugin.md` (factory, context, `environment` and `secret` rules, `createProvider` and the
  cycle rule, the contract kit, the blocking `create`, the class loader lifetime) and link it from `docs/gitbook/SUMMARY.md`.
- [ ] 9.3 `docs(storage)`: update `docs/gitbook/reference/plugin-api.md` at the storage row: the registration replaces wrapping `StorageFacade` as the extension
  point; list `IngotStorageProviderFactory` and the context with their experimental marking. Update `docs/gitbook/reference/environment-variables.md` with the
  plugin-facing `INGOT_` and `REPOSILITE_` fallback rule for storage plugins, and `docs/gitbook/reference/upgrade-changes.md` with the behaviour change for an
  unknown type and the experimental marking.

## 10. Code pull request

- [ ] 10.1 Open the code pull request under the title `feat(storage): let plugins register storage providers`, from a branch of the remote main branch with no
  file under `openspec/`, after the proposal of 1.1 is merged.
