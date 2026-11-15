# Design

## What already exists

Verified on this branch:

- The derived type is done. `effectiveRepositoryType` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/RepositoryType.kt:54`)
  derives hosted, proxy, group or mixed, the list API returns it with `typeExplicit`
  (`maven/api/RepositoryListApi.kt:27-34`, `maven/RepositoryService.kt:258`), and the frontend shows it with a "legacy" hint
  (`reposilite-frontend/src/store/maven/repositoryTypes.js:27`, `components/tokens/RepositoryTypeBadge.vue`). The type select
  with a switch confirmation is in the settings view (`SETTING_TYPE_OPTIONS`, `switchedEntry`, `violationsOf`). This change
  does not rebuild any of it. What is missing is the one-click action and the `mixed` warning.
- 405 for upload to a typed PROXY or GROUP exists (`maven/MavenFacade.kt:135-141`, `maven/RepositoryType.kt:67`), and 409 for
  a redeploy exists (`maven/RepositoryService.kt:107`, from `Repository.acceptsDeploymentOf`, `maven/Repository.kt:69`).
- The global read-only mode is on main (`net.onelitefeather.ingot.readonly`, `ReadOnlyFacade`, scheduler guard at
  `cleanup/CleanupScheduler.kt:58`).
- No `versionPolicy`, no per-repository `readOnly` and no write policy exist. `RepositorySettings` has `redeployment` and
  `preserveSnapshots` only (`maven/application/MavenSettings.kt:144-147`).
- Validation is the existing mechanism: `ValidatedSettings.violations()` (`configuration/shared/api/ValidatedSettings.kt:33`),
  with per-repository rules in `RepositorySettings.violations()` and `typeViolations()` (`MavenSettings.kt:182`, after it).

## D1: two optional trailing keys, derived on read

`RepositorySettings` gets `versionPolicy: VersionPolicy? = null` and `readOnly: Boolean? = null`. Both are nullable so that
absence is distinguishable from an explicit value: the mapper writes `NON_NULL`, so a repository that never set them is
written back without them, whatever else is edited, as `add-repository-format-spi` D1 does for `format`. The effective values
are `versionPolicy ?: MIXED` and `readOnly ?: false`, computed in `RepositoryFactory` (`maven/RepositoryFactory.kt:52`) and
exposed on `Repository` next to `redeployment`.

Alternative considered: non-null with defaults. Rejected: a default `false` would be written into every document on the next
save, which rewrites stored data for no gain.

## D2: version policy is judged on the deploy path

The check sits next to `checkAcceptsUploads` in `MavenFacade.deployFile` (`MavenFacade.kt:143`), before the storage write, so
a refused file leaves no trace. It looks at the parent directory of the deployed path, which is the version directory for
every artifact file and its checksums:

- `RELEASE`: the parent directory name ends with `-SNAPSHOT` (same suffix rule as `maven/MetadataRecalculation.kt:32` and
  `cleanup/CleanupPlanner.kt:81`): refuse with `400`.
- `SNAPSHOT`: the parent directory name does not end with `-SNAPSHOT` and the file is not a `maven-metadata.xml` or one of its
  checksums: refuse with `400`.
- `maven-metadata.xml` and its checksums are always exempt, because artifact-level metadata is not tied to a version and
  Maven writes it on every deploy. This matches the redeployment exemption in `Repository.acceptsDeploymentOf`.

`400` is the maintainer's decision for a snapshot deploy into a RELEASE repository. The report marks Nexus's own status as
unverified, so the documentation states Ingot's answer and does not claim parity.

Not judged: deletes, reads, mirror cache fills, cleanup, metadata regeneration. A policy changed on a repository with
existing content does not touch that content.

## D3: the per-repository read-only flag answers 405

Order of refusals for a deploy or delete: global read-only `503` (dispatch level), then `401` and `403`, then the type check
`405` and the flag `405`, then the version policy `400`, then the redeploy `409`. The flag reuses the existing `405` for "this
repository does not take writes" and the same facade method, so the routes at `MavenEndpoints.kt:237` and `:268` pick it up
without edits, as does the generate-POM route (`MavenFacade.kt:189`). The message names the repository and says it is
read-only, so it is not confused with the type message.

Alternatives considered: `403` (reads as a permission problem and invites token changes), `503` (is the cluster-wide mode, and
a retry will not help). Open question for the maintainer: confirm `405`.

Cleanup runs call `deleteUnchecked` (`cleanup/application/CleanupPlugin.kt:124`), which skips the HTTP checks. A read-only
repository would then lose files to a scheduled policy, which defeats the flag. The cleanup plugin skips a read-only
repository and records it as skipped, the same way the scheduler guard handles the global mode.

Mirror cache fills are not client writes and are unaffected, as with the global mode. The flag is rejected on a declared PROXY
or GROUP anyway, so the case only arises for an untyped repository that has mirrors.

## D4: validation only on opt-in

New rules are added to `RepositorySettings.violations()` and fire only when a new key is set:

- `versionPolicy` on a declared GROUP: "a group inherits nothing, each member enforces its own policy".
- `versionPolicy` or `readOnly` on a declared PROXY: not supported in this change (see out of scope).
- Untyped repositories accept both keys, since they take uploads.

A repository that sets neither key is validated by the old rules only, so no existing document can start failing. This
follows report 5.5 with one deliberate narrowing: `versionPolicy` on a PROXY is rejected here instead of accepted and ignored,
because an accepted but unenforced policy is worse than a clear refusal. A later change that filters proxy fetches lifts it.

## D5: write policy is a view, not a key

`writePolicy` is computed, never stored: `READ_ONLY` when `readOnly` is true, else `ALLOW` when `redeployment` is true, else
`ALLOW_ONCE`. It is `null` for a typed PROXY or GROUP. It appears in `RepositorySummary` and on the repository detail. The
settings form shows one select and writes the two real keys: ALLOW sets `redeployment: true` and removes `readOnly`,
ALLOW_ONCE sets `redeployment: false` and removes `readOnly`, READ_ONLY sets `readOnly: true` and leaves `redeployment`
alone, so switching back restores the previous redeploy choice. The `409` behaviour is unchanged because `redeployment` stays
the source of truth, which is report principle 4.

## D6: declare type is a client action over existing rules

The action takes the derived type from the list API, writes `type` through the normal settings save, and shows the server
rule list if the save is refused. Before saving, the dialog runs `violationsOf` (`repositoryTypes.js`, end of file) so the
operator sees, for example, that a derived group whose mirror has `store` enabled would be rejected. `mixed` offers no action,
only the warning text and the suggestion to split into one GROUP and one PROXY. No server endpoint is added.

## D7: list API stays additive

`RepositorySummary` gets three nullable fields with null defaults, `versionPolicy` (effective value as a string), `readOnly`
and `writePolicy`. Existing consumers ignore unknown JSON fields. The OpenAPI description is updated. Plugins that
construct `RepositorySummary` keep compiling because the new constructor parameters have defaults.

## Risks

- Constructor order clash with `add-repository-format-spi`: mitigated by named arguments and a rebase task.
- An operator sets `RELEASE` on `snapshots` by mistake and deploys fail with `400`: the message names the repository and the
  policy, and the how-to shows the fix.
- Rolling back to an older server silently drops enforcement: documented.
