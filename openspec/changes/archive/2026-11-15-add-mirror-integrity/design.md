# Design

## Context

Verified against origin/main:

- `MirrorService.putMirroredFile` (`MirrorService.kt:186-197`) passes the upstream stream to `storageProvider.putFile`
  without a digest. `storeFile` and `fetchAndStoreFromRemoteHosts` both go through it.
- `searchInRemoteRepositories` (`MirrorService.kt:207-245`) moves on to the next eligible host after any non-404 error, and
  answers `Failed` only when every host failed. A refusal therefore reads like an upstream failure to the caller.
- `ResolutionProvider.tryLocalFirst` (`ResolutionProvider.kt:96-99`) answers a 5xx or 429 from the upstream as an error.
  `tryRemoteFirst` (`ResolutionProvider.kt:112-114`) falls back to the stored copy on any failure.
- The mirror client is `HttpRemoteClient`, built with `NetHttpTransport.Builder().setProxy(proxy)` (`HttpRemoteClient.kt:83-86`).
  `RepositoryFactory.createMirroredHostConfiguration` passes `createHttpProxy(httpProxy)` or `null` (`RepositoryFactory.kt:84-88`).
- `docs/gitbook/how-to/create-proxy-repositories.md` documents `httpProxy` as the way to reach a remote through another proxy.

## Decisions

### D1: the digest comes from the same host, SHA-256 first

The sibling `<artifact>.sha256` is requested from the host that served the artifact. `.sha1` is the fallback. MD5 and SHA-512
are not checked, to keep the request count and the code small. Maven Resolver's checksum policy works the same way.

### D2: verify before commit, and only under `fail`

Under `fail` the digest is computed while the stream is written, and the copy becomes visible only when it matches. This
needs an atomic commit in storage, which is the subject of `fix-atomic-filesystem-upload`. Under `warn` the copy is stored
first and a mismatch is logged, so `warn` never changes what is served. The policy needs `store` enabled. Without it the
body is streamed to the client and cannot be checked, so the combination is refused at validation.

### D3: no `serveStaleOnError`

The roadmap asked for `serveStaleOnError`, default `false`. The behaviour it describes is already on main for metadata
(`ResolutionProvider.kt:112-114`), and it is on by default. Stored artifacts are not revalidated, so no stale copy is ever
held back. A default of `false` would stop that fallback and change what clients see on an upstream outage, which the
drop-in rule forbids. The item is therefore not built. The how-to documents what happens today.

### D4: the egress proxy is decided by a characterisation test

The roadmap said to verify first. The first code task runs a loopback fake proxy against the mirror client with no
`httpProxy` and with `HTTPS_PROXY` set, and records whether the request reaches the proxy. The expected result is that
the standard variables are ignored, and that only the JVM system properties (`https.proxyHost`, `https.nonProxyHosts`) work.
If the variables are ignored, the opt-in `INGOT_HTTP_USE_ENV_PROXY` is built, default off, and the per-mirror `httpProxy`
keeps priority. If they are honoured already, the change only documents it.

Proxy URLs that carry credentials are refused. The per-mirror path sets the JVM-wide default `Authenticator`, which is
process-wide, so the environment path must not do the same silently.
