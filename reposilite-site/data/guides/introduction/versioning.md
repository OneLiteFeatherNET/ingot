---
id: versioning
title: Versioning
---

Ingot starts at **1.0.0**.

The sources it started from were Reposilite 3.5.28, so the first thing you will notice is
that the version number went *down*. That is deliberate. A version number moving backwards
is normally a warning sign, so here is exactly what it does and does not mean.

### Why the line restarts

At the fork there were two options.

**Continue the upstream line** and release 3.5.29, 3.6.0 and so on. Convenient for about a
month, misleading forever: our 3.6.0 and upstream's 3.6.0 would be different software
sharing a number, and neither project could say "fixed in 3.6.0" without asking which one
you meant. It also implies a continuity of maintainership that does not exist. We are not
the Reposilite maintainers and we do not speak for that project.

**Start our own line**, which is what we did. One clean break instead of a permanent
ambiguity.

The break is complete. The Reposilite tags are not part of this repository, ours begin at
`v1.0.0`, and the Maven coordinates moved from `com.reposilite:reposilite` to
`net.onelitefeather.ingot:ingot`, so no artifact can be mistaken for an upstream one.

### 1.0.0 is not less software than 3.5.28

The number restarted. The feature set did not.

Ingot 1.0.0 contains everything Reposilite 3.5.28 contained: repositories, mirroring and
caching, S3 and filesystem storage, the access token and route permission model, LDAP, the
dashboard, the plugin system, the REST API. Nothing was removed to reach 1.0.0.

Roughly, **Ingot 1.0.0 corresponds to Reposilite 3.5.28** plus the changes this project made
on top.

If you are comparing the two projects, compare features and maintenance rather than version
numbers. A 1.0.0 that is a rename of a mature 3.5.x is a different thing from somebody's
first release, and only the changelog tells you which one you are looking at.

### What the numbers mean from here

[Semantic Versioning](https://semver.org/), applied to what other people depend on:

| Change | Bump |
|--------|------|
| Breaking change to the plugin API, the REST API, the configuration format, or the on-disk layout | major |
| New capability, backwards compatible | minor |
| Bug fix, backwards compatible | patch |

The version is derived from [Conventional Commits](https://www.conventionalcommits.org/),
not chosen by hand, so it reflects what actually changed.

### Coming from Reposilite

Moving over is not a migration. Ingot deliberately keeps what a running instance and a
third party plugin depend on:

* **Your data works as is.** The repository layout, `configuration.cdn`,
  `configuration.shared.json` and `reposilite.db` are unchanged. Point Ingot at an existing
  working directory and it starts.
* **Your plugins keep compiling.** The packages are still `com.reposilite.*` and the module
  artifact ids are still `reposilite-*`. A plugin changes one dependency line, from
  `com.reposilite:reposilite` to `net.onelitefeather.ingot:ingot`, and nothing else.
* **Your deployment keeps starting.** `REPOSILITE_OPTS`, `REPOSILITE_LOCAL_*` and
  `-Dreposilite.local.*` are still read. The `INGOT_` equivalents exist alongside them and
  win when both are set, so a manifest can be migrated one variable at a time.
* **Container paths are unchanged**: `/app/data`, `/var/log/reposilite` and the
  `reposilite` service user.

What changes: the jar is `ingot-<version>.jar`, the image is
`ghcr.io/onelitefeathernet/ingot`, and the dashboard says Ingot.

There is no automatic downgrade back to Reposilite once a newer Ingot has run against your
data. That is normal for any repository manager upgrade. Take a backup first.

### You can check this yourself

None of the above requires trusting us. The fork point is in the git history, the changes
since are individual commits, and the attribution and statement of changes required by the
Apache License 2.0 are in the repository's `NOTICE` file.

The upstream history was deliberately *not* rewritten. Erasing it would have made the cut
look tidier and destroyed the record of what Ingot inherited and from whom.
