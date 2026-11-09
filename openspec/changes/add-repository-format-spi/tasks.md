# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `repository`. Tests are written
first and seen to fail before the code they cover exists; characterisation tests are written first and seen to pass on main,
then must pass unedited after the change. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (any
time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable static state (the format registry is
built per test from a list), a `@TempDir` per filesystem test, a fresh server per integration test, and assertions with
messages. Gradle runs go through `heavy` with `--max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`.

Depends on: `gate-plugin-abi` and `extract-repository-registry`, both merged before this pull request is opened. The one
new configuration key is `format` in the `maven` domain (group 2); no environment variable or deployment manifest changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose add-repository-format-spi: this change and nothing else. Verify:
  `openspec validate add-repository-format-spi --strict` passes and the dash grep from the proposal prints nothing.

## 2. Format attribute and registry (`test(repository)` then `feat(repository)`)

- [ ] 2.1 `test(repository)`: write failing tests: settings without `format` resolve to `maven2`; a save of the domain after
  editing a description writes no `format` key for a repository that had none and keeps an explicit `"maven2"`; a 3.5.28
  shaped `configuration.shared.json` fixture loads; an unknown id and the id `maven` are violations naming the repository
  and the known ids; a group referencing a repository of a test format is a violation; duplicate format ids fail with both
  class names; a stored repository with an unknown format is left out at start with a recorded failure while `releases`
  serves. Verify: they fail on the missing key and types.
- [ ] 2.2 `feat(repository)`: add `RepositoryFormat`, `RepositoryFormats` (ServiceLoader, injectable list),
  `@ExperimentalIngotFormatApi`, `Maven2Format` with its `META-INF/services` entry, the trailing `format: String? = null` on
  `RepositorySettings` with the previous constructor kept as a secondary constructor, the validations, `format` on
  `Repository` and `RegisteredRepository`, and the unknown-format failure in `RepositoryFactory`. Verify: the tests of 2.1
  pass, and the "Plugin API" report shows only the `copy` difference of `RepositorySettings` as new.
- [ ] 2.3 `docs(repository)`: document `format` in `docs/gitbook/reference/repository-settings.md` (absent means `maven2`,
  only `maven2` exists, not written back when absent) and the rollback behaviour in
  `docs/gitbook/explanation/drop-in-compatibility.md` (older Ingot and Reposilite ignore the key and drop it when they save).

## 3. Path rules (`test(repository)` then `refactor(repository)`)

- [ ] 3.1 `test(repository)`: write the characterisation tests of design D3: the current `acceptsDeploymentOf`,
  `acceptsCachingOf`, `ignoredExtensions` check, `isResolutionMetadata`, `isMetadataOrChecksumFileName` and
  `artifactIndexPath` against `Maven2Format.pathRules` over one shared table of paths. Verify: they fail on the missing rules.
- [ ] 3.2 `refactor(repository)`: implement the Maven path rules by moving the existing logic, and make `Repository`,
  `RepositoryService`, `ResolutionProvider`, `MirrorService`, `MirrorCache` and `ArtifactIndexPath` ask the repository's
  format. Keep the public signatures of `Repository`. Verify: the tests of 3.1 and the existing Maven tests pass unedited.

## 4. Dispatcher (`test(repository)` then `feat(repository)`)

- [ ] 4.1 `test(repository)`: write the characterisation integration test of the design risks: status, headers and body for
  HEAD, GET, PUT, POST and DELETE on a hosted, a proxy and a group repository, an unknown repository, a directory index,
  `/api/unknown`, a repository named `settings` (Gradle request and browser navigation), a redirected path, and a `401` on a
  private repository; plus a comparison of the repository paths of the generated OpenAPI document with a fixture taken from
  main. Verify: it passes on main.
- [ ] 4.2 `test(repository)`: write failing unit tests for the dispatcher with a fake registry and a test format: a request
  for a repository of format `fake` reaches the fake handler; an unknown repository reaches the default handler. Verify: they
  fail on the missing dispatcher.
- [ ] 4.3 `feat(repository)`: add `RepositoryRequestHandler`, `RepositoryRequest`, `FormatMount` (only `RepositoryPath` is
  used) and `RepositoryRouteDispatcher`; move the Maven route bodies into `MavenEndpoints` functions unchanged and make them
  the `maven2` handler; register the dispatcher from `MavenPlugin` where the Maven routes were registered; switch
  `ResourcesFrontendHandler` and `RedirectPlugin` to the dispatcher; extend the architecture test so only the Maven format
  constructs `MavenEndpoints`. Verify: the tests of 4.1 pass unedited and the tests of 4.2 pass.
- [ ] 4.4 `docs(repository)`: describe the dispatch in `docs/gitbook/reference/web-routes.md` and the format layer in
  `docs/gitbook/explanation/backend-architecture.md`.

## 5. Storage boundary (`test(repository)` then `refactor(repository)`)

- [ ] 5.1 `test(repository)`: write failing tests: `CachableS3Client` with a given related-key rule drops exactly the named
  keys; without a rule it drops the `maven-metadata.xml` entries two levels up as today; a `maven2` repository on S3 (Floci
  integration test) drops the metadata entry after a deploy; the architecture rule allowing only `RepositoryVisibility` from
  storage into Maven; filesystem listing order `1.2`, `1.9`, `1.10`. Verify: the rule-injection tests fail; the default,
  listing and architecture tests pass on main.
- [ ] 5.2 `refactor(repository)`: add the related-key rule to `CachableS3Client` with today's rule as the default, the
  optional `RelatedKeyAware` on `S3StorageProvider`, `StorageHints` on the format with Maven's rule, the call in
  `RepositoryFactory`, and KDoc on `RepositoryVisibility` and `VersionComparator` describing their roles. Verify: the tests
  of 5.1 and the existing S3 tests pass.

## 6. Plugin API documentation (`docs(repository)`)

- [ ] 6.1 `docs(repository)`: add the experimental format SPI to `docs/gitbook/reference/plugin-api.md` (opt-in marker, not
  loaded from plugin jars yet, settings classes not covered by the compatibility promise).

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `feat(repository): add repository formats with maven2 as the default`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections (type `feat`). The description tells the operator's story:
  a repository can now say what it is, nothing in an existing installation changes, and the server has one place where the
  next formats plug in. Name the `copy` difference of `RepositorySettings` and paste the "Plugin API" job summary. No
  session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook
  accepts the title and every commit, the container job's upgrade rehearsal passes, and the body has no em or en dash.
