# Design

## Context

`StatusFacade.isAlive()` (`reposilite-backend/src/main/kotlin/com/reposilite/status/StatusFacade.kt:185`) is the only
health signal. It reads the web server state and nothing else, and `StatusEndpoints` serves it at `/api/status/health`.
The image health check and the docs use that route. The readiness endpoint needs a second, stricter answer that the
existing one can stay separate from.

Facts this design relies on, checked against origin/main `c0f01846`:

- Plugins are loaded and initialised in `ReposiliteFactory.kt:98-99`, before `Reposilite.launch()`. `launch()` emits
  `ReposilitePostInitializeEvent`, binds the web server and then emits `ReposiliteStartedEvent`
  (`Reposilite.kt`, the `launch` function).
- The database is `DatabaseConnection.databaseSource` (`configuration/local/infrastructure/DatabaseConnection.kt`), a
  `HikariDataSource`, reachable from `Reposilite.databaseConnection`.
- Repositories come from `MavenFacade.getRepositories()`, each with a `storageProvider`. A filesystem repository directory
  is created when the storage provider is built (`FileSystemStorageProviderFactory.kt:126`). `StorageProvider.getFiles`
  returns a `Result`, and for S3 it lists the prefix.
- The cluster bus attaches a `ClusterStatusSource` through `StatusFacade.attachClusterStatus` only when a bus URL is
  configured. Its `bus.state` is `connected`, `reconnecting` or `disabled`.

## Decisions

### D1. "Plugins loaded" is a flag set on `ReposiliteStartedEvent`

The status plugin keeps a flag that is false until it receives `ReposiliteStartedEvent`, and it is set to false again on
`ReposiliteDisposeEvent`. At that point every plugin has initialised, the web server is bound and the migrations of the
database plugins have run. Setting the flag earlier, at `ReposilitePostInitializeEvent`, would report ready before the
web server accepts connections. Since the endpoint is served by that web server, the difference is small, but the later
event is the one the plugin API documents as "the server started".

### D2. Each check is a function returning a boolean, run with a bound

Each check runs on its own thread from a small pool and is awaited with a timeout of two seconds. A timeout, an exception
or a `false` answer makes that check fail. The probe therefore answers within about two seconds, even when a storage
backend or the database hangs. The timeout is a constructor parameter, so tests use a short one without sleeping: they
pass a check that blocks on a latch that the test releases, or never releases.

The database check takes a connection and calls `isValid(2)`. The storage check calls `getFiles` on the root location of
each repository and counts an `Err` as a failure. The cluster check reads `bus.state` through `ClusterStatusSource`.

### D3. A storage root that is empty answers as ready

`getFiles` answers `Ok` with an empty list for an empty directory or an empty S3 prefix. The check treats `Ok` as answering
and only an error or a timeout as failing. Using `exists` would fail a new S3 repository, because a prefix has no object
of its own.

### D4. The cluster is ready only when connected, and it counts only when configured

With a bus URL, readiness requires `connected`. `reconnecting` fails the check, because the replica cannot receive cache
invalidations while it is down. `disabled` also fails, because a bus URL is configured and the transport is not usable
(missing transport or refused configuration). An operator who sets a bus URL asked for a working bus. Without a URL the
check is skipped, so single-instance installations stay ready.

### D5. No caching of results, and what that costs

Each request runs the checks again. Kubernetes and most load balancers probe every few seconds per replica, so the load is
one connection validation, one listing per repository and one local read. S3 repositories each cost one list request per
probe. A cache would make an unready replica look ready for its lifetime, which is the failure this endpoint exists to
prevent, so no cache is added. If the S3 cost matters to an operator, the probe interval is the lever, and that is
documented.

### D6. The body names checks, never repositories or exception text

The endpoint needs no credentials. A repository name, a bucket name or an exception message would leak internal detail to
any caller, which the `harden-http-security` change forbids for error bodies. The failing list holds the four fixed check
names. The detail goes to the log at `WARN` with the repository name and the exception, so an operator reads it there.

### D7. Known trade-off: a saturated pool reports not ready

The database check takes a connection from the same pool that serves requests. Under heavy load the pool can be busy for
longer than two seconds, and the replica then answers `503` until the load falls. That is intended for a load balancer,
because a replica that cannot take a connection cannot serve the request either. It is recorded here because it removes
a replica from rotation under load, and the readiness probe period and failure threshold of the deployment decide how
quickly that happens. The Helm chart choice is out of scope.

## Risks

- The two-second bound makes a slow but working S3 endpoint look failing. The log names the check, so an operator can
  raise the bound in a later change if it happens.
- A replica that is ready for one probe and not the next toggles in and out of rotation. This is the expected behaviour
  of a readiness signal and is documented in the monitoring page.
