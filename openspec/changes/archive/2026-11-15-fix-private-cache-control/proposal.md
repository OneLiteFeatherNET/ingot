# Proposal

## Why

Every file that Ingot serves from a repository is answered with `Cache-Control: public, max-age=...`, whatever the visibility
of the repository is. The header is written in `attachmentHeaders` (`reposilite-backend/src/main/kotlin/com/reposilite/shared/extensions/JavalinExtensions.kt:141`),
and the flag that selects it comes from `Repository.acceptsCachingOf` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/Repository.kt:72-73`).
That method looks at redeployment and at `maven-metadata.xml` and never at visibility.

`public` tells a shared cache, such as a CDN or a company proxy, that it may store the answer even when the request carried
credentials (RFC 9111, section 3.5). So an artifact from a PRIVATE repository, downloaded once by an authorized build, can be
handed out later to anyone who asks that cache for the same URL. A group shows the same gap from the other side. A PUBLIC
group with a PRIVATE member answers an anonymous request with the member's file, when the group's own mirror identity can
read it, and labels that answer `public` because the group is public.

Today an operator who runs such a cache in front of a private repository has no setting that prevents this.

## What Changes

- **C1 Scope follows visibility.** A file answer is `private, max-age=<maxAge>` when its repository is HIDDEN or PRIVATE, or when
  it is a group or mixed repository that reaches a non-PUBLIC local repository through a loopback reference, directly or
  through another such group. A PUBLIC repository whose local members are all PUBLIC keeps `public, max-age=<maxAge>`. Remote
  references do not count, because this instance cannot judge the access rules of an upstream host.
  HIDDEN is anonymously readable by its exact path, so this is the stricter choice for it. The cost is that a shared cache no
  longer stores HIDDEN answers. Answers that are not cacheable stay `no-cache, no-store, max-age=0`. Status codes, bodies,
  `ETag` and `Last-Modified` do not change, and `206` and `304` answers follow the same scope as `200`.
- **C2 Escape hatch.** `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC` accepts `true` or `false`, and `REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC`
  is read as the fallback. `true` restores `public` for the answers that C1 makes private. The default is `false`. Any other
  value stops the start-up and names the variable.
- **C3 Documentation.** The Cache-Control rows of `docs/gitbook/reference/web-routes.md` and `docs/gitbook/reference/repository-settings.md`,
  the new variable in `docs/gitbook/reference/environment-variables.md`, and a note in `docs/gitbook/reference/upgrade-changes.md`.

### Out of scope

- The `Vary` header and `no-cache` on error answers (roadmap Q4), and the cluster-wide S3 HEAD cache (Q5).
- The ETag rule in `Repository.acceptsCachingOf` and `MavenFacade.findEntityTag`. Only the Cache-Control value changes.
- `Cache-Control` on directory listings, the search API and the dashboard. They keep their current values.
- The visibility of remote upstream hosts, and any change to who may read what. Access rules stay as they are.
- A per-request, authorization-aware cache policy, and any change to `maxAge`.

## Capabilities

### New Capabilities

- `repository-cache-control`: which `Cache-Control` value a repository file answer carries, how groups inherit the scope of
  their members, and the escape hatch.

## Impact

- **Type and scope.** The change ships under `fix(maven)`. The commits use `test(maven)`, `fix(maven)` and `docs(maven)`, one type
  each. The pull request title, squash-merged onto main, is:

  `fix(maven): send private cache-control for access-controlled repository content`

- **Breaking marker.** Nothing is BREAKING for operators. No configuration key, volume, manifest or plugin has to change, and the
  status codes and bodies stay the same. The title carries no `!`.
- **Client-visible change by default.** For access-controlled content, the `Cache-Control` value changes from `public` to
  `private`. Maven, Gradle and the dashboard ignore it, so their behaviour does not change. A shared cache in front of a PRIVATE
  or HIDDEN repository, or of a group with a private member, stops storing those answers. That is the intended fix, and the
  upgrade notes say so. `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true` restores the old values for an operator who accepts the risk.
- **Code.** `reposilite-backend` (maven, shared extensions, configuration) and `docs/gitbook/`.
- **Ordering.** `attachmentHeaders` already carries the range and validator headers of `add-problem-details` (merged, task
  group A3). This change keeps them and only changes the `Cache-Control` value.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`, and integration tests through
  `:reposilite-backend:integration --tests`.
- `openspec validate fix-private-cache-control --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-private-cache-control` prints nothing.
