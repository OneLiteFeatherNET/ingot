# Versioning

Ingot starts at **1.0.0**.

The sources it started from were Reposilite 3.5.28. So the first thing you will notice is
that the version number went *down*. That is deliberate, and this page explains exactly
what it does and does not mean, because a version number that moves backwards is normally
a warning sign.

## What happened

Ingot is a fork of [Reposilite](https://github.com/dzikoysk/reposilite). At the point of
the fork we had two options for numbering.

**Continue the upstream line** and release 3.5.29, 3.6.0 and so on. That is convenient for
about a month and misleading forever: our 3.6.0 and upstream's 3.6.0 would be different
software with the same name for the same number, and neither project could ever say "this
was fixed in 3.6.0" without asking which 3.6.0 you meant. It also implies a continuity of
maintainership that does not exist. We are not the Reposilite maintainers, and we do not
speak for that project.

**Start our own line**, which is what we did. Ingot's version numbers describe Ingot and
nothing else. There is one clean break, here, instead of a permanent ambiguity.

The break is complete:

- The version resets to 1.0.0.
- The Reposilite tags are not part of this repository. Its history begins with the fork,
  and its tags begin with `v1.0.0`. Upstream's tags stay where they belong, in
  [upstream's repository](https://github.com/dzikoysk/reposilite/tags).
- The Maven coordinates changed, so nothing can be confused with an upstream artifact:
  `com.reposilite:reposilite` became `net.onelitefeather.ingot:ingot`.

## 1.0.0 is not less software than 3.5.28

The number restarted. The feature set did not.

Ingot 1.0.0 contains everything Reposilite 3.5.28 contained: the repositories, mirroring
and caching, S3 and filesystem storage, the access token and route permission model, LDAP,
the dashboard, the plugin system, the REST API. Nothing was removed to reach 1.0.0.

Roughly: **Ingot 1.0.0 corresponds to Reposilite 3.5.28**, plus the changes this project
made on top, which are in [CHANGELOG.md](CHANGELOG.md).

If you are comparing the two projects, compare features and maintenance, not version
numbers. A 1.0.0 that is a rename of a mature 3.5.x is a different thing from a 1.0.0 that
is somebody's first release, and only the changelog can tell you which one you are looking
at.

## What the numbers mean from here

[Semantic Versioning](https://semver.org/), applied to the parts of Ingot that other people
depend on:

| Change | Bump |
|--------|------|
| Breaking change to the plugin API, the REST API, the configuration format, or the on-disk layout | major |
| New capability, backwards compatible | minor |
| Bug fix, backwards compatible | patch |

The version is derived from [Conventional Commits](https://www.conventionalcommits.org/) by
[Release Please](https://github.com/googleapis/release-please). It is not chosen by hand,
so it reflects what actually changed rather than what we felt like calling it.

## Coming from Reposilite

Ingot deliberately keeps the things a running instance and a third party plugin depend on,
so moving over is not a migration:

- **Your data works as is.** The repository layout, `configuration.cdn`,
  `configuration.shared.json` and the `reposilite.db` database are unchanged. Point Ingot
  at an existing working directory and it starts.
- **Your plugins keep compiling.** The Java and Kotlin packages are still `com.reposilite.*`
  and the module artifact ids are still `reposilite-*`. A plugin changes one dependency
  line, from `com.reposilite:reposilite` to `net.onelitefeather.ingot:ingot`, and nothing
  else.
- **Your deployment keeps starting.** `REPOSILITE_OPTS`, `REPOSILITE_LOCAL_*` and
  `-Dreposilite.local.*` are still read. The `INGOT_` equivalents exist alongside them and
  take precedence when both are set, so you can migrate a manifest one variable at a time.
- **Container paths are unchanged**: `/app/data`, `/var/log/reposilite` and the
  `reposilite` service user. Bind mounts and log shippers do not have to be touched.

What does change: the jar is `ingot-<version>.jar`, the image is
`ghcr.io/onelitefeathernet/ingot`, and the dashboard says Ingot.

There is no automatic downgrade path back to Reposilite once you have run a newer Ingot
against your data. That is the normal situation for any repository manager upgrade: take a
backup first.

## Why you can check this yourself

Nothing above requires trusting us. The fork point is in the git history, the changes since
are individual commits, and the attribution and statement of changes required by the Apache
License 2.0 are in [NOTICE](NOTICE).

The upstream history was deliberately *not* rewritten. Erasing it would have made the
version cut look tidier and would have destroyed the record of what Ingot inherited and
from whom. The version line is new; the provenance is intact.
