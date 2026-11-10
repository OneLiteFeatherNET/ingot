# Design

## What exists today

Ingot has no separate group object. A repository is a group when its effective type is `GROUP`: a declared `type: GROUP` or an
untyped repository whose references are all local (`RepositoryType.kt:44-62`, `Repository.kt:56-60`). Its members are the
loopback entries of `mirrorHosts`, and `RepositoryService.findMembers` lists them (`RepositoryService.kt:267-285`). A typed group
takes no `store` and no external URL (`MavenSettings.kt:247-258`).

Resolution runs in `ResolutionProvider.resolve`. It takes `hosts` from `repository.mirrorHosts`, narrowed to the pinned host when
the resolution cache has one (`ResolutionProvider.kt:59-62`), and asks them through `MirrorService.findRemoteFile` or
`findRemoteDetails` (`RepositoryService.kt:206-232`). A loopback member is reached by `RepositoryLoopbackClient`, which calls the
member repository's own lookup (`RepositoryLoopbackClient.kt:37-58`). So a proxy member of a group is itself a repository with
external hosts, and nothing in the group tells hosted members from proxy members.

The only existing coordinate filter is `allowedGroups` on a mirror. It is a plain `startsWith` on the path without a segment
boundary (`MirrorService.kt:346-351`), and it narrows what one mirror may fetch. It cannot say "this coordinate must not come
from a proxy", so it is not reused.

## D1 The rule sits on the group, in host selection

The key is `protectedGroupPrefixes` on `RepositorySettings`. The group already is the place where members are mixed, so the
operator states the rule there and one repository is enough. The enforcement is a filter on the `hosts` list that
`ResolutionProvider.resolve` builds, applied after the cached-host narrowing and before either lookup. `RepositoryService`
supplies the filter, because it can reach `RepositoryProvider` to look at the member repositories. A filtered host is never
contacted: asking the proxy would already send the coordinate to the outside, which is the leak the rule prevents.

Alternatives considered:

- A flag `proprietary` on the hosted repository, as the research report proposed. Rejected: the proxy would have to know which
  hosted repositories exist, and a group of two hosted repositories and one proxy still needs the group to decide.
- A filter inside `MirrorService`. Rejected: it sees one repository and its hosts, not the group-level intent.
- Reusing `allowedGroups` on the proxy member. Rejected: it lives on the proxy, so it can be forgotten, and it has no segment
  boundary.

## D2 Which members count as hosted

A member is hosted when its effective type is `HOSTED`. A member whose effective type is `PROXY` or `MIXED` is a proxy member,
because it reaches an external URL, and a `MIXED` member may also hold cached proxy copies. A member that is itself a `GROUP` is
decided by its members: it is hosted only when every one of them is hosted, checked recursively with a visited set so a cycle
cannot loop. A nested group with one proxy member is therefore skipped as a whole. That is conservative on purpose. The
alternative, passing the restriction down into the nested group, would make a loopback call depend on its caller, and the
loopback client takes only a repository name. The how-to tells operators to put hosted members directly into the protected group.

The key is accepted only when the effective type of the repository is `GROUP`. A `MIXED` repository holds its own storage that
may contain proxy copies, and `tryLocal` would serve them, so the rule cannot hold there. Settings validation refuses the key on
any other type, with a message that names `protectedGroupPrefixes`. Validation fires only when the key is set, so an existing
untyped repository keeps loading as it did. The classification is computed from the repositories the current configuration
built, so it follows a settings change without a restart. It is cheap (a walk over a handful of repositories) and is not cached.

## D3 Matching

A prefix is a dotted group id. Validation accepts segments of letters, digits, `_` and `-`, joined by single dots, with no
leading or trailing dot, no slash and no duplicate. At request time the prefix is turned into path segments and compared to the
leading segments of the coordinate, so `net.onelitefeather` matches `net/onelitefeather/ingot/1.0/ingot-1.0.jar` and
`net/onelitefeather/maven-metadata.xml`, and does not match `net/onelitefeatherx/...`. The comparison is case sensitive, as the
Maven layout is. Paths shorter than the prefix, such as `net/`, are ancestors and are not protected (out of scope).

## D4 The refusal looks like a miss

When the hosted members answer, the answer is served and nothing else happens. When they do not and at least one proxy member was
skipped, the answer is the ordinary `404` of an unresolved coordinate, with the same message as `ResolutionProvider.kt:53`. A
different status or text would tell a caller which prefixes are protected. The empty-host case (`NoEligibleHosts`) already maps to
that `404` (`Mirror.kt:44-47`), so the filter needs no new answer type. The metadata miss is cached as `MissingMetadata` in the
resolution cache like any other miss. The cache lives in the `Repository` object, which a settings change rebuilds, so a changed
prefix list is not shadowed by an old entry.

## D5 Audit event and metric

The event is raised only when the refusal changed the outcome: a proxy member was skipped and the hosted members missed. A
request that the hosted members serve produces no event, because nothing was prevented and a busy build would write one row per
file. `ResolutionProvider` raises a new `ProxyRefusedEvent` (repository, coordinate, the token identifier of the request) on the
plugin event bus, as it already does for `ResolutionInvalidatedEvent`. The audit plugin listens and writes
`NAMESPACE_PROXY_REFUSED` with `success=false`, `repository` and `path` set, and the details keys `prefix` and `skippedMembers`
(names only). The actor name is resolved from the token identifier through the token facade, and an unresolved token is
`anonymous`. The client address is not known at this layer, so it stays empty. The first task of the code change confirms the
token lookup and, if the address cannot be had without changing the request path, leaves it empty and says so in the pull request.

The counter `ingot_namespace_refusals_total{repository}` counts the same moments. `repository` is bounded by the configured
groups, so the label set is small. Repeated requests for one missing coordinate write one row each. Folding them like failed
logins (`AuditFacade.kt:91-120`) was considered and left out: it needs a new key shape, and the audit retention and the counter
already bound the cost. The how-to names the counter as the signal to alert on.

## D6 Mirror integrity events: checked against main

The brief asked to build the events and the metric only if #118 did not. On main (`afa3291d`) they are absent. `AuditAction`
has no mirror action (`AuditAction.kt:24-53`), the `warn` and `refuse` paths only call `logger.warn`
(`MirrorService.kt:204-233`), and `MavenOperationCounters` knows deploys and upstream outcomes only
(`MavenOperationCounters.kt:28-62`). The metric registry in `OperationalMetrics.kt:70-77` has no checksum series. So P4 is
built here and #118 is its base.

Events: `MIRROR_CHECKSUM_MISMATCH` when a copy does not match the upstream checksum, and `MIRROR_CHECKSUM_MISSING` when a host
publishes no usable checksum file. Both carry `success=false`, the actor `system` (the fetch has no request actor at this depth),
the repository and path, and the details keys `host`, `policy` (`warn` or `fail`), `algorithm` and `outcome` (`stored` under
`warn`, `refused` under `fail`). `MirrorService` raises a `MirrorIntegrityEvent`, and the audit plugin maps it. The counter
`ingot_mirror_checksum_results_total{policy,result}` reads a new section of `MavenOperationCounters`, so the series exist from the
start with every label value, like the existing ones. A verified copy counts as `verified`. `ignore` counts nothing.

Audit consumers tolerate new values: the action is stored as a name and the table has no constraint on it, and the reference page
lists the new names.

## Consequences

- A hosted member that lacks an artifact now yields a `404` for a protected coordinate even when a proxy has it. That is the
  point, and the event tells the operator that a build wants an artifact the hosted repository does not hold.
- A group that mixes hosted and proxy members and sets no prefix behaves exactly as before.
- Protection ends where the group ends. A client that is configured with the proxy URL directly is not covered, and the how-to says so.
