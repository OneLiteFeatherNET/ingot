# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `plugin`. Tests are written first
and seen to fail before the code they cover exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(any time source is injected as a `java.time.Clock`), no fixed ports, no network access from the test (artifacts come from
Gradle), no shared mutable state, a `@TempDir` working directory and a fresh server per test, and assertions with messages.
Gradle runs go through `heavy` with `--max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`. No configuration key, environment variable or deployment manifest
changes.

Depends on: nothing. Must land before `add-repository-format-spi`, and should land before `extract-repository-registry`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose gate-plugin-abi: this change and nothing else. Verify:
  `openspec validate gate-plugin-abi --strict` passes and the dash grep from the proposal prints nothing.

## 2. Binary canaries (`test(plugin)`, `build(plugin)`)

- [ ] 2.1 `build(plugin)`: add the test-scope configuration `pluginCanary` with
  `com.reposilite.plugin:checksum-plugin:3.5.28` and `com.reposilite.plugin:migration-plugin:3.5.28`, widen the upstream
  repository content filter by the group `com.reposilite.plugin` (releases only), and pass the resolved paths to the
  `integration` task as a system property. Verify: `dependencies --configuration pluginCanary` resolves only from Maven
  Central and the filtered upstream repository.
- [ ] 2.2 `test(plugin)`: write `UpstreamPluginCanaryIntegrationTest`: the upstream checksum plugin answers the SHA-256 of a
  deployed artifact; the upstream migration plugin migrates the token of a legacy `tokens.dat` fixture (copy the existing
  fixture of `migration-plugin`). Each test builds its own `@TempDir` working directory and server. Verify: run once with an
  empty plugins directory to see both fail with a message naming the missing behaviour, then with the jars; record the
  result (pass or the exact failure) in the pull request description.

## 3. Binary report (`build(plugin)`)

- [ ] 3.1 `build(plugin)`: add `me.champeau.gradle.japicmp` (exact version) to `reposilite-backend`, a detached configuration
  for `com.reposilite:reposilite:3.5.28` with its transitive dependencies, and the task `pluginApiReport` comparing it with
  the current jar over the package list of design D2, binary-incompatible changes only, `failOnModification = false`,
  HTML and Markdown output under `build/reports/plugin-api/`. Verify: the task succeeds and the report lists at least the
  known `RepositorySettings` and `MavenSettings` constructor drift; a scratch change that removes `MavenFacade.getRepositories`
  appears in the report (do not commit the scratch change).

## 4. CI job (`ci(plugin)`)

- [ ] 4.1 `ci(plugin)`: add the job "Plugin API" to `.github/workflows/ci.yml` for pull requests: run `pluginApiReport`, the
  canary integration test (`continue-on-error: true`), and `:reposilite-plugins:checksum-plugin:test` with
  `:reposilite-plugins:migration-plugin:test`; append the Markdown report, the canary results and a note for edited canary
  sources (`git diff --name-only` against the base) to `$GITHUB_STEP_SUMMARY`; upload the HTML report. Keep
  `permissions: contents: read`. Verify: on the pull request the job succeeds and its summary shows the report.

## 5. Documentation (`docs(plugin)`)

- [ ] 5.1 `docs(plugin)`: add "Compatibility checks" to `docs/gitbook/reference/plugin-api.md`: the baseline (3.5.28), the
  package list, the two canaries, that the gate is report-only for now and where the report is found.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title
  `ci(plugin): report binary compatibility of the plugin api against reposilite 3.5.28`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections (type `build` / `ci` / `chore`). The description tells the story of
  a plugin author whose plugin would fail at runtime without anyone noticing, and lists the drift the first report found. It
  carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style
  hook accepts the title and every commit, and the body has no em or en dash.
