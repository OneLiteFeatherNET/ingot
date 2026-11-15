# Proposal

## Why

A proxy repository that stores what it fetches keeps the first copy it receives, and nothing checks that copy.
`putMirroredFile` in `MirrorService.kt` (lines 186 to 197) writes the upstream bytes straight into storage. If an upstream
serves a damaged or replaced artifact once, Ingot keeps it and serves it to every build after that. The checksum file that
the build client compares against comes from the same upstream and is cached the same way, so the client agrees with the
bad copy and nothing fails.

Operators in clusters meet the second gap. Mirror traffic usually leaves through an egress proxy that is configured with
`HTTPS_PROXY`, `HTTP_PROXY` and `NO_PROXY`. Ingot takes a proxy only from the per-mirror `httpProxy` setting: the mirror
client gets that proxy or `null` (`RepositoryFactory.kt:84-88`, `HttpRemoteClient.kt:83-86`), so the standard variables
are not read by Ingot itself. Whether the JDK picks them up anyway is an open verification, and the first task of the code
change settles it. The JVM system properties in `JAVA_OPTS` may already work when `httpProxy` is empty, but no document says so.

The roadmap also proposed `serveStaleOnError`. On main the behaviour already exists for metadata: when the upstream fails on
a metadata lookup that prefers upstream, `ResolutionProvider.tryRemoteFirst` answers with the stored copy
(`ResolutionProvider.kt:112-114`). Stored artifacts are never revalidated, so there is nothing to serve stale. A setting with
the default `false` would remove a fallback that clients rely on today, so this change does not add it (design.md, D3).

## What Changes

- **M1 Checksum policy.** New per-mirror setting `checksumPolicy` with the values `ignore`, `warn` and `fail`, default
  `ignore`. It applies to artifact files that a mirror with `store` enabled fetches. Metadata and checksum files are not
  checked. With `warn`, a mismatch or a missing checksum is logged and the copy is stored, as today. With `fail`, the copy is
  verified before it is committed to storage: a mismatch or a missing checksum refuses that host, the next host is tried,
  and a copy that fails is never stored or served. The digest is SHA-256 from the upstream `.sha256` file, with `.sha1` as
  the fallback. `warn` and `fail` are refused when `store` is off, because nothing can be verified without keeping the copy.
- **M2 Egress proxy.** New switch `INGOT_HTTP_USE_ENV_PROXY`, read with `REPOSILITE_HTTP_USE_ENV_PROXY` as fallback, default
  `false`. When it is on and a mirror has no `httpProxy`, the mirror client takes its proxy from `HTTPS_PROXY` for https
  and `HTTP_PROXY` for http, and goes direct for hosts listed in `NO_PROXY`. This part is built only when the first task of
  the code change shows that the standard variables are not read today. Otherwise it is dropped, and the change only
  documents the system property route.
- **Docs.** `docs/gitbook/how-to/create-proxy-repositories.md` (the `checksumPolicy` key and a section on checksums),
  `docs/gitbook/reference/environment-variables.md` (the switch), and `docs/gitbook/how-to/run-on-kubernetes.md` (the
  egress and NetworkPolicy pairing).

### Out of scope

- `serveStaleOnError` and any change to when a stored copy is revalidated.
- The edge-cache how-to. It belongs to `docs-cloud-native-guides` (N18).
- Verifying signatures (`.asc`) and metadata files. The download gate and signature checks are Strategic (S5).
- Backfill: copies stored before the policy is set are not checked.
- Proxy settings for other outbound calls (S3, OIDC, update check). The switch covers mirror clients only.
- Changing the default of any existing setting.

## Capabilities

### New Capabilities

- `mirror-integrity`: the checksum policy of a mirror and the verified commit of a mirrored artifact.
- `mirror-egress-proxy`: proxy selection for mirror clients from the environment.

## Impact

- **Type and scope.** The change ships under `feat(maven)`. Mirror code lives in `reposilite-backend/src/main/kotlin/com/reposilite/maven/`.
  The pull request title, squash-merged onto main, is:

  `feat(maven): verify mirrored artifacts against upstream checksums`

- **Breaking marker.** None. The policy defaults to `ignore` and the switch defaults to off. No configuration key, environment
  variable, volume, manifest or plugin has to change, and the existing mirror tests pass unedited. The title carries no `!`
  and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None.
- **Dependencies.** `fix-atomic-filesystem-upload` (F7, wave A) must be merged first, because `fail` needs the storage commit
  to be atomic so that a bad copy never becomes visible. `add-overload-protection` (N15, wave D) also changes the single-flight
  code in `MirrorService.kt`; merge it first or rebase onto it. `add-mirror-integrity` has no dependency on `add-read-only-mode`.
- **Code.** `reposilite-backend` (maven, maven application settings, shared http) and `docs/gitbook/`.

## Verification

- The first code task is a characterisation test that proves or disproves the egress gap before any production code changes.
- Targeted tests per commit through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`. Integration tests run as
  `:reposilite-backend:integration --tests '<pattern>'`.
- `openspec validate add-mirror-integrity --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-mirror-integrity`
  prints nothing.
