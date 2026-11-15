# Proposal

## Why

A build that asks one group repository for `net.onelitefeather:ingot-api` expects the answer to come from the operator's own
hosted repository. Today a group asks its members in order and a proxy member answers any coordinate its upstream knows. If an
attacker publishes `net.onelitefeather:ingot-api` to Maven Central with a higher version, or the hosted copy is missing for a
moment, the group serves the public artifact into an internal build. This is dependency confusion, and Ingot has no switch
against it. Nexus offers the control only through the Firewall product, which needs a Pro licence and an IQ Server, so a
built-in switch is a reason to choose Ingot.

An operator needs one sentence to describe the wish: "these group ids belong to us, never fetch them from the outside". The
operator also needs to see when the rule fired, because a refusal is either an attack or a build that is missing its own
artifact, and both are worth knowing.

A second gap sits next to it. `checksumPolicy` shipped with #118 and its mismatches and refusals reach only the log
(`MirrorService.kt:204-233`). No audit event and no metric records them, so an operator cannot alert on a poisoned upstream.
That part of the roadmap item N13 is still open, so this change closes it.

## What Changes

- **P1 Protected prefixes.** New per-repository setting `protectedGroupPrefixes`, a list of group id prefixes such as
  `net.onelitefeather`, default empty. It is accepted on a repository whose effective type is `GROUP`. For a request whose
  coordinate falls under a prefix, the group asks hosted members only. A proxy member is skipped and never contacted. A prefix
  matches whole dot segments, so `net.onelitefeather` covers `net.onelitefeather.ingot` and not `net.onelitefeatherx`.
- **P2 Refusal answer.** When the hosted members do not hold the coordinate and a proxy member was skipped, the answer is `404`
  with the same body as an ordinary miss, so the response does not reveal that a rule exists. The request writes one audit event
  and moves one counter.
- **P3 Audit and metric.** New audit action `NAMESPACE_PROXY_REFUSED` and counter `ingot_namespace_refusals_total{repository}`.
- **P4 Mirror integrity events.** New audit actions `MIRROR_CHECKSUM_MISMATCH` and `MIRROR_CHECKSUM_MISSING`, written where the
  mirror logs those cases today for `warn` and `fail`, and a counter `ingot_mirror_checksum_results_total{policy,result}` with
  the results `verified`, `mismatch` and `missing`. It counts `warn` as well as `fail`. Nothing is written for `ignore`.
  Neither part existed on main when this change was written (design.md, D6), so both are built here.
- **Docs.** `docs/gitbook/how-to/create-a-group-repository.md` (a protection section), `docs/gitbook/reference/repository-settings.md`
  (the key), `docs/gitbook/reference/audit-events.md` (three actions), `docs/gitbook/how-to/monitor-status-and-failures.md` (two
  metrics) and `docs/gitbook/how-to/create-proxy-repositories.md` (a pointer from the checksum section to the events).

### Out of scope

- Protection on a proxy repository itself. A client that asks a proxy directly gets what the proxy serves. The rule guards the
  group, which is where members are mixed. A `MIXED` repository is refused for the key, see design.md D2.
- Protection for requests that never go through a group, and for directory listings of the ancestors of a prefix (`net/`).
- Allow lists that let one proxy serve one protected prefix. The rule has no exceptions in this change.
- Marking a hosted repository as proprietary. The setting lives on the group, because the group is where the choice is made.
- Upload checksum verification, vulnerability data and quarantine (other roadmap items).
- Folding of repeated refusal events. The counter carries the volume (design.md D5).
- Changing the default of any existing setting.

## Capabilities

### New Capabilities

- `namespace-protection`: protected group id prefixes on a group repository and the refusal of proxy members for them.
- `mirror-integrity-observability`: audit events and a metric for checksum results of a mirror.

## Impact

- **Type and scope.** The change ships under `feat(maven)`. The code lives in `reposilite-backend/src/main/kotlin/com/reposilite/maven/`,
  with one new action in the audit package and one counter in the prometheus plugin. The pull request title, squash-merged onto
  main, is:

  `feat(maven): refuse proxy members for protected group prefixes`

- **Breaking marker.** None. The key defaults to an empty list, so group resolution is unchanged for every existing install.
  The new audit actions are additional values of a string column and need no migration, and consumers that filter by
  action keep working. No configuration key, environment variable, volume, manifest or plugin has to change. The title carries
  no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None.
- **Dependencies.** None that block. `add-mirror-integrity` (#118) is merged and is the base of P4. `add-overload-protection`
  (N15) and `fix-atomic-filesystem-upload` touch `MirrorService.kt` as well, so rebase on whichever merges first.
- **Code.** `reposilite-backend` (maven, audit), `reposilite-plugins/prometheus-plugin` and `docs/gitbook/`.

## Verification

- Targeted tests per commit through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`. Integration tests run as
  `:reposilite-backend:integration --tests '<pattern>'`.
- `openspec validate add-namespace-protection --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-namespace-protection`
  prints nothing.
