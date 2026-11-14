# Tasks

Each group is one or more commits, one Conventional Commits type per commit, and each group ends with the task that opens its
pull request. The groups are separate pull requests, each with one title. Every test follows F.I.R.S.T.: no sleeps or waits by
time, no system clock (inject `java.time.Clock`, or assert values that do not depend on the signing time), no fixed ports (the
in-process stub server takes the port the OS assigns), no shared mutable state, a fresh fixture per test, and assertions with
messages. Environment values are read from a map argument, so no test depends on the real environment. Gradle runs go through
`heavy`, with `--max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose add-cdn-support: this change and nothing else. Verify:
  `openspec validate add-cdn-support --strict` passes and the dash grep from the proposal prints nothing.

## 2. Fix group F1 and F2: credentialed proxies and Vary (fix(maven))

- [ ] 2.1 `test(maven)`: write failing unit tests in the scope rule tests, with repositories built in memory: a PUBLIC repository
  with a remote mirror that has `authorization` (BASIC) resolves to PRIVATE; the same remote without `authorization` resolves to
  PUBLIC; a PUBLIC group that reaches a PUBLIC proxy with credentials resolves to PRIVATE; a remote with CUSTOM_HEADER
  credentials resolves to PRIVATE; the escape hatch `true` still resolves to PUBLIC. Verify: the credentialed cases fail on the
  current code, and the unauthenticated case passes.
- [ ] 2.2 `test(maven)`: write failing integration tests next to `MavenCacheControlIntegrationTest`, with an in-process stub
  upstream on a port the OS assigns, which answers `401` without basic credentials: an anonymous GET through a PUBLIC proxy with
  credentials answers `private, max-age=<maxAge>`; the same proxy without credentials answers `public, max-age=<maxAge>`; a
  compressed answer carries `Vary` with `Accept-Encoding`. Verify: the first assertion fails with `public` on the current code.
  Record the result of the `Vary` assertion, because it decides whether task 2.3 changes the `Vary` header.
- [ ] 2.3 `fix(maven)`: add a remote-credentials flag to the scope node and use it in the scope resolution, so that a node with
  the flag resolves to PRIVATE. Add `Vary: Accept-Encoding` to file answers only if task 2.2 shows it missing. Leave
  `Repository.acceptsCachingOf`, the ETag rule and the escape hatch reader as they are. Verify: 2.1 and 2.2 pass, and the existing
  maven unit and integration tests pass with no assertion changed.
- [ ] 2.4 `docs(gitbook)`: in `docs/gitbook/reference/web-routes.md`, change the `Cache-Control` row so that it says a credentialed
  mirror makes the answer `private`, and drop "remote references do not count" for credentialed mirrors only; in
  `docs/gitbook/reference/repository-settings.md`, extend the Visibility paragraph; in `docs/gitbook/reference/environment-variables.md`,
  note that the escape hatch also covers credentialed mirrors; in `docs/gitbook/reference/upgrade-changes.md`, add the note that a
  shared cache no longer stores answers of credentialed proxies, and how to restore the old value. Verify: the values on these
  pages match the scenarios in `specs/cdn-cache-headers`.
- [ ] 2.5 Open the code pull request: `fix(maven): mark credentialed proxy answers as private`. Branch from the remote main branch
  that already contains the merged proposal, with the groups of tasks 2.1 to 2.4 and no file under `openspec/`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections, telling the story of an operator whose upstream asks for a password
  and whose files a shared cache kept. No class names, no session link, no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and each commit, and the body has no
  em or en dash.

## 3. Feature group S1: presigned S3 downloads (feat(storage))

- [ ] 3.1 `test(storage)`: write failing unit tests for the switch reader (`INGOT_` over `REPOSILITE_`, empty and unset mean
  `false`, a value other than `true` or `false` in any case refuses the start and names the variable), for the TTL reader (default
  300, 60 to 3600 accepted, anything else refuses the start), and for the redirect decision as a pure function: a cacheable GET
  without range or conditional headers in an S3 repository with the switch on redirects; a `Range` request, an `If-None-Match`
  request, a `HEAD`, a non-cacheable file and a switch that is off each do not; the `Cache-Control` of the redirect has
  `max-age` equal to the smaller of `maxAge` and half the TTL. The presigned URL test asserts the bucket, the key, the endpoint
  and `X-Amz-Expires`, and never the signing date. Verify: they fail on the missing symbols.
- [ ] 3.2 `test(storage)`: write failing integration tests with the S3 setup that the existing storage integration tests use: with
  the switch on, an authorized GET of a cacheable file answers `302` with a `Location` that names the bucket and the key; the same
  GET with a `Range` header answers `206` from the instance; an anonymous GET of a private file answers `401` and no `Location`.
  The test runs through `heavy`, because it starts the container the existing S3 tests start. Verify: the redirect assertion fails
  on the current code.
- [ ] 3.3 `feat(storage)`: add `presignedGetUrl(location, ttl): String? = null` to the storage provider interface, implement it in
  `S3StorageProvider` with the S3 presigner that uses the same credentials, path style and V4 settings as the client, and add the
  endpoint override from `INGOT_S3_PRESIGN_ENDPOINT`. Read the switch and the TTL once at start. In `MavenEndpoints.serveDocument`, redirect
  only the cases of D3, after the read check. Verify: 3.1 and 3.2 pass, the existing storage tests pass, and `gate-plugin-abi`
  passes with the new default method.
- [ ] 3.4 `docs(gitbook)`: add the rows of `INGOT_S3_PRESIGNED_REDIRECTS`, `INGOT_S3_PRESIGN_TTL_SECONDS` and
  `INGOT_S3_PRESIGN_ENDPOINT` (each with its `REPOSILITE_` fallback and its default) to
  `docs/gitbook/reference/environment-variables.md`; add a section "Presigned redirects" to
  `docs/gitbook/how-to/use-s3-storage.md` that tells what the `302` carries and when the instance serves the bytes itself; add
  one line to `docs/gitbook/reference/storage-providers.md`. Verify: the defaults on the pages match the code.
- [ ] 3.5 Open the code pull request: `feat(storage): redirect cacheable s3 downloads to presigned urls`. Branch from the remote
  main branch, with the groups of tasks 3.1 to 3.4 and no file under `openspec/`. Fill the template with the reason as an
  operator sees it: the instance spends its bandwidth on bytes that a bucket can serve. Verify: the title, the commit-style hook
  and the absence of dashes, as in 2.5.

## 4. Feature group P1: purge webhook (feat(plugin))

- [ ] 4.1 `test(plugin)`: write failing unit tests for the new module `reposilite-plugins/cdn-purge-plugin`, with an injected sender
  and no network: the public URL is the base URL joined with the repository and the path, with each segment percent-encoded and no
  doubled slash; the JSON body is `{"urls": [...]}`; a deploy event sends one request, and so does a delete event; the bearer token
  header is present only when the token is set; an empty `INGOT_CDN_PURGE_URL` sends nothing; a set URL without a base URL refuses
  the start and the message names `INGOT_CDN_PURGE_BASE_URL`; a sender that throws does not reach the caller. Verify: they fail on the
  missing module.
- [ ] 4.2 `feat(plugin)`: add the module, register it in `settings.gradle.kts`, add the listeners for `DeployEvent` and
  `DeleteEvent`, and send the request with `java.net.http.HttpClient` and a 5 second timeout. Catch every failure and log it. Verify:
  4.1 passes, and `heavy ./gradlew :reposilite-plugins:cdn-purge-plugin:shadowJar --no-daemon --max-workers=2` builds the jar.
- [ ] 4.3 `docs(gitbook)`: add the rows of `INGOT_CDN_PURGE_URL`, `INGOT_CDN_PURGE_BASE_URL` and `INGOT_CDN_PURGE_TOKEN` (each with its
  `REPOSILITE_` fallback, the default and the secret marking) to `docs/gitbook/reference/environment-variables.md`. Verify: the
  defaults on the page match the code.
- [ ] 4.4 Open the code pull request: `feat(plugin): purge cdn urls on artifact deploy and delete`. Branch from the remote main
  branch, with the groups of tasks 4.1 to 4.3 and no file under `openspec/`, and no change to the Dockerfile. Verify: the title, the
  commit-style hook and the absence of dashes, as in 2.5.

## 5. Guide group G1 (docs(gitbook))

- [ ] 5.1 `docs(gitbook)`: write `docs/gitbook/how-to/put-a-cdn-in-front.md` with the sections of D6 (the values a cache may store,
  `Authorization` and cookies, the CDN configuration, TTLs, purging with the plugin, presigned redirects, Maven and Gradle), add it
  to `docs/gitbook/SUMMARY.md`, and link it from `docs/gitbook/how-to/run-behind-reverse-proxy.md`. Verify: every variable named in
  the guide appears in `docs/gitbook/reference/environment-variables.md`, and the value table matches `web-routes.md`, both checked
  with a grep in the pull request.
- [ ] 5.2 Open the code pull request: `docs(gitbook): add a guide for running ingot behind a cdn`. Branch from the remote main branch
  after the pull requests of groups 2 to 4 are merged, with no file under `openspec/`. Verify: the title, the commit-style hook and
  the absence of dashes, as in 2.5.
