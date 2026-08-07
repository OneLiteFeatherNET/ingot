# Changelog

## 1.0.0 (2026-08-07)


### ⚠ BREAKING CHANGES

* the version line restarts at 1.0.0, below the 3.5.28 the sources came from. Tooling that compares Ingot versions against Reposilite versions will read the change as a downgrade.

### Features

* **config:** accept ingot prefixes for external overrides ([45f603c](https://github.com/OneLiteFeatherNET/ingot/commit/45f603c6cd6bcdeb4e69e17420265ddc52cfd2ff))
* **frontend:** rebrand the dashboard to Ingot ([a9ef4aa](https://github.com/OneLiteFeatherNET/ingot/commit/a9ef4aaa708d3dedc9d5f4983bfd83b52ed46f1d))
* rebrand the user-facing runtime to Ingot ([f6b69ed](https://github.com/OneLiteFeatherNET/ingot/commit/f6b69ed08fa0e6c241375bee8113d695ad71e6e9))
* replace the upstream logo with an Ingot mark ([6bc6b03](https://github.com/OneLiteFeatherNET/ingot/commit/6bc6b033f562cced21d7b20497489cbf343bc70f))
* start Ingot's own version line at 1.0.0 ([c4fc88b](https://github.com/OneLiteFeatherNET/ingot/commit/c4fc88b571a165606f766229256270a73596c238))


### Bug Fixes

* **ci:** grant the permissions the reusable build workflow needs ([ad4c718](https://github.com/OneLiteFeatherNET/ingot/commit/ad4c718eb4c4cb350c2092269a22f1dede169be2))
* **ci:** stop calling the pull request build workflow on pushes ([8005b86](https://github.com/OneLiteFeatherNET/ingot/commit/8005b86718d14e7b5ce8fbf92e944015d149a6a3))
* **docker:** stop the compose example pinning the deprecated user id ([365900b](https://github.com/OneLiteFeatherNET/ingot/commit/365900bfc161a63f481de2bb6ba2974899ff0683))
* **status:** report Ingot releases instead of upstream versions ([43f3b63](https://github.com/OneLiteFeatherNET/ingot/commit/43f3b638d6c18fb885de5b306bb8b93a4061baff))

## Changelog

## Why this starts at 1.0.0

Ingot was forked from Reposilite 3.5.28 and restarted its version line, so the first entry
below is 1.0.0. The number moving backwards is not a reduction in scope: 1.0.0 has
everything 3.5.28 had. [VERSIONING.md](VERSIONING.md) explains the cut, how the two
projects line up, and what moving an existing instance over involves.

Entries are generated from [Conventional Commits](https://www.conventionalcommits.org/) by
[Release Please](https://github.com/googleapis/release-please). For changes made in
Reposilite before the fork, see
[upstream's releases](https://github.com/dzikoysk/reposilite/releases).
