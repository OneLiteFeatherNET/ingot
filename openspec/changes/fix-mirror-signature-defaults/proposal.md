# Proposal

## Why

A proxied repository that filters by file extension drops every file whose extension is not on its list, and the only trace
is a debug log line. The default list (`allowedExtensions` of a mirrored repository) covers the artifacts, the checksum files
and the PGP signatures (`.asc`), but not Sigstore bundles (`.sigstore.json`). Projects that publish Sigstore bundles put them
beside the artifact, as they put `.asc` signatures.

An operator who puts Ingot in front of such a project with the defaults gets a `404` for the bundle. A client that verifies
with Sigstore then fails, and nothing on the Ingot side says why.

## What Changes

- **Q12 Default extension.** `.sigstore.json` joins the default `allowedExtensions` of a mirrored repository, directly after
  `.asc`, in `MavenSettings.kt` (`MirroredRepositorySettings`).
- **Scope of the default.** Verified against origin/main. The default is written into the stored shared settings only when
  a settings domain is saved, and then every domain is written with its current value. So the default applies in two cases:
  - An installation whose stored document already holds a `maven` domain keeps the list it has. Ingot does not rewrite it.
  - An installation without a stored `maven` domain takes the new default. Its first save of any settings domain stores the
    new list.
  No migration step is added.
- **Documentation.** The default lists on four pages (`storage-and-mirror-caching`, `create-proxy-repositories`,
  `repository-settings`, `settings-domains`) gain `.sigstore.json`. The upgrade notes say that a stored list is kept.

### Out of scope

- Hosted repositories. Only mirrored repositories read `allowedExtensions`. Deploys are checked against the repository's
  acceptance rules, not against this list, and they stay as they are.
- Download statistics. `RepositoryService.ignoredExtensions` leaves `.asc` out of the resolved-request count, and
  `.sigstore.json` is not on that list, so bundle downloads are counted as downloads. Whether to change that is a separate
  decision.
- The search index. `ArtifactPathParser` treats `.asc` as a special case and this change does not touch it.
- Verifying signatures, or any content type for bundles.
- Rewriting the lists that existing installations have stored.

## Capabilities

### New Capabilities

- `mirror-extension-defaults`: the default extensions of a mirrored repository, and when a default applies to a stored
  configuration.

## Impact

- **Type and scope.** The change ships under `fix(maven)`. The commits use `test(maven)`, `fix(maven)` and `docs(gitbook)`.
  The pull request title, squash-merged onto main, is:

  `fix(maven): allow sigstore bundles in the default mirror extensions`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change, and a Reposilite installation pointed at Ingot keeps its stored lists. The title carries no `!` and no commit
  carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** For a mirror that uses the default list, a request for `*.sigstore.json` is
  proxied where it used to get a `404`. For a stored list nothing changes. The change adds one file type to one list and
  only where the upstream serves it, so it is judged safe as a default.
- **Code.** `reposilite-backend` (maven application settings and tests) and `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate fix-mirror-signature-defaults --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-mirror-signature-defaults` prints nothing.
