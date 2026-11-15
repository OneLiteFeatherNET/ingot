# Proposal

## Why

An operator who keeps release builds in one repository and snapshot builds in another has no way to say so. Ingot accepts
a `-SNAPSHOT` deploy into `releases` and a release deploy into `snapshots`, and the mistake is found weeks later as a broken
build or a polluted release repository. Teams that come from Nexus expect a version policy per repository and a repository
they can freeze on its own: a finished release line, a repository under audit, a migration target that is being verified.
The cluster-wide read-only mode (`add-read-only-mode`, on main) stops writes everywhere, which is too much for that.

The dashboard also hides what already exists. A repository's write behaviour is split over `redeployment` and the type, and
an operator has to know that "no redeployment" means "409 on the second upload". A repository without a declared type shows a
"legacy" badge, but the only way out is to find the type field in the settings form and choose a value by hand.

This change adds two optional fields, shows the write policy and the type in plain words, and offers a one-click "declare
type". An existing installation sees no difference until an operator sets a field.

## What Changes

- **`versionPolicy`.** New optional field of a repository in the `maven` settings domain: `RELEASE`, `SNAPSHOT` or `MIXED`.
  Absent means `MIXED`, which is today's behaviour, and the key is not written back unless an operator sets it. On a hosted or
  untyped repository, `RELEASE` refuses a deploy into a `-SNAPSHOT` version directory and `SNAPSHOT` refuses a deploy into any
  other version directory, both with `400` and a message that names the repository and the policy. Version-independent
  metadata files are exempt. The policy is checked for deploys only; reads, deletes, mirror cache fills and cleanup runs are
  unchanged.
- **`readOnly`.** New optional boolean per repository, default `false`, not written back unless set. A repository with
  `readOnly: true` refuses deploys and deletes with `405` and a message that names the repository. Reads and mirror cache
  fills are unaffected. Scheduled cleanup runs skip the repository and report it as skipped. It is distinct from the global
  read-only mode, which answers `503` for every repository and stays as it is.
- **Validation only on opt-in.** `versionPolicy` and `readOnly` are rejected with the usual `400` and rule list on a declared
  PROXY or GROUP repository, and `versionPolicy` is rejected on a GROUP. A repository that sets neither field is validated
  exactly as before.
- **Write policy in the UI, derived.** The repository list API and the dashboard show `ALLOW`, `ALLOW_ONCE` or `READ_ONLY`
  for hosted and untyped repositories. It is computed from `redeployment` and `readOnly` and stored nowhere. The settings form
  edits it as one choice that writes those two keys, so `redeployment` stays the only source of truth for the `409`.
- **Type badge and declare type.** The list and the settings view show the derived type (hosted, proxy, group, mixed) next
  to whether it is declared. A repository without a declared type gets a "declare type" action that writes `type` with the
  derived value and nothing else, after running the same checks the server runs. A derived `mixed` repository gets a warning
  and a suggestion to split it into a group and a proxy, and no one-click action.
- **List API.** `GET /api/maven/repositories` gains the optional fields `versionPolicy`, `readOnly` and `writePolicy`, only
  additive.
- **Documentation.** `docs/gitbook/reference/repository-settings.md` (two keys, the write policy table, status codes),
  `docs/gitbook/reference/repository-types.md` (new rules, the declare action), `docs/gitbook/reference/api-errors.md`,
  `docs/gitbook/reference/http-api.md` (list fields), and a how-to `docs/gitbook/how-to/separate-releases-and-snapshots.md`
  listed in `docs/gitbook/SUMMARY.md`.

### Out of scope

- The `format` field. It belongs to `add-repository-format-spi`; this change neither adds nor reads it.
- `layoutPolicy`, named storage, routing rules, proxy hardening, a writable group member and group member ordering
  (report 5.2 rows that are separate changes).
- Applying `versionPolicy` to a PROXY (filtering what is fetched) and to cleanup scope or staging. A PROXY with a policy is
  rejected for now.
- The "Nexus-style starter layout" action and the Repositories admin page. They belong to the navigation work
  (`add-repositories-admin-page`, N6) and may reuse the fields added here.
- Any change to the global read-only mode, to the fresh-install defaults (`releases`, `snapshots`, `private`), to `/api/maven/*`
  routes, to file layouts on disk or in S3, and any rewrite of stored settings on upgrade.
- Legal and business model questions.

## Capabilities

### New Capabilities

- `repository-policy`: the `versionPolicy` and `readOnly` fields, their enforcement and validation, the derived write policy
  and type shown in the API and the dashboard, and the declare-type action.

## Impact

- **Type and scope.** The change ships under `feat(maven)`. Branches use `test`, `feat` and `docs` commits with the scope
  `maven`. The pull request title, squash-merged onto main, is:

  `feat(maven): add repository version policy, read-only flag and write policy view`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin. Both
  keys are optional, absent means today's behaviour, and no stored document is rewritten. The title carries no `!` and no
  commit carries a `BREAKING CHANGE:` footer. No environment variable is added.
- **Behaviour that changes by default.** None. A deploy into any existing repository is judged as before.
- **Rollback.** A settings document that carries the new keys, loaded by an older Ingot or Reposilite, is read with the keys
  ignored, so the policies stop being enforced there. The how-to says so.
- **Dependencies on other changes.**
  - `add-read-only-mode` (on main): the per-repository flag is distinct from it. Its `503` takes precedence, and its scheduler
    guard is the pattern for the cleanup skip.
  - `add-repository-format-spi` (not merged): both changes add a trailing optional key to `RepositorySettings`. Whichever lands
    second rebases the constructor, and tests use named arguments so neither depends on the order. No other coupling.
  - `restructure-navigation` (on main, #76 and #82): the dashboard pieces here live in the existing repository list and
    settings view, so they need no new route. `add-repositories-admin-page` later moves them into its table.
- **Code.** `reposilite-backend` (maven settings, repository model, deploy and delete checks, list API, cleanup plugin),
  `reposilite-frontend` (repository list badges, settings form, declare action), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate add-repository-policy-fields --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-repository-policy-fields` prints nothing.
