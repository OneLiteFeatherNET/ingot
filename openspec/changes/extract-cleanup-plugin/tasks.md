# Tasks

Each group is one or more pull requests. A pull request has the title named in its heading, which is the single commit that lands on
main. Inside a pull request the commits have one type each: the failing test first (`test(cleanup)`), then the build file
(`build(cleanup)`), then the move or the change (`refactor(cleanup)`), and the docs (`docs(gitbook)`). Tests are written first and seen to
fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (a `Clock` is injected
where time matters), no shared mutable state, a temporary directory per test (`@TempDir`), and assertions with messages. Gradle runs go
through `heavy` with `--max-workers=2`, targeted with `--tests`, and never as the full suite of `reposilite-backend`.

## 0. Gate and verification (results recorded in `design.md` and `proposal.md`)

- [ ] 0.1 Gate: the prerequisite change `add-embedded-plugin-jars` (`feat(plugin)`) is merged on main. Verify: its pull request is linked in
  the description of the move pull request (group 2), and the loader there reads `embedded-plugins/*.jar` and the version rule of design D9.
  No move pull request starts before this gate passes. The seam of group 1 does not need it.
- [ ] 0.2 Verify that the shared configuration keeps stored domains that no loaded plugin claims. Read the loader of
  `configuration.shared.json` and cite the line that keeps them. Record the result in design D5. If the loader drops them, the stored-domain
  requirement of the spec changes before group 2 starts.
- [ ] 0.3 Verify what the dashboard shows for `/settings/cleanup` when the server does not list the domain. Read the settings route and the
  settings view, and record the result in design D4.
- [ ] 0.4 Verify the uses of backend `internal` declarations by the cleanup code. Run `grep -rn "internal" reposilite-backend/src/main/kotlin/com/reposilite/cleanup`
  and list every declaration that the backend or the prometheus plugin reaches. Record the list in the commit body of group 2. The list decides
  whether a declaration is made public (design D2).
- [ ] 0.5 Verify where the server's working directory is in the image, because the prerequisite extracts into `<workingDirectory>/embedded-plugins/`.
  Read the entrypoint and the loader's default. Record the path in the operator notes of group 3.

## 1. Core seam for the job status (red, then green)

PR title: `refactor(cleanup): report the last scheduled run through a core interface`

- [ ] 1.1 `test(cleanup)`: failing test in `reposilite-backend/src/test/kotlin/com/reposilite/` that `FacadeOperationalSource` reports the job
  `cleanup` from a registered `JobLastSuccessSource` facade, omits it when no such facade is registered, and leaves the `audit-purge` entry as
  before. Verify: it fails because the interface does not exist.
- [ ] 1.2 `refactor(cleanup)`: add `JobLastSuccessSource` in `net.onelitefeather.ingot.deploy`, let `CleanupService` implement it and register
  it, switch `FacadeOperationalSource` to the interface, and remove the import of `com.reposilite.cleanup.CleanupService` from
  `reposilite-plugins/prometheus-plugin`. Verify: the test of 1.1 passes, `OperationalMetricsTest` passes unedited, and the prometheus module
  compiles without any class of `com.reposilite.cleanup`.

## 2. Move the cleanup feature into its own module (red, then green)

PR title: `refactor(cleanup): move the cleanup policies into the cleanup-plugin module`

This pull request is the single move. The classes must leave the server classpath together, see design D2. Renames dominate the diff.

- [ ] 2.1 `test(cleanup)`: failing test in the core test source set that no class of `com.reposilite.cleanup` is loadable from the server class
  loader, by `Class.forName` with `initialize = false`, and that the module's resources hold the service entry naming
  `com.reposilite.cleanup.application.CleanupPlugin`. Verify: the first assertion fails on the classes that are still in the server, and the
  second fails because the module does not exist.
- [ ] 2.2 `build(cleanup)`: add `reposilite-plugins/cleanup-plugin/build.gradle.kts` with `version = "1.0.0" // x-release-please-version`,
  `compileOnly` and `testImplementation` on `:reposilite-backend`, a shadow jar `cleanup-plugin.jar` with `Implementation-Version` in its
  manifest, and the Maven publication `net.onelitefeather.ingot:cleanup-plugin`. Include the module in `settings.gradle.kts`. In
  `reposilite-backend/build.gradle.kts` place the module's jar into `embedded-plugins/` of the `shadowJar` with the hook of the prerequisite,
  and add the module's compiled classes to the backend integration classpath, without its service entry, as `storageCachePlugin` is wired
  around line 494. Add `!reposilite-plugins/cleanup-plugin` to `.dockerignore`. Verify:
  `heavy ./gradlew :reposilite-backend:shadowJar --max-workers=2` runs, the module configures, and the jar lists `embedded-plugins/cleanup-plugin.jar`.
- [ ] 2.3 `refactor(cleanup)`: `git mv` the sources of `reposilite-backend/src/main/kotlin/com/reposilite/cleanup/` into the module, the unit
  tests of `reposilite-backend/src/test/kotlin/com/reposilite/cleanup/` into the module's test source set, and the integration tests of
  `reposilite-backend/src/integration/kotlin/com/reposilite/cleanup/` and `WorkerTwoInstanceCleanupIntegrationTest` into its integration source
  set. Remove `CleanupPlugin` from the service entry of `reposilite-backend/src/main/resources/META-INF/services/` and put it in the module's
  entry. Make public only the declarations that group 0.4 lists. Change imports only where a declaration moved module. Verify: the test of 2.1
  passes; `heavy ./gradlew :reposilite-plugins:cleanup-plugin:test --max-workers=2` passes; `heavy ./gradlew :reposilite-backend:integration --tests 'com.reposilite.cleanup.*' --max-workers=2`
  passes; `SettingsIntegrationTest` passes with only its import changed; the OpenAPI snapshot test passes without `-PrefreshOpenApiSnapshot`; the
  worker test shows `CLEANUP_SCHEDULE` only on the worker; and the backend unit suite compiles.
- [ ] 2.4 Verify the drop-in path. Start the built server jar with no plugins directory and the default image layout, and check that the cleanup
  routes answer, the `cleanup` domain is listed, and the start log names the embedded copy. Verify: the container images job passes on this pull
  request, including the upgrade rehearsal against the upstream image.
- [ ] 2.5 Verify: `git diff --stat -M` shows the renames, the pull request names no file under `openspec/`, and its changed lines stay near 400
  with the renames not counted. The pull request description names the prerequisite pull request.

## 3. Documentation of the module (red, then green)

PR title: `docs(gitbook): describe the cleanup-plugin module and how to take a newer jar`

- [ ] 3.1 `docs(gitbook)`: add the module and the embedded copy to `docs/gitbook/references/architecture/backend-architecture.md` (the plugin
  graph and the note on the cleanup plugin), name the owner of the `cleanup` domain in `docs/gitbook/references/settings/settings-domains.md` and
  `docs/gitbook/references/http-api/dashboard-pages.md`, and describe the update path of design D9 and the plugins directory of task 0.5 in
  `docs/gitbook/references/repositories/cleanup-policies.md`. The configuration reference and the environment variables page stay as they are.
  Verify: every link on the changed pages points to a file that exists, and the dash grep prints nothing.

## 4. Replacement by a newer jar (red, then green)

PR title: `test(cleanup): cover the replacement of the embedded copy by a newer jar`

- [ ] 4.1 `test(cleanup)`: failing integration tests, with the plugins directory in a `@TempDir`, that a jar with the id `cleanup` and a higher
  `Implementation-Version` replaces the embedded copy, that an equal or lower one is ignored, that a jar without a version is ignored, and that each
  case logs one INFO line that names both versions. Verify: they fail until the prerequisite is merged on main, and they pass after it, without
  editing them.

## 5. Open the code pull requests

- [ ] 5.1 Open the code pull requests in the order of groups 1 to 4, each on the remote main branch that holds the previous one and the merged
  prerequisite, with the titles named above. Each one is opened only after its tests pass and no file under `openspec/` is in it. Verify: each
  pull request title is a valid Conventional Commit, and `gh pr view` shows the expected title and files.
