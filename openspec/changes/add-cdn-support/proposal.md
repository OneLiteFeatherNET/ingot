# Proposal

## Why

Operators put Ingot behind a CDN or a company cache and then ask three questions: what the cache may store, how it learns
that an artifact changed, and whether the bytes of a large release can leave the instance. Today the answers are partly
wrong and partly missing.

- **A proxy with upstream credentials is answered as public.** The scope rule of `fix-private-cache-control` reads only local
  repositories and loopback references. A proxy whose mirror carries `authorization` (BASIC or CUSTOM_HEADER) reaches an
  upstream that refuses anonymous readers, yet its answers are `Cache-Control: public`, so a shared cache stores content that
  the upstream itself would not hand to an anonymous client.
- **Nothing tells a cache that an artifact changed.** A release can be deleted and deployed again in a repository without
  redeployment. A cache in front of Ingot keeps the old bytes until `max-age` runs out, and no hook exists to purge them.
- **Every download of an S3 repository passes through the instance.** The bytes are streamed from S3 with `getObject`, even
  for a release that a CDN would store anyway. Operators who want the bytes to leave the instance have no switch for it.
- **The CDN setup is not documented.** The cache-relevant values are listed in `web-routes.md`, and no guide says which
  values a CDN may store, how it must treat `Authorization`, or how to purge.

## What Changes

The fix groups come first. Each feature group is opt-in, and no default of an existing installation changes except where a
group is named as a fix.

### Fix groups

- **F1 Credentialed proxies answer private.** A file answer is `private, max-age=<maxAge>` when its repository reaches a
  remote host through a mirror that has `authorization`, directly or through a group. A proxy without credentials keeps
  `public`. `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true` restores `public` for these answers as well.
  Conventional Commits type and scope: `fix(maven)`. Pull request title:
  `fix(maven): mark credentialed proxy answers as private`.
  This reverses the rule of `fix-private-cache-control` that remote references never count, for credentialed mirrors only.
- **F2 Vary on compressed answers, verified first.** Ingot sets no `Vary` on file answers. The group first adds a test that
  asserts `Vary: Accept-Encoding` on a compressed answer. If the test already passes because the server adds the header, no
  code changes. It ships in the F1 pull request, under the same type and scope.

### Feature groups

- **S1 Presigned redirects for cacheable S3 downloads (opt-in).** With `INGOT_S3_PRESIGNED_REDIRECTS=true`, a plain `GET` of a
  cacheable file stored in S3 answers `302` with a presigned `Location`. Ranges, conditional requests, `HEAD`, metadata and
  non-cacheable answers are served by the instance as before.
  Conventional Commits type and scope: `feat(storage)`. Pull request title:
  `feat(storage): redirect cacheable s3 downloads to presigned urls`.
- **P1 Purge webhook (opt-in plugin).** A new plugin module, `reposilite-plugins/cdn-purge-plugin`, listens to the existing
  `DeployEvent` and `DeleteEvent` and sends the public URL of the file to a configured endpoint. It is not bundled in the
  image, so it runs only when the operator puts the jar in the plugins directory and sets `INGOT_CDN_PURGE_URL`.
  Conventional Commits type and scope: `feat(plugin)`. Pull request title:
  `feat(plugin): purge cdn urls on artifact deploy and delete`.
- **G1 CDN guide.** A how-to page, `docs/gitbook/how-to/put-a-cdn-in-front.md`, with the table of values that a cache may
  store, the rules for `Authorization`, a cache configuration recipe, purging and presigned redirects.
  Conventional Commits type and scope: `docs(gitbook)`. Pull request title:
  `docs(gitbook): add a guide for running ingot behind a cdn`.

Every new environment variable takes the `INGOT_` prefix and is read with the `REPOSILITE_` spelling as a fallback:
`INGOT_S3_PRESIGNED_REDIRECTS`, `INGOT_S3_PRESIGN_TTL_SECONDS`, `INGOT_S3_PRESIGN_ENDPOINT`, `INGOT_CDN_PURGE_URL`,
`INGOT_CDN_PURGE_BASE_URL` and `INGOT_CDN_PURGE_TOKEN`.

### Out of scope

- The `immutable` directive on release files. `maxAge` is one hour and cannot be changed by an environment variable, so the
  directive would add no protection, and it would keep a deleted and redeployed release in a cache for a year. See design
  decision D5.
- A configurable `s-maxage` and any TTL set by Ingot for shared caches. The CDN sets its own TTL.
- Purge through the native APIs of individual CDN providers. The webhook sends one JSON document, and an operator maps it to a
  provider with a small adapter.
- A retry queue for purge requests, and batching of the many events of a cleanup run.
- Bundling the purge plugin in the image, and any change to the Dockerfile.
- The directory listing headers (already on main in `fa62e15a6`), the ETag rule, `maven-metadata.xml` and `maxAge`.
- Any change to who may read a repository.

## Capabilities

### New Capabilities

- `cdn-cache-headers`: the `Cache-Control` value of a credentialed proxy and the `Vary` of compressed file answers.
- `s3-presigned-downloads`: the opt-in redirect of cacheable S3 downloads to presigned URLs.
- `cache-purge-webhook`: the opt-in plugin that tells a cache which URLs to purge on deploy and delete.
- `cdn-guide`: the operator guide for a CDN in front of Ingot.

## Impact

- **Breaking marker.** No item is BREAKING. No operator has to change a configuration key, a volume, a manifest or a plugin
  to keep today's behaviour, and each feature stays off until it is switched on. The title of no pull request carries a `!`.
- **Client-visible change by default (F1 only).** A shared cache in front of a credentialed proxy stops storing its answers.
  This is the same kind of change as `fix-private-cache-control`, and the upgrade notes say so. Maven and Gradle ignore the
  value. The escape hatch `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true` restores the old value. Open question Q1 asks the
  maintainer to confirm that this default change is intended.
- **Code.** `reposilite-backend` (maven, storage, S3 provider, configuration), the new module `reposilite-plugins/cdn-purge-plugin`
  registered in `settings.gradle.kts`, and `docs/gitbook/`.
- **Ordering.** F1 and F2 ship first. S1 and P1 are independent of each other. G1 ships last, because it describes the
  switches of F1, S1 and P1.
- **Plugin API.** The storage provider interface gets one method with a default implementation, so existing storage plugins
  compile unchanged. The gate of `gate-plugin-abi` is run in the S1 pull request.

## Verification

- Targeted tests per group through `heavy` with `--max-workers=2`. Integration tests run as
  `:reposilite-backend:integration --tests '<pattern>'`. No full suite is run.
- `openspec validate add-cdn-support --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-cdn-support` prints nothing.
