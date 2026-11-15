# Design

## Tool choice

`oasdiff` (github.com/oasdiff/oasdiff) is an actively released OpenAPI diff tool (v1.37.0 on 2026-11-08) with a `breaking`
command and a GitHub action (`oasdiff/oasdiff-action`, v0.1.23 on 2026-11-08, tag commit
`8a211b41745fcecdfa4a86531ffbf08934fb14f6`). The `breaking/` action is a Docker action built from its own Dockerfile
(`using: docker`, `image: Dockerfile`), so pinning the action to the commit SHA pins the container recipe with it. The match to
the repository convention is exact: every action in `.github/workflows/ci.yml` is pinned as
`owner/repo@<40 character sha>  # vX.Y.Z` (for example `actions/checkout@d23441a4...  # v6.1.0` at line 62). Renovate is expected
to raise the SHA and the tag together, as `harden-ci-supply-chain` sets up.

The implementation re-reads the action's Dockerfile at the pinned commit. If it pulls its base image by a floating tag, the task
records that and either pins it through a digest override or runs the published `ghcr.io/oasdiff/oasdiff` image by digest
directly. Which of the two is used is decided in that task, because it depends on the Dockerfile at the pinned commit.

`review: false` and `github-token: ''` are set. The default uploads an encrypted copy of both specs to oasdiff.com and posts a
comment; Ingot's pipeline sends nothing out and runs with `contents: read` only.

Alternatives considered:

- `openapi-diff` by OpenAPITools: maintained less actively and with fewer breaking-change rules. Rejected.
- A hand-written script comparing path and method sets: catches removals only, not incompatible schema changes. Rejected.
- `optic`: needs a service account for its useful features. Rejected for a FOSS project that wants no external dependency.

## Why a committed snapshot

The document is not a build output that can be copied. The kapt processor writes an annotation index into the class output
(`reposilite-backend/build.gradle.kts:91-93`), and the plugin assembles the final document when the server runs
(`web/application/JavalinConfiguration.kt:212-222`). Getting the base branch's document would mean building and starting the
base branch inside the pull request job.

With a snapshot, the base document is `git show origin/<base>:docs/openapi/ingot-openapi.json`, and the pull request's is the
file in the checkout. A test keeps the file equal to what the server serves, so the file cannot lie. The cost is that an API
change touches a generated file; the benefit is that the API change is visible in review, which is the point.

The exact way the test obtains the document (an in-process server request, or the plugin's generator called directly) is
confirmed in the first red test. If neither is possible without a running container, the fallback is the integration job that
already starts the server, which fetches `/openapi` and compares.

The `info.version` field is normalised to a fixed placeholder in the snapshot, so a release does not touch it, and the release
upload replaces it with the real version.

## The `!` rule

`release-please` reads `type(scope)!:` as a major bump. The gate reuses that definition so that the two agree: a title matching
`^[a-z]+(\([^)]+\))?!:` is a declared break. The check does not demand the footer; the commit-message hook and review do that.
Only the title is read, because the squash-merged title is the commit on main.

The workflow runs on `opened`, `synchronize`, `reopened` and `edited`, so fixing the title turns the check green without a push.
That is why it is a separate workflow: adding `edited` to `ci.yml` would rerun the whole build on every title edit.

Fork pull requests run with a read-only token and the workflow needs nothing more.

## What counts as breaking

oasdiff's default severity levels decide. Error level fails the check: removed endpoint or method, removed or renamed required
parameter, new required request field, narrowed response, changed type. Warning level (for example a removed optional field in
a request) is reported and does not fail. Path parameter names are not part of the match (`include-path-params` stays off),
because renaming `{gav}` to `{path}` does not change a URL. A `.oasdiff.yaml` file in `docs/openapi/` carries any project-level
adjustment, and each ignore entry has a comment saying why.

## Consequences

- A contributor who changes an annotation must refresh the snapshot. The failing test says how, with one command.
- The first run after merge may report a large "added" set if `complete-openapi-spec` has not landed. Additions are never
  breaking.
- The check does not see changes that are not in the document. That is why completeness comes first.
