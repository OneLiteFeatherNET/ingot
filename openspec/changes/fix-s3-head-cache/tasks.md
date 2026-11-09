# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (the cache's time source is an injected Caffeine `Ticker` that the test advances by hand), no shared mutable state, a
temporary directory per test where a file is needed, and assertions with messages. Gradle runs go through `heavy`, with
`--max-workers=2`, targeted and never the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-s3-head-cache`: this change and nothing else. Verify:
  `openspec validate fix-s3-head-cache --strict` passes and the dash grep from the proposal prints nothing.

## 2. Q5 Cluster-coherent HEAD cache

- [ ] 2.1 `test(storage)`: write failing tests in `CachableS3ClientTest`: a HEAD for `maven-metadata.xml`,
  `maven-metadata-local.xml` and `maven-metadata.xml.sha1` is sent to S3 on every request; a HEAD for a key with a
  `1.0-SNAPSHOT` directory is sent every time; a release jar is still cached; with a bus bound, an answer stored at 0 is not
  used at 301 seconds and a read at 200 seconds does not renew it; without a bus, the answer is still used at 301 seconds.
  Every time is a fake `Ticker`. Verify: the new tests fail before 2.2. The four existing tests pass unedited.
- [ ] 2.2 `fix(storage)`: skip the cache for mutable keys in `CachableS3Client.headObject`, and cap the cache expiry at
  300 seconds once a cluster bus is bound, using a variable expiry that reads the cap. Bind it through the cluster hook that
  already binds the resolution cache (`FacadeCacheHooks.clusterBusOpened`), and keep the binding free of static state. The
  default stays off. Verify: the tests of 2.1 pass, and so do the existing `CachableS3Client` and `S3StorageProvider`
  tests in `heavy`.
- [ ] 2.3 `docs(storage)`: update `docs/gitbook/reference/storage-providers.md` (the `metadataCacheSettings` section),
  `docs/gitbook/how-to/use-s3-storage.md` (the metadata cache row, which says other instances do not see the cache) and
  `docs/gitbook/reference/repository-settings.md`: mutable metadata is never cached, and with a cluster bus an entry lives at
  most 300 seconds. Verify: the three pages agree on the same rule.

## 3. N12 S3 retry jitter

- [ ] 3.1 `test(storage)`: write failing tests that the S3 client's backoff strategy is the SDK's `FullJitterBackoffStrategy`
  (read through an internal function that the factory uses), and that for retry numbers 0 to 4 and for numbers 6 and above the
  delay lies between zero and the smaller of 2 to the power of the retry number seconds and 60 seconds. The checks run over a
  fixed number of samples; they assert bounds, which hold on every draw, not exact values. Verify: the type assertion fails
  before 3.2.
- [ ] 3.2 `fix(storage)`: use the SDK's `FullJitterBackoffStrategy` with a base delay of 1 second and a maximum backoff of
  60 seconds in `S3StorageProviderFactory`, keep five retries, and delete `ExponentialBackoffStrategy.kt`. Check in the
  2.46.3 SDK source that the ceiling is the smaller of the cap and base times 2 to the power of the retry number. Verify: the
  tests of 3.1 pass.
- [ ] 3.3 `docs(storage)`: change the Retries row in `docs/gitbook/reference/storage-providers.md` from exponential backoff
  to full jitter, with the same 1 second and 1 minute limits.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `fix(storage): skip caching mutable S3 metadata and add retry jitter`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the operator's story (the cluster
  that shows an old file size after a deploy, the retries that arrive together) without class names. It carries no session
  link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the
  title and every commit, and the body has no em or en dash.
