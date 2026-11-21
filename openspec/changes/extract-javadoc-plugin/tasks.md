# Tasks

Each group is one or more pull requests. A pull request has the title named in its heading, which is the single commit that lands on
main. Inside a pull request the commits have one type each: the failing test first (`test(javadoc)`), then the build file
(`build(javadoc)`), then the move or the change (`refactor(javadoc)` or `docs(javadoc)`). Tests are written first and seen to fail
before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (a `Clock` is injected
where time matters), no shared mutable state, a temporary directory per test (`@TempDir`), and assertions with messages. Gradle runs go
through `heavy` with `--max-workers=2`, targeted with `--tests`, and never as the full suite of `reposilite-backend`. Each code pull
request stays near 400 changed lines; renames of a move count little, and the move pull request says so in its description.

## 0. Gate and verification (results recorded in `design.md` and `proposal.md`)

- [x] 0.1 Gate: the prerequisite change `add-embedded-plugin-jars` (`feat(plugin)`) is merged on main. Verify: its pull request is linked in
  the description of the move pull request (group 3), and the loader there reads `embedded-plugins/*.jar` and the version rule of design D9.
  No code pull request of this change starts before this gate passes.
- [x] 0.2 Verify that the shared configuration keeps stored domains that no loaded plugin claims. Read the loader of
  `configuration.shared.json` and cite the line that keeps them. Record the result in design D5. If the loader drops them, question 3 of
  `proposal.md` changes before group 3 starts.
- [x] 0.3 Verify what the dashboard shows for `/settings/<domain>` when the server does not list the domain. Read the settings route and
  the settings view, and record the result in design D4. If the page shows an error, the redirect to `/settings` of group 3 is required,
  and its test is part of that group (question 4).
- [x] 0.4 Verify the uses of backend `internal` declarations by the viewer code. Run `grep -rn "internal" reposilite-backend/src/main/kotlin`
  for each class the viewer reaches, and list the names in the commit body of group 3. The list decides whether a class is made public
  (design D2).
- [x] 0.5 Verify where the plugins directory of the image is, and that the working directory is the one the entrypoint sets. Read the
  entrypoint and the loader's default. Record the path in design D10, so that the operator guide names it.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose extract-javadoc-plugin`: this change and nothing else, branched from the remote main branch. Verify:
  `npx -y @fission-ai/openspec@latest validate extract-javadoc-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Core seam (red, then green)

PR title: `refactor(javadoc): let the cluster reach the javadoc cache through a core interface`

- [x] 2.1 `test(javadoc)`: failing architecture test in `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/architecture/DependencyRulesTest.kt`:
  no class outside `com.reposilite.javadocs` depends on that package. Verify: it fails on `FacadeCacheHooks`, which imports `JavadocFacade`.
- [x] 2.2 `test(javadoc)`: failing tests in `ClusterJavadocEventsTest.kt`: a received `javadoc.invalidated` calls the public
  `JavadocCacheReceiver.evictReceived` of a fake once with the repository and the path; with no receiver the event is logged at debug level
  and nothing is deleted; the publishing tests are unchanged. Verify: they fail on the missing interface.
- [x] 2.3 `refactor(javadoc)`: add `JavadocCacheReceiver` in `net.onelitefeather.ingot.cluster`, make `JavadocFacade` implement it, and let
  `FacadeCacheHooks` find it among the facades. Verify: the tests of 2.1 and 2.2 pass, and the tests of `javadocs` pass unedited.

## 2a. API document and settings schema of the embedded plugin (prerequisite of group 3, red, then green)

PR title: `test(javadoc): guard the api document and the settings schema of the javadoc domain`

Group 3 does not start before this group is merged and the gate of 2a.1 passes. See design D13. Question Q112 of `design.md` is settled:
the unit tests stay in the backend (design D7).

- [x] 2a.1 Gate: the change `add-plugin-api-fragments` (`feat(plugin)`) is merged on main: the server merges the
  `META-INF/ingot/openapi/<id>.json` fragment of every loaded plugin into the served document, and looks up `json-schemes/<type name>` through
  a plugin's own class loader before the runtime generator. Verify: its pull requests are linked in the description of the move pull request
  (group 3). The tests of this group do not need it; the move does.
- [x] 2a.2 `test(javadoc)`: unit test in `reposilite-backend/src/test/kotlin/com/reposilite/javadocs/` that the compile-time schema
  `json-schemes/com.reposilite.javadocs.application.JavadocSettings` is readable through the class loader of `JavadocSettings` and that its
  `properties` name every property of `JavadocSettings`, each missing name in its own assertion message. Verify: it passes on main, and it
  fails when the file is left out of the module jar, which is the failure the move has to avoid. The test stays in the backend in task 3.3
  (design D7).
- [x] 2a.3 Verify that `OpenApiSnapshotIntegrationTest` holds no javadoc entry today, so the move must leave `docs/openapi/ingot-openapi.json`
  byte for byte as it is: `grep -ci javadoc docs/openapi/ingot-openapi.json` prints `0`. Record the result in the description of the pull
  request of this group.
- [x] 2a.4 Open the code pull request of this group under
  `test(javadoc): guard the api document and the settings schema of the javadoc domain`, branched from the remote main branch, after its
  test passes on main. Verify: the title is a valid Conventional Commit, `gh pr view` shows only the test file, and no file under `openspec/`.

## 3. Move the viewer into its own module (red, then green)

PR title: `refactor(javadoc): move the javadoc viewer into its own javadoc-plugin module`

- [x] 3.1 `test(javadoc)`: failing tests, both in `reposilite-backend/src/test/kotlin/com/reposilite/javadocs/`:
  (a) the backend's own compiled main output holds no class of `com.reposilite.javadocs`, read from the class directory that the build
  passes to the test as a system property. `Class.forName` cannot prove it, because the module's classes are on the backend's test
  classpath (design D7);
  (b) the module's resources hold the service entry naming `com.reposilite.javadocs.application.JavadocPlugin`, read from
  `reposilite-plugins/javadoc-plugin/src/main/resources/META-INF/services/`. The test does not read a built jar, so it does not depend on
  the build order. Verify: (a) fails on the classes that are still in the server, and (b) fails because the module does not exist.
- [x] 3.2 `build(javadoc)`: add `reposilite-plugins/javadoc-plugin/build.gradle.kts` with `version = "1.0.0" // x-release-please-version`,
  `compileOnly` and `testImplementation` on `:reposilite-backend` and its test fixtures, a shadow jar `javadoc-plugin.jar` with
  `Implementation-Version` in its manifest, and the Maven publication `net.onelitefeather.ingot:javadoc-plugin` as
  `reposilite-plugins/gcs-storage-plugin/build.gradle.kts` configures its own. In `reposilite-backend/build.gradle.kts` place the module's jar
  into `embedded-plugins/` of the `shadowJar` with the hook of the prerequisite change, and add the module to the integration classpath as
  `storage-cache-plugin` is wired around line 494. Add the module's compiled classes and its generated `json-schemes/` and
  `META-INF/ingot/openapi/` resources, without its service entry, to the backend's `testImplementation` in the same `files(...).builtBy(...)`
  form, so the unit tests that stay in the backend see them (design D7). Add `!reposilite-plugins/javadoc-plugin` to `.dockerignore`. Remove the run configuration
  `.run/Build javadoc-plugin to test workspace [javadoc-plugin _ clean build].run.xml` (question 6). Verify:
  `heavy ./gradlew :reposilite-backend:shadowJar --max-workers=2` runs and the module configures.
- [ ] 3.3 `refactor(javadoc)`: `git mv` the sources of `reposilite-backend/src/main/kotlin/com/reposilite/javadocs/` into the module and the
  integration tests of `reposilite-backend/src/integration/kotlin/com/reposilite/javadocs/` into its integration source set. The unit tests
  of `reposilite-backend/src/test/kotlin/com/reposilite/javadocs/` stay where they are (design D7, question Q112). Remove the service entry
  from `reposilite-backend/src/main/resources/META-INF/services/`, and make public exactly the declarations that the exception of design D2
  names: `JavadocContainerService` with its constructor and `unpackJavadocJar`, `JavadocContainer`, `javadocMaxEntryBytes`,
  `DEFAULT_JAVADOC_MAX_ENTRY_BYTES`, `javadocEvictionRoot` and the constructor of `JavadocFacade`. Change imports only where a declaration
  moved module. Verify: the tests of 3.1 pass;
  `heavy ./gradlew :reposilite-backend:test --tests 'com.reposilite.javadocs.*' --max-workers=2` passes with no test file edited;
  `heavy ./gradlew :reposilite-backend:integration --tests 'com.reposilite.javadocs.*' --max-workers=2` passes, and the backend unit suite
  compiles.
- [ ] 3.4 Verify the drop-in path. Start the built server jar with no plugins directory and with the default image layout, and check that
  the viewer answers on `/javadoc/` and that a jar of version `1.1.0` in the plugins directory is the one in use (the update rule of the
  prerequisite). Verify: the container images job passes on this pull request, including the upgrade rehearsal against the upstream image.
- [x] 3.5 Verify: `git diff --stat` shows renames of the files above, the pull request names no file under `openspec/`, and its changed
  lines stay near 400 with the renames not counted.
- [x] 3.6 `build(javadoc)`: the module applies `kapt` with `openapi-annotation-processor`, renames the generated
  `openapi-plugin/openapi-default.json` to `META-INF/ingot/openapi/javadoc.json`, and leaves out its `openapi-plugin/.index` and
  `json-schemes/index` (design D13). The module's resources join the backend integration classpath with its classes (task 3.2). Verify: the
  jar lists `json-schemes/com.reposilite.javadocs.application.JavadocSettings`; the test of 2a.2 passes unedited;
  `OpenApiSnapshotIntegrationTest` passes without `-PrefreshOpenApiSnapshot`; and the start log of the built server jar of task 3.4 holds
  no line with `Cannot find scheme`, checked by `grep -c 'Cannot find scheme'` printing `0`.

## 4. Dashboard without a viewer (red, then green)

PR title: `refactor(javadoc): stop falling back to default javadoc suffixes in the dashboard`

- [x] 4.1 `test(javadoc)`: failing tests in `reposilite-frontend/src/store/` and `reposilite-frontend/src/components/browser/rowActions.test.js`:
  with no placeholder for `{{REPOSILITE.JAVADOC_SUFFIXES}}` the suffixes are empty, and no row offers the Javadoc action for
  `demo-1.0-javadoc.jar`; with the placeholder sent, the action is offered as before. Verify: the first assertion fails on the fallback.
- [x] 4.2 `refactor(javadoc)`: remove the `REPOSILITE.JAVADOC_SUFFIXES` entry of `FALLBACKS` in `reposilite-frontend/src/store/placeholders.ts`.
  Verify: the tests of 4.1 pass, the unit tests of the dashboard pass, and the browse and navigation end-to-end tests pass against the fake
  API, which sends the placeholder.

## 5. Documentation of the module

PR title: `refactor(javadoc): describe the javadoc module and its embedded copy in the GitBook pages`

- [x] 5.1 `docs(javadoc)`: update the module list and the plugin graph in `docs/gitbook/references/architecture/backend-architecture.md`
  (the endpoint list near line 39, the plugin graph, and the note on `JavadocPlugin` near line 512), and the owner of the `javadoc` domain in
  `docs/gitbook/references/settings/settings-domains.md` and `docs/gitbook/references/http-api/dashboard-pages.md`. Add the update path of
  design D9 and the plugins directory of design D10 to the architecture page. The configuration reference, the environment variables and the
  web routes page stay as they are, because no key, variable or route changes. Verify: every link on the changed pages points to a file
  that exists, and the dash grep prints nothing.

## 6. Open the code pull requests

- [x] 6.1 Open the code pull requests in the order of groups 2, 2a and 3 to 5, each on the remote main branch that holds the previous one and the
  merged prerequisite, with the titles named in those groups. Each one is opened only after its tests pass and no file under `openspec/` is
  in it. Verify: each pull request title is a valid Conventional Commit, and `gh pr view` shows the expected title and files.
