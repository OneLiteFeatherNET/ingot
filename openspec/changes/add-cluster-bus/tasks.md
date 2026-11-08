# Tasks

Each group is one or more commits of one Conventional Commits type. Tests land before the code they test, and each group
carries the documentation its own settings need. The pull request is squash-merged, so its title is the commit on main.

## 1. Prerequisite (already on main, no commit)

- [ ] 1.1 Verify on origin/main that `AuthenticationFacade` bounds cached credentials to one minute after they were stored (`expireAfterWrite`, `CREDENTIAL_CACHE_MAX_AGE`), and that `AuthenticationCacheTest` "should expire a cached credential one bound after it was cached even while it is used continuously" passes. Verify: the test is green before 2.1 starts. If it is not, stop and raise a `fix(auth)` change first.

## 2. Core event contract

- [ ] 2.1 `test(cluster)`: write failing tests that a token state change, a resolution invalidation and a session family revocation each emit exactly one event after the write, and that the existing `onTokenChanged` listeners still fire. Verify: the tests compile and fail because the event classes do not exist yet.
- [ ] 2.2 `feat(cluster)`: add the experimental event classes `ResolutionInvalidatedEvent`, `TokenStateChangedEvent`, `SessionFamilyRevokedEvent`, `SharedConfigurationChangedEvent` and `StorageUsageRefreshedEvent` in the plugin API package, and emit them from the mutation sites. Verify: the tests from 2.1 pass and the existing token and identity test suites still pass.
- [ ] 2.3 `refactor(auth)`: route the existing token listeners through the new event with unchanged behaviour. Verify: the authentication test suite passes with no test renamed or removed.

## 3. Plugin skeleton, configuration and inert default

- [ ] 3.1 `test(cluster)`: write failing tests that, with no bus URL in any source, no thread starts, no transport factory is invoked and no event is published, using a fake factory injected into the plugin. Verify: the tests fail on the missing plugin, then pass after 3.3.
- [ ] 3.2 `test(cluster)`: write failing tests for source precedence (system property, then `INGOT_CLUSTER_URL`, then `REPOSILITE_CLUSTER_URL`), refusal of an unknown scheme without echoing the URL, credential redaction, the password variable fallback, and the credentials file source (`ingot.cluster.credentials`, then `INGOT_CLUSTER_CREDENTIALS`) with its contents never logged. Verify: the tests fail before 3.3.
- [ ] 3.3 `feat(cluster)`: add the `cluster` plugin in `net.onelitefeather.ingot.cluster`, the configuration reader, URL parsing with redaction, and creation of the cluster identifier through `ConfigurationFacade.findOrCreateConfiguration`. Register the plugin in `META-INF/services/com.reposilite.plugin.api.ReposilitePlugin`. Verify: 3.1 and 3.2 pass, and the existing plugin-loading tests pass.
- [ ] 3.4 `docs(gitbook)`: create `docs/gitbook/how-to/configure-cluster-bus.md` with the URL sources, the password variable, the instance identifier variable and the inert default; add it to `docs/gitbook/SUMMARY.md`; link it from `docs/gitbook/how-to/run-on-kubernetes.md` and `docs/gitbook/explanation/multi-replica-operation.md`; add the new variables to `docs/gitbook/reference/environment-variables.md`. Verify: every variable from design section 13 appears in the new page and in the reference, and both linked pages point at the new page.

## 4. Envelope and in-memory transport

- [ ] 4.1 `test(cluster)`: write failing tests for the envelope round trip, the ignored and counted unknown versions and types, the ignored foreign cluster identifier and own origin, and the absence of secret-shaped fields in the payload of every event type. Verify: the tests fail before 4.3.
- [ ] 4.2 `test(cluster)`: write a failing test that two plugin instances connected by an in-memory transport propagate a token change, and that applying a received event publishes nothing. Verify: the test fails before 4.3 and uses a latch with a bounded timeout, not a sleep.
- [ ] 4.3 `feat(cluster)`: add the envelope codec, the transport interface and the in-memory transport in test sources only. Verify: 4.1 and 4.2 pass.

## 5. Outbound queue and reconnect policy

- [ ] 5.1 `test(cluster)`: write failing tests for the backoff sequence (one second, doubling, capped at sixty seconds, jitter injected), for the bounded outbound queue that drops the oldest entry and counts it, and for a publish that never blocks while the queue is full. Verify: the tests fail before 5.2.
- [ ] 5.2 `feat(cluster)`: add the backoff policy as a pure function of the attempt number, the bounded queue with its drain thread, and the single log line per state transition. Verify: 5.1 passes.

## 6. Event handlers

- [ ] 6.1 `test(cluster)`: write a failing test that a received `token.changed` drops the cached credentials of that token name, publishes nothing, and makes the next request for that credential check the database. Verify: the test fails before 6.6.
- [ ] 6.2 `test(cluster)`: write failing tests that a received `resolution.invalidated` drops the matching pin, and that resolution entries expire after `resolutionCacheClusterMaxAgeSeconds` only when a bus is configured, with a default of 300 when the stored value is 0. Verify: the tests fail before 6.6.
- [ ] 6.3 `test(cluster)`: write failing tests that a received `config.changed` triggers a poll at once, and that without the event the scheduled poll still reloads the configuration, driven by the injected scheduler. Verify: the tests fail before 6.6.
- [ ] 6.4 `test(cluster)`: write failing tests that a received `session.revoked` makes the session token of that family refused, that the denylist entry ends with the session expiry, and that without the event the token is refused at its expiry. Verify: the tests fail before 6.6.
- [ ] 6.5 `test(cluster)`: write failing tests that a received `storage.usage` replaces the figures, and that the previous figures are kept until it arrives. Verify: the tests fail before 6.6.
- [ ] 6.6 `feat(cluster)`: add the handlers for 6.1 to 6.5. A received event calls the internal invalidation functions directly, never the emitting facade methods. Add the resolution maximum age setting `resolutionCacheClusterMaxAgeSeconds` to `MavenSettings` beside `resolutionCacheMaxEntries`, and the in-memory session denylist. Verify: 6.1 to 6.5 pass, and the echo test from 4.2 still passes.
- [ ] 6.7 `test(cluster)`: write a failing test that failure aggregation is off unless `INGOT_CLUSTER_FAILURES` is set, and that with the bus and the switch on a recorded failure appears on the other instance. Verify: the test fails before 6.8.
- [ ] 6.8 `feat(cluster)`: add failure aggregation behind the switch, merged in memory on read. Verify: 6.7 passes.
- [ ] 6.9 `docs(gitbook)`: document `resolutionCacheClusterMaxAgeSeconds` in the maven rows of `docs/gitbook/reference/settings-domains.md`, next to `resolutionCacheMaxEntries`, and in the resolution cache section of `docs/gitbook/explanation/storage-and-mirror-caching.md`; document the failure switch in `docs/gitbook/how-to/configure-cluster-bus.md`. Verify: `grep` finds `resolutionCacheClusterMaxAgeSeconds` and `INGOT_CLUSTER_FAILURES` in the named pages.

## 7. Console fan-out

- [ ] 7.1 `test(cluster)`: write failing tests that a local line is published once, that a received line reaches local subscribers labelled with its origin and is not written to the local log (asserted through a captured appender), that only lines at the producing instance's visibility threshold are published, that the switch off publishes nothing, and that a logging call never blocks while the bus is slow (bounded latch). Verify: the tests fail before 7.2.
- [ ] 7.2 `feat(cluster)`: add the remote delivery entry point to the journalist, the console fan-out in the plugin with its bounded outbound queue, and the `INGOT_CLUSTER_CONSOLE` switch, default on with a bus. Verify: 7.1 passes.
- [ ] 7.3 `docs(gitbook)`: document the console fan-out switch and what the bus readers can see in `docs/gitbook/how-to/configure-cluster-bus.md`, including the privacy note. Verify: `grep` finds `INGOT_CLUSTER_CONSOLE` and the privacy note on that page.

## 8. Single runner (already on main, verification only)

- [ ] 8.1 Verify on origin/main, with no commit: `CleanupSchedulerClaimTest`, `AuditPurgeClaimTest` and `SqlScheduledJobClaimsTest` pass, the keys are `cleanup:<policy>:<UTC date>` and `audit-purge:<UTC date>`, and no code in this change calls the bus for a claim. Verify: the three test classes are green. If one fails, stop and raise a `fix(scheduling)` change first.

## 9. Transports

- [ ] 9.1 `test(cluster)`: write failing integration tests against a NATS container from the existing `SharedContainers` object (new lazy `nats` container) for a publish and receive round trip within a bounded latch, reconnection after the broker restarts, an instance that starts while the broker is down, serves requests and connects later, and a broker in operator mode (JWT auth) that the instance joins with a `.creds` file. Verify: the tests fail before 9.3.
- [ ] 9.2 `test(cluster)`: write failing integration tests against a Valkey container (new lazy `valkey` container in `SharedContainers`) with the same three behaviours, plus a check that a `redis://` URL and a `valkey://` URL to the same host use the same transport. Verify: the tests fail before 9.3.
- [ ] 9.3 `feat(cluster)`: add the NATS transport on the NATS client library, and the Valkey and Redis transport on the Jedis client with one dedicated subscription thread, behind the scheme factories. Client versions are pinned at least two weeks old. Verify: 9.1 and 9.2 pass.
- [ ] 9.4 `docs(gitbook)`: document the accepted schemes, TLS, the Valkey-first wording and the licensing note in `docs/gitbook/how-to/configure-cluster-bus.md`. Verify: `grep` finds each accepted scheme and the licensing note, and no em dash or en dash appears in the page.

## 10. Status

- [ ] 10.1 `test(cluster)`: write a failing test that the status output reports the state, the last received event time and the three counts, with credentials removed from the URL, and that no secret appears in the status JSON. Verify: the test fails before 10.2.
- [ ] 10.2 `feat(cluster)`: add the cluster section to the existing status output. Verify: 10.1 passes.

## 11. Integration checks

- [ ] 11.1 `test(cluster)`: write a two-instance integration test against MariaDB or PostgreSQL and a NATS container: a token disabled on instance A is refused on B, a logout on A ends the session on B, a saved configuration applies before the next poll, and a console line from B appears once in a session on A. Each test uses its own database name. Verify: the test passes with bounded waits only.
- [ ] 11.2 `ci(container)`: extend the `Container images` job in `.github/workflows/ci.yml` to start two replicas with a bus configured, and to run the existing upgrade rehearsal with the previous release image beside the new one on one database. Verify: the job passes, and the previous release still serves requests.

## 12. Aggregated status

- [ ] 12.1 `test(cluster)`: write failing tests that a status snapshot is published once per recorded snapshot with the fields listed in design section 11 and no secret or hostname field, that the summary sums memory, threads and failure counts, shows the shortest uptime, the single or "mixed" version and the worst health, that an instance is shown as stale after one interval and dropped after three, and that the clock is injected as `java.time.Clock` with no sleeps. Verify: the tests fail before 12.2.
- [ ] 12.2 `feat(cluster)`: add the `status.snapshot` event, the per-instance store with the stale and drop rules, the aggregation on read, and the cluster view in the status output. Verify: 12.1 passes, and the existing status tests still pass with their responses unchanged.
- [ ] 12.3 `test(frontend)`: write failing component tests that the dashboard shows the cluster summary above the per-instance list, that a stale instance is marked as stale, that the memory and thread graphs draw one series per instance, and that without a bus the dashboard renders as before. Verify: the tests fail before 12.4.
- [ ] 12.4 `feat(frontend)`: add the cluster summary and the per-instance list to `InstanceStatusPanel.vue`, and one series per instance to the memory and thread graphs in `ResourceTrends.vue`. Verify: 12.3 passes.
- [ ] 12.5 `docs(gitbook)`: document the cluster status view, the summary rules, the stale and drop rules and the per-instance graphs in `docs/gitbook/how-to/configure-cluster-bus.md`. Verify: `grep` finds the cluster view and the stale rule on that page.

## 13. Pull request

- [ ] 13.1 Open the pull request with the title `feat(cluster): share cache and revocation events across instances`, squash-merge only. Verify: `gh pr view` shows that exact title, and the commit-style hook accepts the title and body, which contain no attribution lines.
