# Changelog

## [1.2.0](https://github.com/OneLiteFeatherNET/ingot/compare/v1.1.1...v1.2.0) (2026-10-17)


### Features

* add the material 3 dashboard and the identity model ([#21](https://github.com/OneLiteFeatherNET/ingot/issues/21)) ([3824878](https://github.com/OneLiteFeatherNET/ingot/commit/3824878820a0a0cc6d129db8527178b0c0bbb419))
* **identity:** scope permission nodes to maven artifacts ([#32](https://github.com/OneLiteFeatherNET/ingot/issues/32)) ([1c2373c](https://github.com/OneLiteFeatherNET/ingot/commit/1c2373c0603e0aff2746f51e41e2fe811fd5aad7))
* **maven:** add hosted, proxy and group repository types ([#38](https://github.com/OneLiteFeatherNET/ingot/issues/38)) ([19189c7](https://github.com/OneLiteFeatherNET/ingot/commit/19189c751db06d2eea4718315094f3852c9cdcbd))
* **maven:** list the repositories a group references ([#27](https://github.com/OneLiteFeatherNET/ingot/issues/27)) ([cf5a690](https://github.com/OneLiteFeatherNET/ingot/commit/cf5a690cf38eb5e7243831bcee5458a0a56943f2))
* **search:** search artifacts across repositories ([#35](https://github.com/OneLiteFeatherNET/ingot/issues/35)) ([402ae39](https://github.com/OneLiteFeatherNET/ingot/commit/402ae39466920e70eae54d4f492304cd769ce057))
* **ui:** edit and check artifact permission nodes ([#33](https://github.com/OneLiteFeatherNET/ingot/issues/33)) ([5e37766](https://github.com/OneLiteFeatherNET/ingot/commit/5e37766f2fa78368350bb36f65fc5213fdc89287))
* **ui:** manage hosted, proxy and group repositories ([#39](https://github.com/OneLiteFeatherNET/ingot/issues/39)) ([5f727c2](https://github.com/OneLiteFeatherNET/ingot/commit/5f727c26c85cc8f4b37b36e836163674ac954057))


### Bug Fixes

* **console:** connect the remote console with a session login ([#29](https://github.com/OneLiteFeatherNET/ingot/issues/29)) ([03f687c](https://github.com/OneLiteFeatherNET/ingot/commit/03f687c060d73ce77077e70876c8b154d09905cf))
* **console:** stop failing log subscribers from escalating errors ([#34](https://github.com/OneLiteFeatherNET/ingot/issues/34)) ([62b8bdf](https://github.com/OneLiteFeatherNET/ingot/commit/62b8bdf45eebd371acb487f13679e1050cca6584))
* **deps:** update nanoid and source-map-js in the site lockfile ([2b2eda0](https://github.com/OneLiteFeatherNET/ingot/commit/2b2eda03ba02a4b77ecdcf2ee90a9f0fb2f6b0b9))
* **docker:** serve every dashboard route from the dashboard image ([#31](https://github.com/OneLiteFeatherNET/ingot/issues/31)) ([7ef3171](https://github.com/OneLiteFeatherNET/ingot/commit/7ef3171ae2e5b2c9811107d47d93c80670294088))
* **docker:** upgrade libexpat in the dashboard image ([#49](https://github.com/OneLiteFeatherNET/ingot/issues/49)) ([a71d395](https://github.com/OneLiteFeatherNET/ingot/commit/a71d395372f0acd445d6e12415a681c737c652da))
* **frontend:** read api instants as epoch seconds ([#25](https://github.com/OneLiteFeatherNET/ingot/issues/25)) ([5e2eab4](https://github.com/OneLiteFeatherNET/ingot/commit/5e2eab4d9a3120f6eae1028527be00ae778b3b60))
* **frontend:** stop padding dashboard assets with zero bytes ([#23](https://github.com/OneLiteFeatherNET/ingot/issues/23)) ([91a9079](https://github.com/OneLiteFeatherNET/ingot/commit/91a907903d0943558e9f0b7904fd812001cae934))
* **frontend:** tidy the details card and breadcrumb on the browse page ([#28](https://github.com/OneLiteFeatherNET/ingot/issues/28)) ([fa0c8d1](https://github.com/OneLiteFeatherNET/ingot/commit/fa0c8d1a11c7f3241dfef5d1c64c571365329e56))
* **identity:** enforce admin area permission nodes ([#41](https://github.com/OneLiteFeatherNET/ingot/issues/41)) ([fc8b45a](https://github.com/OneLiteFeatherNET/ingot/commit/fc8b45a15940b97a97b82169d8fd55fb09f44448))
* keep mirror credentials when saving repository settings ([#47](https://github.com/OneLiteFeatherNET/ingot/issues/47)) ([0b92ee6](https://github.com/OneLiteFeatherNET/ingot/commit/0b92ee6cd896ff9d3b698dfa54ad921d922db737))
* keep the session when browsing a group repository ([#46](https://github.com/OneLiteFeatherNET/ingot/issues/46)) ([5cba28a](https://github.com/OneLiteFeatherNET/ingot/commit/5cba28a0562b84042d3167f1180a623630d49e3f))
* **login:** accept legacy ldap credentials in the login dialog ([193ba59](https://github.com/OneLiteFeatherNET/ingot/commit/193ba59f5493d52703fd135d418471e4b9fa45c6))
* **maven:** let a repository with an empty entry take a type ([#45](https://github.com/OneLiteFeatherNET/ingot/issues/45)) ([00ee5ab](https://github.com/OneLiteFeatherNET/ingot/commit/00ee5ab151b61639d2dff79dd766a6ae192c3b3b))
* **settings:** accept weak etags from compressing proxies ([#44](https://github.com/OneLiteFeatherNET/ingot/issues/44)) ([44e1334](https://github.com/OneLiteFeatherNET/ingot/commit/44e1334490fb5188b2c600671a1e5d12c1683a5c))
* **settings:** render the authentication settings form ([#24](https://github.com/OneLiteFeatherNET/ingot/issues/24)) ([06d6e1b](https://github.com/OneLiteFeatherNET/ingot/commit/06d6e1b0f814008afffeb61e79678f571cc3e486))
* **storage:** count stored bytes of s3 repositories ([#26](https://github.com/OneLiteFeatherNET/ingot/issues/26)) ([4d8aedc](https://github.com/OneLiteFeatherNET/ingot/commit/4d8aedc11857e6622768cd9f6545c9dd7d48399a))
* **storage:** keep unchanged repositories live across settings reloads ([#43](https://github.com/OneLiteFeatherNET/ingot/issues/43)) ([ca5763f](https://github.com/OneLiteFeatherNET/ingot/commit/ca5763f06b96c04d91248249ecf58e91ab9678fb))
* **storage:** warn when a repository walk cannot read its root ([#30](https://github.com/OneLiteFeatherNET/ingot/issues/30)) ([2cf1d05](https://github.com/OneLiteFeatherNET/ingot/commit/2cf1d0532498985c26d24e77c1cea00d5bc5f9f4))
* **ui:** keep long addresses inside the audit actor column ([#36](https://github.com/OneLiteFeatherNET/ingot/issues/36)) ([156efe0](https://github.com/OneLiteFeatherNET/ingot/commit/156efe052ead599a482dc6a4b989b1cefaeedfb5))
* **ui:** show the right message when the first admin setup fails ([#40](https://github.com/OneLiteFeatherNET/ingot/issues/40)) ([4856ed7](https://github.com/OneLiteFeatherNET/ingot/commit/4856ed700f8d4ee2988e8be2d89d16edd2e82268))
* **ui:** theme select fields and stop their jump on focus ([#48](https://github.com/OneLiteFeatherNET/ingot/issues/48)) ([a1d7fbf](https://github.com/OneLiteFeatherNET/ingot/commit/a1d7fbfe53cc5fe6f9165ef2e84dfe1da8b2f4bc))

## [1.1.1](https://github.com/OneLiteFeatherNET/ingot/compare/v1.1.0...v1.1.1) (2026-10-16)


### Bug Fixes

* **console:** render log output as text instead of html ([#16](https://github.com/OneLiteFeatherNET/ingot/issues/16)) ([1364ee2](https://github.com/OneLiteFeatherNET/ingot/commit/1364ee2f0fed3ba303266cb6ee62d710df66c738))
* **deps:** update dependencies with known vulnerabilities ([#19](https://github.com/OneLiteFeatherNET/ingot/issues/19)) ([05a6160](https://github.com/OneLiteFeatherNET/ingot/commit/05a616027d56cedabb79d8895b82e090bcc67fc6))
* escape request paths in generated html pages ([#17](https://github.com/OneLiteFeatherNET/ingot/issues/17)) ([5b1ccad](https://github.com/OneLiteFeatherNET/ingot/commit/5b1ccadd359c5ed2d612299605748ed918d045cb))
* **storage:** answer a repeated delete of a missing file with success ([#18](https://github.com/OneLiteFeatherNET/ingot/issues/18)) ([1744fc1](https://github.com/OneLiteFeatherNET/ingot/commit/1744fc11f67d28be11887c33bfc1a01ee9c4b9b2))

## [1.1.0](https://github.com/OneLiteFeatherNET/ingot/compare/v1.0.0...v1.1.0) (2026-08-09)


### Features

* **backend:** report repository visibility in the root listing ([#14](https://github.com/OneLiteFeatherNET/ingot/issues/14)) ([3d9d067](https://github.com/OneLiteFeatherNET/ingot/commit/3d9d067d8fee0a236f0b60ec586a5453a999bce3))
* **frontend:** move the dashboard to Tailwind CSS v4 and rebuild it around limits ([#10](https://github.com/OneLiteFeatherNET/ingot/issues/10)) ([d137bd3](https://github.com/OneLiteFeatherNET/ingot/commit/d137bd3e6796fe4638d2e9505444081c2135ab45))
* **frontend:** work through the dashboard design audit ([#13](https://github.com/OneLiteFeatherNET/ingot/issues/13)) ([b14183d](https://github.com/OneLiteFeatherNET/ingot/commit/b14183d6637f10304179c2433a0bce9a34809ef3))

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
