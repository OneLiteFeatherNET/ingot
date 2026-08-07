# Maintaining the fork

Ingot is a fork of [Reposilite](https://github.com/dzikoysk/reposilite). Upstream keeps
moving, and so do we. This is how the two stay reconcilable.

## Remotes

```bash
git remote -v
# origin    https://github.com/OneLiteFeatherNET/ingot.git
# upstream  https://github.com/dzikoysk/reposilite.git
```

If `upstream` is missing:

```bash
git remote add upstream https://github.com/dzikoysk/reposilite.git
git fetch upstream
```

## Syncing

**Cherry-pick, do not merge.** A merge from `upstream/main` drags in every upstream commit
including the ones that conflict with the rebrand, and resolving that on a merge commit
buries which decision was made where. Cherry-picking keeps one upstream change per commit,
with `-x` recording where it came from.

```bash
git fetch upstream
git log --oneline $(git merge-base HEAD upstream/main)..upstream/main
git cherry-pick -x <sha>
```

`-x` appends a `(cherry picked from commit ...)` line, which is how you tell later whether
an upstream commit is already in. To list what is still outstanding:

```bash
git log --oneline --cherry-pick --right-only main...upstream/main
```

Do this often enough that the outstanding list stays short. A backlog of a hundred commits
is not a hundred small merges, it is one large one.

Upstream commits keep their original author. Do not rewrite the authorship of a change you
did not write, and do not reword the commit message beyond what a conflict resolution
requires.

**A cherry-picked fix that arrived without a test gets one from us.** That is the point of
running a fork: upstream's bar is upstream's business, ours is ours. See
`ExtensionsTest.kt` for the shape, including checking that the new test actually fails
against the pre-fix code.

## Where we deliberately diverge

These areas will conflict on almost every sync. That is expected, not a problem to solve
by giving up the divergence.

| Area | What we changed | On conflict |
|------|-----------------|-------------|
| `.github/` | Workflows, issue templates, readme, contributing | Keep ours. Upstream's CI targets their infrastructure. |
| `reposilite-site/` | Rebranded guides and site chrome | Keep ours, but read upstream's version: a guide change is usually a real documentation fix wearing the wrong name. |
| Branding strings in the backend | Banner, CLI name, page titles, settings defaults | Keep ours. Take any surrounding behaviour change. |
| `build.gradle.kts`, `settings.gradle.kts` | Group id, artifact names, repositories, release tooling | Keep ours. Take dependency version bumps. |
| `Dockerfile`, `entrypoint.sh`, `docker-compose.yml`, `.env` | Ingot artifact names, `INGOT_*` variables | Keep ours. Take runtime fixes. |
| `reposilite-frontend/src/components/dashboard/*Chart.vue` | ECharts instead of ApexCharts | Keep ours. An upstream chart change has to be reimplemented, not merged. |

Everything else, which is most of the backend, should merge cleanly. If a core file starts
conflicting on every sync, that is a signal the change belongs upstream as a pull request
rather than in the fork.

## What stays upstream's

Some names look like leftovers and are not. They are the contract an installed instance and
a third-party plugin depend on, and renaming them breaks people for no gain:

- The Kotlin and Java packages `com.reposilite.*`
- The module directories `reposilite-*` and their library artifact ids
- `configuration.cdn`, `configuration.shared.json`, `reposilite.db`, `reposilite.address`
- The `{{REPOSILITE.*}}` frontend placeholders, which plugins also register into
- `/app/data`, `/var/log/reposilite` and the `reposilite` service user in the container
- `REPOSILITE_LOCAL_`, `reposilite.local.`, `REPOSILITE_OPTS`, which still work alongside
  their `INGOT_` counterparts

`com.reposilite.journalist` is a separate library by the same author, not our code. It is
never renamed, and it is the one dependency not available from Maven Central.

## Sign-off

Contributions carry a [DCO](https://developercertificate.org/) sign-off, documented in
[CONTRIBUTING.md](CONTRIBUTING.md). It is not enforced by CI yet.

> **TODO(onelitefeather):** add the check. Whatever enforces it has to exempt commits
> cherry-picked from upstream, which carry no sign-off because Reposilite does not use the
> DCO, and it has to leave their original authorship alone. A check that simply requires
> `Signed-off-by` on every commit would make syncing impossible.

## Contributing back

A fix that is not about branding and not about a decision specific to Ingot belongs
upstream too. Open the pull request there as well; it costs one push and it keeps the two
trees from drifting further than they need to.
