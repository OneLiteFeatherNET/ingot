# Proposal

## Why

Ingot accepts uploads, credentials and repository paths from the network, parses them in a Kotlin backend, and renders
a Vue dashboard from the same server. Today the repository has three automated checks that look at its code and its
dependencies: the test suites, Trivy for known CVEs in the libraries it ships, and the Scorecard analysis. None of them
reads the code we wrote for the patterns that produce vulnerabilities, such as an unsanitised path that reaches the
filesystem or a value that reaches a log or an HTML response unescaped. A maintainer who reviews a pull request
sees those patterns only if a reviewer happens to notice them.

An operator who asks "how do you find bugs before we do" gets an answer that starts with tests and ends with a
dependency scanner. CodeQL is the analysis that answers the first half for the code itself, and it is free for public
repositories.

Facts checked against origin/main (`db0717b2b`):

- `.github/workflows/` holds `ci.yml`, `release-please.yml` and `scorecard.yml` only. No CodeQL workflow exists.
- `scorecard.yml` already uploads SARIF through `github/codeql-action/upload-sarif` pinned at
  `33d7b85da1a04171e211befe2c739303b9a4c066` (v4.39.3). The code scanning dashboard is therefore already the sink
  for findings, and the same action ref can be reused for `init` and `analyze`.
- The backend is Kotlin built by Gradle 9.5.1 with JDK 21 (`gradle/wrapper/gradle-wrapper.properties`, the `licenses`
  and `coverage` jobs in `ci.yml`). Kotlin code goes through kapt, so a build is needed before analysis.
- The `changes` job of `release-please.yml` ignores `.github/**`. A new workflow file therefore never triggers a
  nightly image, and the nightly rule in `CLAUDE.md` is not touched.

## What Changes

- **C1 Analysis on pull requests, on main and weekly.** A new `.github/workflows/codeql.yml` runs on `pull_request`
  into `main`, on `push` to `main`, and on a weekly `schedule`.
- **C2 Two languages.** The matrix covers `java-kotlin` and `javascript-typescript`. Kotlin is analysed from a real
  build: CodeQL does not support build mode `none` for Kotlin. JavaScript and TypeScript, including the `.vue` files
  of the dashboard, are analysed without a build.
- **C3 Pinned references.** `actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle` and
  `github/codeql-action` are pinned by commit SHA with a version comment, using the same refs that `ci.yml` and
  `scorecard.yml` already use. Renovate keeps them current through the existing digest pinning.
- **C4 Least privilege.** The top level stays `contents: read`. Only the `analyze` job adds `security-events: write`.
- **C5 Report first, gate later.** The workflow never fails on a finding. Findings appear as code scanning alerts and as
  pull request annotations. A repository ruleset that blocks new high and critical alerts is proposed as a follow-up
  once the baseline is clean (see `design.md`). The check is not a required status check in this change.

### Out of scope

- Turning the repository's CodeQL default setup on or off in the settings. A maintainer checks that default setup is
  not also enabled, because two analyses of the same languages upload duplicate alerts.
- Fixing the findings that the first runs report. Each finding becomes its own change or its own pull request.
- Required status checks and the code scanning ruleset. Both are repository settings that a maintainer changes after
  the baseline.
- Secret scanning, dependency review and Dependabot alerts.
- Scanning of the container images with CodeQL. Trivy already scans the images.
- Changes to `release-please.yml`, the Dockerfiles or the images.

## Capabilities

### New Capabilities

- `code-scanning`: the CodeQL analysis of the backend and the dashboard on pull requests, on main and on a schedule, its
  build, its permissions and the way its findings reach maintainers.

## Impact

- **Type and scope.** The change ships under `ci(security)`, the scope the other supply chain changes use. The pull
  request title, squash-merged onto main, is:

  `ci(security): run codeql on java-kotlin and javascript-typescript`

  The proposal commit uses `docs(openspec)`. The code commits all use `ci(security)`.
- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, image, manifest or
  plugin changes. There is no `docs/gitbook/` page to update, because the analysis is invisible to operators.
- **Behaviour that changes.** Pull requests get one more check, which takes a Gradle compile and therefore some minutes.
  The Security tab gets alerts. No release, nightly or image changes.
- **Dependencies.** `harden-ci-supply-chain` (merged) defines the SHA pinning rule that this change follows. It does not
  depend on `sign-release-artifacts` or on `attach-release-sboms`.
- **Code.** `.github/workflows/codeql.yml`, `.github/codeql/codeql-config.yml`, a workflow shape test under
  `.github/scripts/tests/`.

## Verification

- The workflow shape test is written first and fails on main, because the workflow does not exist yet.
- The first run on the pull request shows both languages analysed and the Kotlin extraction log lists the backend
  sources. The first run on main publishes alerts to the Security tab.
- `openspec validate add-codeql-analysis --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-codeql-analysis`
  prints nothing.
