# Proposal

## Why

Ingot is to become a repository manager for more than Maven: raw files, Helm charts, npm, PyPI and container images on one
server. Today every request under `/{repository}/**` goes to Maven code, and Maven's rules are spread through the parts that
should be shared: what counts as a metadata file (`maven-metadata.xml` in `Repository.kt:70,73`, `ResolutionProvider.kt:147`,
`MirrorCache.kt:170`), what counts as a download (`RepositoryService.kt:67-82`), and even the S3 storage layer, which
invalidates `maven-metadata.xml` after every write (`storage/s3/CachableS3Client.kt:49`). A repository has no way to say
"I am not Maven".

So there is no place to put a second format without copying the Maven plugin, and no way to tell an operator's repository
apart from one that a later format will create. This change adds that place, with Maven as its only occupant, and changes
nothing a client, an operator or a plugin can observe.

## What Changes

- **A `format` attribute on repositories.** `RepositorySettings` in the `maven` settings domain gets an optional `format`
  (string). Absent means `maven2`. An existing `configuration.shared.json` or settings row stays valid as it is, and a
  repository whose `format` was absent is written back without the key unless an operator sets it. The only accepted value
  in this change is `maven2`; anything else is refused on update with a message naming the repository and the known formats,
  and a stored repository with an unknown format is not served and is logged at start (the case of rolling back from a
  later Ingot). `RegisteredRepository` and `Repository` expose the resolved format.
- **The `RepositoryFormat` SPI** in `net.onelitefeather.ingot.format`, marked experimental (opt-in annotation), discovered
  through `ServiceLoader` like `StorageProviderFactory`. This change ships the parts that have a caller: identity, path rules
  (metadata, checksum sidecar, resolution metadata, download counting, redeploy and cache exemptions), the request handler,
  the mount, the group policy and the storage hints. Design D3 defines the full surface, including metadata generation,
  cleanup criteria, search indexing and permission scope, which the changes that first need them add.
- **Maven as the first format.** `Maven2Format` implements the SPI by delegating to today's code, unchanged. The rule sites
  above ask the repository's format instead of hard-coding Maven names.
- **A request dispatcher.** One core route set on `/{repository}/<path>` (HEAD, GET, POST, PUT, DELETE) resolves the
  repository through the registry and hands the request to its format's handler. An unknown repository goes to the default
  format, so the `404` page stays exactly as it is. The dashboard's shared-path hand-over and the redirect plugin call the
  dispatcher instead of building `MavenEndpoints`. The priority order (dashboard at `HIGH`), the `PostAuthHandler` after
  filter, the reserved-segment warnings and the OpenAPI document stay as they are.
- **The storage boundary.** `StorageProvider` stays the byte layer. The three Maven leaks named by the coupling analysis are
  closed: the S3 metadata invalidation becomes a related-key rule that the format supplies (Maven supplies today's rule);
  `RepositoryVisibility` in `storage.api` is frozen as the one allowed Maven import, guarded by a test; `VersionComparator`
  stays a generic natural-order listing comparator, and version semantics belong to the format.
- **Group rule.** A group, or an untyped repository that proxies local repositories, may only reference repositories of its
  own format. With one format this never fires; it is defined now so a second format cannot create a mixed group.
- **Documentation.** `docs/gitbook/reference/repository-settings.md` (the `format` key), `web-routes.md` (dispatch),
  `plugin-api.md` (the experimental SPI), `explanation/backend-architecture.md` and `explanation/drop-in-compatibility.md`
  (what happens to `format` on a rollback).

### Out of scope

- Any second format. Raw, Helm, npm, PyPI and OCI are later changes, each with a proposal of its own.
- Splitting repository definitions out of the `maven` settings domain, and the search index schema.
- Host-based routing. Design D7 describes how it plugs in; no host mount is registered here.
- Plugin-provided formats. The SPI is discovered by `ServiceLoader` on the server class path; loading formats from plugin
  jars waits until the SPI is declared stable.
- Showing the format in the dashboard beyond the generated settings form, and in `/api/maven/repositories`.

## Capabilities

### New Capabilities

- `repository-format`: the `format` attribute, its default and write-back, validation, and the SPI with Maven as its only
  implementation.
- `repository-dispatch`: routing of `/{repository}/**` to the repository's format with unchanged responses.
- `storage-boundary`: what the storage layer may know about formats, and the related-key rule.

## Impact

- **Type and scope.** The change ships under `feat(repository)`, with `test(repository)`, `refactor(repository)` and
  `docs(repository)` commits. The scope is `repository` rather than `maven`: the new attribute belongs to every repository,
  the new code lives outside `com.reposilite.maven`, and the changelog entry should read as a property of repositories, not
  as a Maven feature. It is `feat` because operators gain a new settings key. The pull request title is:

  `feat(repository): add repository formats with maven2 as the default`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. `format` is optional, absent by default and not written back when absent. Routes, status codes, headers and bodies
  are unchanged for every Maven and Gradle client and for the dashboard. The new SPI is opt-in experimental API. The title
  carries no `!`.
- **Binary surface.** `Repository` gains a property, `RegisteredRepository` gains a member (it was declared not yet stable
  for plugins in `extract-repository-registry`), and `RepositorySettings` gains a trailing constructor parameter. Its previous
  constructor is kept as a secondary constructor, so the gate shows no new constructor break. The JVM signature of its
  `copy` changes; settings classes are not part of the documented plugin API and this constructor already differs from
  Reposilite 3.5.28, so the difference is named in the pull request rather than marked breaking (design D1).
- **Order.** Depends on `gate-plugin-abi` (the report must run on this pull request) and on `extract-repository-registry`
  (the dispatcher and the format lookup read through the registry). Both are merged first.
- **Code.** `reposilite-backend` (new package `net.onelitefeather.ingot.format`, maven, frontend, redirect, storage) and
  `docs/gitbook/`.

## Verification

- Targeted unit and integration tests through `heavy` with `--max-workers=2`. The existing Maven, frontend, redirect, S3
  and settings tests pass without edits.
- The container job's upgrade rehearsal against `dzikoysk/reposilite:3.5.28` passes unchanged.
- `openspec validate add-repository-format-spi --strict` passes and
  `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-repository-format-spi` prints nothing.
