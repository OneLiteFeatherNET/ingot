# Changelog

## 1.0.0 (2026-08-08)


### ⚠ BREAKING CHANGES

* the version line restarts at 1.0.0, below the 3.5.28 the sources came from. Tooling that compares Ingot versions against Reposilite versions will read the change as a downgrade.

### Features

* **config:** accept ingot prefixes for external overrides ([45f603c](https://github.com/OneLiteFeatherNET/ingot/commit/45f603c6cd6bcdeb4e69e17420265ddc52cfd2ff))
* **docker:** run the dashboard and the server as separate hardened containers ([#6](https://github.com/OneLiteFeatherNET/ingot/issues/6)) ([f7bff49](https://github.com/OneLiteFeatherNET/ingot/commit/f7bff4949046c44aca33859d234a930833c75b1c))
* **frontend:** rebrand the dashboard to Ingot ([a9ef4aa](https://github.com/OneLiteFeatherNET/ingot/commit/a9ef4aaa708d3dedc9d5f4983bfd83b52ed46f1d))
* rebrand the user-facing runtime to Ingot ([f6b69ed](https://github.com/OneLiteFeatherNET/ingot/commit/f6b69ed08fa0e6c241375bee8113d695ad71e6e9))
* replace the upstream logo with an Ingot mark ([6bc6b03](https://github.com/OneLiteFeatherNET/ingot/commit/6bc6b033f562cced21d7b20497489cbf343bc70f))
* start Ingot's own version line at 1.0.0 ([c4fc88b](https://github.com/OneLiteFeatherNET/ingot/commit/c4fc88b571a165606f766229256270a73596c238))


### Bug Fixes

* **build:** order the coverage report after both integration suites ([eed6e68](https://github.com/OneLiteFeatherNET/ingot/commit/eed6e6801f9009c1e9805ce153e1364fac68741d))
* **ci:** grant the permissions the reusable build workflow needs ([ad4c718](https://github.com/OneLiteFeatherNET/ingot/commit/ad4c718eb4c4cb350c2092269a22f1dede169be2))
* **ci:** stop calling the pull request build workflow on pushes ([8005b86](https://github.com/OneLiteFeatherNET/ingot/commit/8005b86718d14e7b5ce8fbf92e944015d149a6a3))
* **deps:** close all 67 known dependency advisories and gate against new ones ([0b6a85d](https://github.com/OneLiteFeatherNET/ingot/commit/0b6a85db9e3b1918ae2dc0833cdb59d40c712b1b))
* **deps:** close the known advisories in the backend dependencies ([9a490ad](https://github.com/OneLiteFeatherNET/ingot/commit/9a490ad431110c1497cae3b97f016c41a8cb55d4))
* **deps:** close the known advisories in the dashboard dependencies ([83c81cf](https://github.com/OneLiteFeatherNET/ingot/commit/83c81cf085c6e4160f75fc2b48c60a2937eedcac))
* **deps:** close the known advisories in the documentation site ([d343e4c](https://github.com/OneLiteFeatherNET/ingot/commit/d343e4c84b3a3389bfefca0f220e0d92ccff800c))
* **docker:** restore drop-in compatibility and stop emulating the build ([#7](https://github.com/OneLiteFeatherNET/ingot/issues/7)) ([8b0d9c6](https://github.com/OneLiteFeatherNET/ingot/commit/8b0d9c69490593580b0cfc1173ae75a6a58f1b9c))
* **docker:** stop the compose example pinning the deprecated user id ([365900b](https://github.com/OneLiteFeatherNET/ingot/commit/365900bfc161a63f481de2bb6ba2974899ff0683))
* **status:** report Ingot releases instead of upstream versions ([43f3b63](https://github.com/OneLiteFeatherNET/ingot/commit/43f3b638d6c18fb885de5b306bb8b93a4061baff))

## Changelog
