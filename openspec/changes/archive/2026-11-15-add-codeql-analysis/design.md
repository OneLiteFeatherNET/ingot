# Design

## Decisions

1. **Kotlin uses a manual build, not `none` and not `autobuild`.** The CodeQL documentation states that Kotlin analysis
   requires a build, so `build-mode: none` is not an option for `java-kotlin`. `autobuild` lets CodeQL pick the Gradle
   tasks, and the repository has several modules whose compiled output matters, so the choice of tasks stays in the
   repository. The manual step is:

   ```
   ./gradlew classes --no-daemon --max-workers=2
   ```

   `classes` compiles the main source sets of every included module, which includes the bundled plugins under
   `reposilite-plugins/` and the frontend project that the backend depends on. Test sources are not compiled, so the
   analysis covers the code that ships, and test code adds no runtime. The `--max-workers=2` limit follows the team rule
   for Gradle, and `--no-daemon` keeps the runner clean for CodeQL's own tracing.

2. **JavaScript and TypeScript use `build-mode: none`.** The dashboard is analysed from its sources. The
   `paths-ignore` list in `.github/codeql/codeql-config.yml` excludes `**/node_modules/**`, `**/build/**` and the
   fixture tree `reposilite-test/workspace/**`, which holds real Maven artifacts as test data.

3. **Runtime budget.** Each language job has `timeout-minutes: 45`. The expectation is that the Kotlin job is dominated
   by the Gradle compile, which the `gradle/actions/setup-gradle` cache shortens after the first run. The first five
   runs on main are measured. If the median of the Kotlin job exceeds 30 minutes, the scope of the manual step is
   reduced to the modules that the backend pulls in, and that reduction is a separate `ci(security)` commit recorded
   here. Pull requests cancel an older run of the same branch (`concurrency`), so a burst of pushes does not queue
   several Gradle builds.

4. **Pinned references reuse the existing SHAs.** The checkout, Java, Gradle and CodeQL refs are the same full SHAs that
   `ci.yml` and `scorecard.yml` already use, with the same version comments. `github/codeql-action` serves `init`,
   `analyze` and `upload-sarif` from one repository and one ref, so the Scorecard step and the new workflow cannot drift
   apart.

5. **Findings surface, but do not fail the pull request.** Results go to code scanning, which shows them on the pull
   request and in the Security tab. The workflow exits green regardless of findings. The gate comes later and lives in a
   repository ruleset, not in the workflow. A workflow-side gate would need a token that reads the alert API and would
   reimplement what the ruleset already does with less code.

6. **Baseline and gate criteria.** The baseline is clean when the scheduled runs of two consecutive weeks on main show
   zero open alerts of severity high or critical. Once that holds, a maintainer adds a ruleset that requires code
   scanning results for new high and critical alerts on pull requests into main, and then decides whether to make the
   check required. Until then the check is not required. This decision is recorded as an open question below, with
   this default.

7. **Fork pull requests.** A pull request from a fork runs with a read-only token, so the SARIF upload from such a run is
   refused and its alerts are not recorded. This is accepted: fork workflow runs already need a maintainer's approval
   (see `.github/RELEASE.md`), and the maintainer's own pushes and the scheduled run cover the code that merges.

8. **Interaction with the nightly rule.** The workflow lives under `.github/`, which the `changes` job of
   `release-please.yml` ignores, so adding or editing `codeql.yml` never causes a nightly image. The workflow pushes no
   package and writes no attestation, so the image rules in `CLAUDE.md` are not touched.

9. **Scorecard.** `scorecard.yml` counts a SAST tool that runs on pull requests. The CodeQL workflow is that tool, so the
   check that currently reports the absence of SAST becomes passing after the first pull request run.

## Permissions

| Job | Permissions |
|---|---|
| top level | `contents: read` |
| `analyze` | `contents: read`, `security-events: write` |

`actions: read` is required by CodeQL only for private repositories. This repository is public, so the job does not
request it. If the repository becomes private, the scope is added in the same workflow file.

## Open questions with defaults

- **When does the gate become a ruleset?** Default: after two consecutive clean weekly baselines on main. A maintainer
  confirms the date when the second baseline is clean.
- **Is the check required?** Default: no, until the ruleset from decision 6 exists and has run for one release cycle.
- **Does the team want `security-and-quality` queries?** Default: the default `security-extended` query suite, which
  CodeQL uses when no suite is named. The quality suite adds noise and is not part of this change.

## Risks

- Kotlin extraction depends on the kapt stubs. If CodeQL reports too few Kotlin files in the extraction log, the first
  pull request run shows it, and the fix is a `ci(security)` change to the manual step rather than a reduced scope.
- The Gradle build inside the CodeQL job downloads the Node toolchain for the frontend project. The `vulnerabilities`
  job already does this without a separate `setup-node`, so no extra step is added; the build time is part of the
  measured budget.
