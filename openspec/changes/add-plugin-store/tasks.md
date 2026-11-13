# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `plugin`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, the `Clock` is injected,
no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through
`heavy`, with `--max-workers=2`, targeted and never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-plugin-store`: this change and nothing else, tasks unchecked, branched from the remote main
  branch. Verify: `openspec validate add-plugin-store --strict` passes and the dash grep from the proposal prints nothing.

## 2. Configuration and coordinates (red, then green)

- [ ] 2.1 `test(plugin)`: failing tests of the local list: `INGOT_PLUGINS` wins over `REPOSILITE_PLUGINS`; a blank entry is
  ignored; `opensearch-plugin` without group and version refuses the whole list and names the variable and the entry; the local
  key `plugins` and the variable give the same list. Verify: they fail on the missing parser.
- [ ] 2.2 `feat(plugin)`: add the coordinate parser, the local key `plugins` and the variables `INGOT_PLUGINS`,
  `INGOT_PLUGIN_SOURCES`, `INGOT_PLUGIN_TRUSTED_KEYS`, `INGOT_PLUGIN_ALLOW_UNSIGNED` and `INGOT_PLUGIN_STORE_DISABLED`, each with its
  `REPOSILITE_` fallback. Verify: the tests of 2.1 pass.
- [ ] 2.3 `test(plugin)` then `feat(plugin)`: failing tests that a plain `http://` source is refused naming the variable, and that
  a source with no trusted key is not used. Then the checks. Verify: the tests pass.
- [ ] 2.4 `docs(plugin)`: document the five variables and their `REPOSILITE_` fallbacks in
  `docs/gitbook/reference/environment-variables.md` and `docs/gitbook/reference/configuration-and-startup.md`. Verify: every variable
  of 2.2 appears in both pages with its default.

## 3. Catalogue and compatibility (red, then green)

- [ ] 3.1 `test(plugin)`: failing tests of the catalogue parser with committed JSON fixtures: schema 1 accepted, schema 2 refused
  with both numbers, unknown fields ignored, an entry without `compatibility` refused, `yanked` kept but not selectable, an `id`
  that differs from the loaded plugin refused. Verify: they fail.
- [ ] 3.2 `feat(plugin)`: add the catalogue model, the parser and the compatibility rule (`min` inclusive, `below` exclusive,
  `pluginApi` 1). Verify: the tests of 3.1 pass.
- [ ] 3.3 `test(plugin)` then `feat(plugin)`: failing tests of source order: the first source provides the entry and the second is
  reported as shadowed by the name of the first. Verify: the tests pass.

## 4. Verification (red, then green)

- [ ] 4.1 `test(plugin)`: failing tests with a committed test key pair that no production configuration trusts: a valid jar and
  catalogue pass; a digest mismatch fails at the digest step and deletes the file; a signature from an untrusted key fails at the
  signature step; an expired subkey is reported; an unsigned jar loads only with the switch and is marked `UNVERIFIED`. Verify:
  they fail.
- [ ] 4.2 `feat(plugin)`: add the OpenPGP verification with Bouncy Castle `bcpg` (add `org.bouncycastle:bcpg` to
  `reposilite-backend/build.gradle.kts`), the digest check and the step names. Verify: the tests of 4.1 pass and the dependency
  appears once in the build file.

## 5. Download, cache and offline start (red, then green)

- [ ] 5.1 `test(plugin)`: failing tests of the cache with a fixed `Clock` and a temporary directory: a verified jar is written
  atomically with its record; a half-written file is never read; a cached jar whose bytes changed is `REFUSED`; a cached jar whose
  key is no longer trusted is not loaded; a yanked cached jar still loads. Verify: they fail.
- [ ] 5.2 `feat(plugin)`: add the cache under `<data>/plugin-cache`, the atomic write and the offline check that needs no network.
  Verify: the tests of 5.1 pass.
- [ ] 5.3 `test(plugin)` then `feat(plugin)`: an integration test against a loopback HTTP server on an ephemeral port: a source
  served by an Ingot mirror is used; an unreachable source with a verified cache loads the plugin; the 60-second startup budget
  marks unfinished entries `UNAVAILABLE` without waiting for real time (the budget is injected). Verify: the tests pass.

## 6. Loader integration (red, then green)

- [ ] 6.1 `test(plugin)`: failing tests of the loader order with a manual jar, a store plugin and a bundled jar that share an id:
  the manual jar wins and the others are `SHADOWED`; a store plugin wins over a bundled jar; the bundled OpenSearch jar is `SHADOWED`
  when the store selects the same id. Verify: they fail.
- [ ] 6.2 `feat(plugin)`: insert the store step between the plugins directory and the bundled jars in `PluginLoader`, keep the
  existing duplicate check, and record the states. Verify: the tests of 6.1 pass and `BundledPluginSwitchTest` passes unedited.
- [ ] 6.3 `test(plugin)` then `feat(plugin)`: failing tests that a jar which throws in `initialize` is `FAILED` and the start
  completes, and that `INGOT_PLUGIN_STORE_DISABLED=true` makes no request. Then the failure isolation for store plugins only, with
  the known limit recorded in the status. Verify: the tests pass and the plugins-directory behaviour is unchanged.

## 7. Replica state and restart (red, then green)

- [ ] 7.1 `test(plugin)`: failing tests of the state computation with a fixed `Clock`: a replica whose loaded set differs from the
  effective set reports `restartPending`; two recent replicas with different sets name both; a record older than three minutes is
  stale. Verify: they fail.
- [ ] 7.2 `feat(plugin)`: add the per-replica record, written at start and on each settings refresh, and the state computation.
  Verify: the tests of 7.1 pass.

## 8. Routes, permissions and audit (red, then green)

- [ ] 8.1 `test(plugin)`: failing tests of `GET /api/plugin-store/status`, `GET /api/plugin-store/catalogue` and `POST
  /api/plugin-store/refresh`: a non-administrator is refused; the refresh is refused in read-only mode and leaves the cache unchanged;
  a changed selection writes one audit entry without a secret. Verify: they fail.
- [ ] 8.2 `feat(plugin)`: add the routes, the `plugins` settings domain with its schema, the audit entries and the read-only checks.
  Verify: the tests of 8.1 pass and the existing settings tests pass unedited.
- [ ] 8.3 `build(plugin)`: add the new paths and schemas to the OpenAPI description, with response and problem schemas. Verify: the
  OpenAPI diff gate reports additions only.
- [ ] 8.4 `docs(plugin)`: document the `plugins` settings domain in `docs/gitbook/reference/settings-domains.md` and the routes in
  `docs/gitbook/reference/plugin-store.md`. Verify: the domain page lists the field and the default of an absent document.

## 9. Dashboard (red, then green)

- [ ] 9.1 `test(plugin)`: failing frontend tests in the existing test setup: the "Plugin store" entry is shown only for the full
  administrator and in the System group; the restart banner names the replicas; an incompatible entry has a disabled toggle with its
  reason; a local entry has no remove control. Verify: they fail.
- [ ] 9.2 `feat(plugin)`: add the page to the administration tree with the existing components and no script. Verify: the tests of
  9.1 pass and the dashboard policy is unchanged.

## 10. Documentation

- [ ] 10.1 `docs(plugin)`: add `docs/gitbook/how-to/install-plugins-from-the-store.md` and
  `docs/gitbook/how-to/run-a-plugin-catalogue-mirror.md`, and link both from `docs/gitbook/SUMMARY.md`.
- [ ] 10.2 `docs(plugin)`: add `docs/gitbook/reference/plugin-store.md` with every variable, the catalogue format, the states and the
  routes; update `docs/gitbook/reference/environment-variables.md`, `docs/gitbook/reference/configuration-and-startup.md`,
  `docs/gitbook/reference/settings-domains.md`, `docs/gitbook/reference/audit-events.md`, `docs/gitbook/reference/dashboard-pages.md`
  and `docs/gitbook/reference/upgrade-changes.md` (new variables, no breaking change), and link the store from
  `docs/gitbook/how-to/install-a-plugin.md`.

## 11. Code pull request

- [ ] 11.1 Open the code pull request under the title `feat(plugin): install plugins from a catalogue in a maven repository`, from a
  branch of the remote main branch that already contains the merged proposal, with no file under `openspec/`.
