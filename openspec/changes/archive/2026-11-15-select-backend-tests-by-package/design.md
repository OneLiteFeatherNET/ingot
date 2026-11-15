# Design

## Starting point on main

- `ci.yml` triggers on push and on pull request to main (lines 4 to 8). It has no `schedule` trigger.
- `coverage-changes` (line 548) decides whether the `coverage` job (line 609) runs at all. The decision is a deny list in
  `.github/scripts/coverage_changes.sh`: documentation, OpenSpec, issue templates and repository metadata are skipped, and every
  other path counts as relevant. The script says why it is a deny list: the backend jar bundles the dashboard, and the characterisation
  tests read files outside the Java sources.
- The `coverage` job runs `./gradlew :reposilite-backend:testCoverage` (`build.gradle.kts`, line 546). `testCoverage` depends on the
  `test` task, the `integration` task and the JaCoCo report. The JaCoCo verification minimum is 0.0 (lines 520 to 540), so a partial run does
  not fail on coverage. Only the upload to Codecov makes a partial figure visible.
- Measured on run 43021374786 (a pull request, 2026-11-11): the `Coverage` job ran from 12:46:01 to 14:04:47 UTC, 79 minutes. The
  longest other job of that run, `Container images`, took 22 minutes.
- Test counts, from the annotations `@Test` and `@ParameterizedTest` in the three source sets of `reposilite-backend`:

  | Source set | Annotations | Files |
  |---|---|---|
  | `src/test` (unit) | 2,642 | 367 |
  | `src/integration` | 574 | 114 |
  | `src/containerTest` | 57 | not in the coverage job |

  The annotation count is a floor for the executed count, because parameterised tests expand into several runs. The brief's figure of
  about 935 integration tests was not reproduced from the sources. The executed count is in the JUnit XML of a run and should be used
  when the baseline is taken.

## Measured package map

The domains are the packages directly below `com.reposilite` and `net.onelitefeather.ingot` (31 domains, 467 main Kotlin files, 165 of
them under `net.onelitefeather.ingot`). Counts are from the sources. The last column is the share of all 3,216 test annotations that the
rule below selects for a change to that package alone: the package and its direct importers.

| Domain | Main files | Unit | Integration | Selected alone | Class |
|---|---:|---:|---:|---:|---|
| `root` (package `com.reposilite`, facade and fixtures) | 7 | 0 | 0 | full | infrastructure |
| `configuration` | 34 | 178 | 38 | full | infrastructure |
| `shared` | 23 | 106 | 0 | full | infrastructure |
| `web` | 14 | 10 | 71 | full | infrastructure |
| `plugin` | 9 | 16 | 2 | full | infrastructure |
| `storage` | 25 | 125 | 26 | full | infrastructure |
| `net...ingot.web` | 7 | 54 | 0 | full | infrastructure |
| `net...ingot.schema` (schema locks, migrations) | 3 | 13 | 5 | full | infrastructure |
| `maven` | 57 | 492 | 125 | 62.8% | full by threshold |
| `token` | 18 | 39 | 17 | 76.9% | full by threshold |
| `status` | 16 | 54 | 8 | 80.6% | full by threshold |
| `net...ingot.identity` | 65 | 694 | 9 | 82.3% | full by threshold |
| `auth` | 11 | 20 | 0 | 61.5% | full by threshold |
| `audit` | 18 | 109 | 9 | 53.3% | selected |
| `statistics` | 12 | 20 | 10 | 50.6% | selected |
| `net...ingot.readonly` | 6 | 19 | 21 | 46.8% | selected |
| `net...ingot.repository` | 4 | 2 | 5 | 40.6% | selected |
| `net...ingot.search` | 37 | 225 | 44 | 35.4% | selected |
| `frontend` | 22 | 89 | 12 | 32.4% | selected |
| `console` | 16 | 8 | 7 | 24.3% | selected |
| `scheduling` | 2 | 21 | 0 | 21.1% | selected |
| `javadocs` | 7 | 22 | 4 | 12.2% | selected |
| `net...ingot.cluster` | 24 | 134 | 17 | 10.9% | selected |
| `cleanup` | 10 | 95 | 11 | 10.0% | selected |
| `net...ingot.deploy` | 2 | 22 | 0 | 3.8% | selected |
| `identity` (integration only) | 0 | 0 | 76 | 2.4% | selected |
| `net...ingot.support` | 12 | 60 | 13 | 2.3% | selected |
| `net...ingot.backup` | 5 | 12 | 3 | 0.5% | selected |
| `login` (integration only) | 0 | 0 | 33 | 1.0% | selected |
| `migration` (integration only) | 0 | 0 | 8 | 0.2% | selected |
| `net...ingot.architecture` | 0 | 3 | 0 | 0.1% | selected (the rules test, see smoke set) |
| `redirect` | 1 | 0 | 0 | 0% | no tests: treated as unmapped |

`...` abbreviates `onelitefeather`. Domains with a `net...` name are packages under `net.onelitefeather.ingot`.

## Decision 1: packages, not JUnit tags

Selection is by package, computed from the import graph, and not by a JUnit `@Tag` on each test class.

- A tag has to be written on 3,216 annotated methods and kept correct as tests move. A test without its tag never runs on pull
  requests, and nothing reports it, which is the failure this change must not add.
- A package is already the domain boundary of the code: the table above is the existing layout, and a new test in an existing package is
  selected without anyone remembering anything.
- The import graph is derived from the source by a script that runs on every pull request, so it cannot drift from the code.
- When the script meets a path it does not map (a new top-level directory, a new package with no main file), it runs the full suite.
  The unknown case is the expensive case, never the skipped one.

## Decision 2: one hop, not the full transitive closure (deviation from the brief)

The brief asked for the transitive set of importers. The measured graph does not support it. Following imports transitively, the
reverse closure of 26 of the 31 domains reaches 99 to 100% of all test annotations. The core packages (`audit`, `auth`, `maven`, `token`,
`status`, `storage`, `root`, `plugin`) import each other in a cycle, so every package in that cycle reaches every other one.

The default is therefore one hop: a change selects its own package and the packages whose sources import it directly. The second-order
effects that one hop misses are the reason for the smoke set and for the full run on main (see Risks). The transitive rule is kept as
an open question with the numbers above, to be revisited if the one-hop miss count is not acceptable.

## Decision 3: the selection rule

For a pull request, the script runs these steps in order and stops at the first full-run result:

1. Take the changed paths of the pull request (the three-dot diff the coverage gate already computes). Drop the paths on the deny list of
   `coverage_changes.sh`, as the gate does.
2. If any remaining path is not under `reposilite-backend/src/main/kotlin`, `reposilite-backend/src/test/kotlin`,
   `reposilite-backend/src/integration/kotlin` (the Kotlin roots of the domains), it is a full-run trigger. This covers the build
   logic, `buildSrc`, `gradle/`, version catalogs, resources, the frontend, `reposilite-plugins/`, `reposilite-test/` (test workspace),
   `.github/` and anything the script does not know.
3. If a changed Kotlin file lies in an infrastructure package (the list in the table: `root`, `configuration`, `shared`, `web`,
   `plugin`, `storage`, `net...ingot.web`, `net...ingot.schema`), it is a full-run trigger. Infrastructure is decided by what changed, not
   by what imports it.
4. Map each remaining changed file to its domain (its package). For each domain, the selection is the domain plus its direct importers,
   taken from the `import` lines of the main, test and integration sources. The union over all changed files is the selection.
5. If the selection is empty for a changed main file (the `redirect` case), it is a full-run trigger.
6. If the selection holds more than 60 percent of the 3,216 test annotations, it is a full-run trigger. The figure is the count of
   annotations of the selected packages, not a run time.
7. Otherwise the script prints `--tests 'com.reposilite.<domain>.*'` (or `net.onelitefeather.ingot.<domain>.*`) for each selected domain,
   plus the smoke set, and the coverage job runs the `test` and `integration` tasks with those filters.

The script prints a single decision line (`mode=full` or `mode=partial`) and the filter arguments, and writes the list of selected
packages and the reason to the job summary. A failure of the script itself makes the job run the full suite.

### Full-run triggers, in one list

- Any path outside the three Kotlin roots of the backend (build logic, `buildSrc`, `gradle/`, version catalogs, resources, the frontend,
  `reposilite-plugins/`, `reposilite-test/`, `.github/`).
- A changed file in an infrastructure package, including the fixtures at the root of the test and integration source sets
  (`SharedSpecification.kt`, `ReposiliteSpecification.kt`, `IsolatedResources.kt`, `SharedContainers.kt`, `ReposiliteRunner.kt` and the
  JUnit extensions are in package `com.reposilite`, which is `root`).
- A changed file in `com.reposilite` root (`Reposilite.kt`, `ReposiliteFactory.kt`, `ReposiliteLauncher.kt`, `ReposiliteMigrations.kt`,
  `ReposiliteParameters.kt`): application bootstrap.
- The OpenAPI document. Its tests are in `web` (`OpenApiCompletenessTest`, `OpenApiAccessIntegrationTest`,
  `OpenApiSecuritySchemesIntegrationTest`), so the path to the document is covered by the rule for `web`.
- A selection above 60 percent of the test annotations.
- A changed file the script cannot map, a script failure, or an empty selection.
- A push to main. The full suite always runs there, without the script.

## Decision 4: smoke set

Every backend run on a pull request also runs these tests, whatever the selection. They are named in the script as a list of test class
names, so a renamed class fails the script's own test rather than disappearing silently.

- `DependencyRulesTest` (`net.onelitefeather.ingot.architecture`): an ArchUnit test that scans the whole classpath. A change anywhere can
  break a rule, so it cannot be selected by package.
- `WritePathEventCharacterisationTest` (`com.reposilite.maven`): the characterisation test; the only test class whose name carries
  `Characterisation`.
- `OpenApiCompletenessTest` (`com.reposilite.web.api`), `OpenApiAccessIntegrationTest` and `OpenApiSecuritySchemesIntegrationTest`
  (`com.reposilite.web`): the OpenAPI document.
- `SecurityHeadersSettingsTest` and `DashboardPolicyTest` (`net.onelitefeather.ingot.web`): security headers and the dashboard policy.
- `StartupParametersIntegrationTest` (`com.reposilite.configuration`), `StartupRefusalTest` (`net.onelitefeather.ingot.deploy`) and
  `StartupRulesTest` (`net.onelitefeather.ingot.search.rebuild`): startup.

The smoke set is nine test classes. Its run time is not measured yet; the code pull request records it, so that the cost of the
always-run part is known before the rule is merged.

## Decision 5: no coverage upload from a partial run

A partial run measures only the packages it ran. Its line coverage is lower than the full figure for reasons that have nothing to do
with the change, and Codecov would use it as the base of the next comparison. The `coverage` job therefore uploads to Codecov only when
`mode=full`. A partial run keeps the job name `Coverage` and reports success when its tests pass, so the required check is unchanged.
The JaCoCo report is not generated in a partial run.

The alternative, uploading the partial report with a `selected` flag excluded from the project status, is not chosen because it needs a
second Codecov configuration that the repository does not have today.

## Decision 6: how the filters reach the tasks

`testCoverage` depends on `test`, `integration` and the JaCoCo tasks, and passing `--tests` to an aggregate task does not reliably
forward the filter to each dependency. A partial run therefore names the tasks itself:

`./gradlew :reposilite-backend:test --tests '<filters>' :reposilite-backend:integration --tests '<filters>'`

The filters are the output of the script, one `--tests` option per selected package, passed to each of the two tasks. The code pull
request checks that a filter does reach the `integration` task (the task is provided by the `com.coditory.integration-test` plugin, so
its filter is verified by a run, not assumed). If it does not, the fallback is the full `integration` task with the partial `test` task,
and the measurement below records which of the two was used.

## Risks and mitigation

- **Calls without imports.** Facades, events, Javalin route registration and `ServiceLoader` (14 use sites under `reposilite-backend/src/main`)
  connect packages without an `import` in the caller. Mitigation: the smoke set; the infrastructure list, which covers the event and
  route owners; and the full run on main.
- **Server-start tests.** An integration test that starts the server through `Reposilite`, `ReposiliteRunner` or the factories depends on
  the whole composition, not on one package. Mitigation: those helpers are infrastructure (full run when they change), and the startup
  smoke tests run on every pull request.
- **Changes in a package whose tests live elsewhere.** `identity`, `login` and `migration` have no main files, only integration tests. A
  change to their tests selects their own package and nothing else, which is correct, because nothing imports them.
- **Big domains get no saving.** `maven` (63 percent) and `auth` (62 percent) cross the threshold and run the full suite. This is
  deliberate for the first version; the threshold is an open question.
- **A missed failure reaches main.** A partial run can pass while the full run on main fails. This is accepted and handled by the policy
  below. It is not prevented, because preventing it would mean running the full suite on every pull request, which is the cost this
  change removes.

### Policy for a failure that a partial run missed

- The full run on main is the backstop. When it fails on a commit whose pull request ran partially, the author of the merge fixes
  forward on main within one working day, or reverts the merge commit if the fix is not clear by then.
- The miss is recorded: the package that changed, the package that failed, and the reason it was not selected. The fix to the mapping
  (a new package edge, an infrastructure entry, a smoke test) ships as its own `fix(coverage)` pull request, so the rule improves
  instead of being patched by hand.
- A failed main run is visible in the workflow list of the repository and in the notifications of the maintainers who watch it. This
  change adds no new alert channel.

## Measurement

- **Baseline.** Before the change is merged, record the `Coverage` job duration of the last 20 backend pull requests on main (the median
  and the range). The single run above (79 minutes) is one sample and is not the baseline.
- **Success criterion.** The median `Coverage` job duration of the first 30 backend pull requests after the change is at most half of
  the baseline median. Partial runs and full runs are reported separately, so the median of partial runs shows what the rule saves and
  the share of full runs shows what it costs.
- **Miss count.** After 30 partial runs, or eight weeks, whichever is later, count the commits on main where the full run failed and the
  pull request had run partially. Two or more unexplained misses in that period return the rule to full-run-only, and the design is
  reopened.
- **Filter check.** The code pull request records the output of the script for the sample of recent pull requests, to show which of them
  would have run partially and which would have run the full suite.

## Open questions with defaults

1. **Threshold.** 60 percent of the test annotations (default). Measured: `maven` 62.8 percent and `auth` 61.5 percent are full runs at
   this value, `audit` 53.3 percent is partial. A duration-weighted threshold needs a timing source, which the first version does not use.
2. **Integration tests.** Selected by the same rule, plus the smoke set (default). The alternative is the full integration suite on every
   pull request, which keeps the 574 annotated integration tests (and their 21 parameterised ones) on every run, and which is where most of
   the time goes.
3. **Plugin API job.** Not affected (default). It compares the plugin API with Reposilite 3.5.28 and loads the upstream jars. It does not
   depend on the test selection.
4. **Depth of the selection.** One hop (default, see Decision 2). The transitive closure is the brief's choice and selects 99 to 100
   percent of the tests for 26 of the 31 domains.
5. **Nightly full run.** The brief names a nightly run, but `ci.yml` has no `schedule` trigger today. Default: no new schedule in this
   change, and the full run on each push to main is the backstop. A scheduled full run can be added in a follow-up if the maintainer asks
   for one.
6. **Codecov on partial runs.** No upload (default, see Decision 5).
