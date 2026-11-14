# Changelog

## [1.2.0](https://github.com/OneLiteFeatherNET/ingot/compare/v1.1.1...v1.2.0) (2026-11-14)


### Features

* add the material 3 dashboard and the identity model ([#21](https://github.com/OneLiteFeatherNET/ingot/issues/21)) ([3824878](https://github.com/OneLiteFeatherNET/ingot/commit/3824878820a0a0cc6d129db8527178b0c0bbb419))
* **admin:** add cluster-wide read-only mode ([#116](https://github.com/OneLiteFeatherNET/ingot/issues/116)) ([09e6ee5](https://github.com/OneLiteFeatherNET/ingot/commit/09e6ee52fba0eff420a851fd700f28fc5e9e8171))
* **admin:** add system information page and support bundle ([#138](https://github.com/OneLiteFeatherNET/ingot/issues/138)) ([a3cb779](https://github.com/OneLiteFeatherNET/ingot/commit/a3cb779001e5e21501b52dae9c9c2c59ba0706ad))
* **api:** add problem details, range requests and conditional artifact downloads ([#83](https://github.com/OneLiteFeatherNET/ingot/issues/83)) ([d41fea6](https://github.com/OneLiteFeatherNET/ingot/commit/d41fea6951c6e6b75b1957c19ecf1c35a3bd28bd))
* **api:** declare the search run trigger as an extensible enum ([#201](https://github.com/OneLiteFeatherNET/ingot/issues/201)) ([68d1a7f](https://github.com/OneLiteFeatherNET/ingot/commit/68d1a7f6c2e0f27cf270d47848185b96abc9234e))
* **audit:** add a json lines audit sink and repository events ([#135](https://github.com/OneLiteFeatherNET/ingot/issues/135)) ([8b28c51](https://github.com/OneLiteFeatherNET/ingot/commit/8b28c51a1b3ac8ca2abd5f69edd5477b492c5d1c))
* **backup:** add online sqlite database snapshots ([#117](https://github.com/OneLiteFeatherNET/ingot/issues/117)) ([b24ce1b](https://github.com/OneLiteFeatherNET/ingot/commit/b24ce1b9b004221f7101e5105f3bdee66c5daf0f))
* **chart:** add opt-in network policy and extra ca bundle ([#123](https://github.com/OneLiteFeatherNET/ingot/issues/123)) ([9a01557](https://github.com/OneLiteFeatherNET/ingot/commit/9a0155778ef1d1439c315c45d282b43192b12881))
* **chart:** add opt-in telemetry, jvm options and shared settings ([#120](https://github.com/OneLiteFeatherNET/ingot/issues/120)) ([0ff9ecf](https://github.com/OneLiteFeatherNET/ingot/commit/0ff9ecf51c8c233414e40776cc6b3dbfac322e2a))
* **chart:** add readiness path, shutdown budget and replica warnings ([#113](https://github.com/OneLiteFeatherNET/ingot/issues/113)) ([e0f048b](https://github.com/OneLiteFeatherNET/ingot/commit/e0f048b487b9330a8ceda94143766597dd6841c6))
* **chart:** add typed HA values and an opt-in restricted profile ([#109](https://github.com/OneLiteFeatherNET/ingot/issues/109)) ([ba6e3fe](https://github.com/OneLiteFeatherNET/ingot/commit/ba6e3feecb4bc5d3efba74da16103715bfc28642))
* **cluster:** share cache and revocation events across instances ([#80](https://github.com/OneLiteFeatherNET/ingot/issues/80)) ([9b8b9e5](https://github.com/OneLiteFeatherNET/ingot/commit/9b8b9e50fd63b9dc2811720312a438b7e8e226a5))
* **configuration:** read local settings from files with _FILE variables ([#102](https://github.com/OneLiteFeatherNET/ingot/issues/102)) ([98945e7](https://github.com/OneLiteFeatherNET/ingot/commit/98945e7940c71ed7e6203ba5ab2790e29eb043ad))
* **configuration:** reload shared settings from a mounted file ([#111](https://github.com/OneLiteFeatherNET/ingot/issues/111)) ([39a35bb](https://github.com/OneLiteFeatherNET/ingot/commit/39a35bbeebc29ffa2bbe75970ff2a740be51884c))
* **configuration:** resolve secret references in shared settings ([#125](https://github.com/OneLiteFeatherNET/ingot/issues/125)) ([dfcfafa](https://github.com/OneLiteFeatherNET/ingot/commit/dfcfafa85cf2a843771c3424eb16c76eb1107c67))
* **deploy:** add deploy modes, a nitro dashboard image and a helm chart ([#77](https://github.com/OneLiteFeatherNET/ingot/issues/77)) ([c0f0184](https://github.com/OneLiteFeatherNET/ingot/commit/c0f01846ba98011e314238aacde459e1156fbc95))
* **docker:** import extra ca certificates into the trust store at start ([#131](https://github.com/OneLiteFeatherNET/ingot/issues/131)) ([bdf9605](https://github.com/OneLiteFeatherNET/ingot/commit/bdf9605c57df18c984392454d64c375b8e37953e))
* **frontend:** add stable data-ingot anchors to every dashboard component ([#166](https://github.com/OneLiteFeatherNET/ingot/issues/166)) ([50e195b](https://github.com/OneLiteFeatherNET/ingot/commit/50e195bd6a121fe4e2b8f5b936c4014ce8388b4e))
* **frontend:** let plugins contribute dashboard pages without loading plugin script ([#139](https://github.com/OneLiteFeatherNET/ingot/issues/139)) ([b2da66a](https://github.com/OneLiteFeatherNET/ingot/commit/b2da66a4c05957d07dff266dc924c95b83b7fbb6))
* **frontend:** let plugins ship a stylesheet for the dashboard ([#192](https://github.com/OneLiteFeatherNET/ingot/issues/192)) ([8ec0349](https://github.com/OneLiteFeatherNET/ingot/commit/8ec034968145cdcf4afe06155daa14d59f839515))
* **identity:** add a read endpoint for the caller's own access summary ([#226](https://github.com/OneLiteFeatherNET/ingot/issues/226)) ([a3f7136](https://github.com/OneLiteFeatherNET/ingot/commit/a3f7136ac8a266c4e8bda5dcb46f85dc355a9196))
* **identity:** scope permission nodes to maven artifacts ([#32](https://github.com/OneLiteFeatherNET/ingot/issues/32)) ([1c2373c](https://github.com/OneLiteFeatherNET/ingot/commit/1c2373c0603e0aff2746f51e41e2fe811fd5aad7))
* **javadocs:** read ingot.javadocs.max-entry-bytes override ([c27d0c9](https://github.com/OneLiteFeatherNET/ingot/commit/c27d0c9404846beb3bc5588d7af8a12f194303e6))
* **maven:** add hosted, proxy and group repository types ([#38](https://github.com/OneLiteFeatherNET/ingot/issues/38)) ([19189c7](https://github.com/OneLiteFeatherNET/ingot/commit/19189c751db06d2eea4718315094f3852c9cdcbd))
* **maven:** add repository version policy, read-only flag and write policy view ([#143](https://github.com/OneLiteFeatherNET/ingot/issues/143)) ([c1fa240](https://github.com/OneLiteFeatherNET/ingot/commit/c1fa2400c33caec2c084f1e6e8381e46669e88fb))
* **maven:** list the repositories a group references ([#27](https://github.com/OneLiteFeatherNET/ingot/issues/27)) ([cf5a690](https://github.com/OneLiteFeatherNET/ingot/commit/cf5a690cf38eb5e7243831bcee5458a0a56943f2))
* **maven:** refuse proxy members for protected group prefixes ([#136](https://github.com/OneLiteFeatherNET/ingot/issues/136)) ([c8ae32b](https://github.com/OneLiteFeatherNET/ingot/commit/c8ae32b894c2096b4a7f89977beb0e0db87b8434))
* **maven:** verify mirrored artifacts against upstream checksums ([#118](https://github.com/OneLiteFeatherNET/ingot/issues/118)) ([afa3291](https://github.com/OneLiteFeatherNET/ingot/commit/afa3291d758f1d4fd5fe6c0a3eb2fda62aa58593))
* **maven:** verify uploaded checksum files against the stored artifact ([#137](https://github.com/OneLiteFeatherNET/ingot/issues/137)) ([db0717b](https://github.com/OneLiteFeatherNET/ingot/commit/db0717b2bd94f53ddd11c6aa9370ca93ba4ff247))
* **metrics:** add request histograms and health gauges behind a flag ([#114](https://github.com/OneLiteFeatherNET/ingot/issues/114)) ([c4e56ce](https://github.com/OneLiteFeatherNET/ingot/commit/c4e56cea6227711b81ab80a78b37f63bd627e451))
* **migration:** add nexus import for maven repositories ([#144](https://github.com/OneLiteFeatherNET/ingot/issues/144)) ([aad2817](https://github.com/OneLiteFeatherNET/ingot/commit/aad2817469e9621f4bbf99f7ed9729e3230aecc6))
* **plugin:** install plugins from a catalogue in a maven repository ([#224](https://github.com/OneLiteFeatherNET/ingot/issues/224)) ([a383b51](https://github.com/OneLiteFeatherNET/ingot/commit/a383b512a6bbd8024d513d114109e86923d41868))
* **repository:** add repository formats with maven2 as the default ([#140](https://github.com/OneLiteFeatherNET/ingot/issues/140)) ([5bee879](https://github.com/OneLiteFeatherNET/ingot/commit/5bee879f604677fd3b2e10319308b00af6134e98))
* **search:** add an opt-in opensearch backend plugin ([#202](https://github.com/OneLiteFeatherNET/ingot/issues/202)) ([5abfe16](https://github.com/OneLiteFeatherNET/ingot/commit/5abfe1672345367c02ac4d437d622bcfe060e2ac))
* **search:** let plugins register search backends and report their health ([#200](https://github.com/OneLiteFeatherNET/ingot/issues/200)) ([b5fe2b5](https://github.com/OneLiteFeatherNET/ingot/commit/b5fe2b5cea7aa488cffa92f45c879709bdb9ad44))
* **search:** match words of the coordinates in artifact search ([#193](https://github.com/OneLiteFeatherNET/ingot/issues/193)) ([2f90ce5](https://github.com/OneLiteFeatherNET/ingot/commit/2f90ce5d35e5a03d7685d0d25cdb88b1997c4fb1))
* **search:** reset the rebuild cursor when a backend index is replaced ([#229](https://github.com/OneLiteFeatherNET/ingot/issues/229)) ([45d359d](https://github.com/OneLiteFeatherNET/ingot/commit/45d359de621ef8ae714a2a5743e4aa2baedbf150))
* **search:** search artifacts across repositories ([#35](https://github.com/OneLiteFeatherNET/ingot/issues/35)) ([402ae39](https://github.com/OneLiteFeatherNET/ingot/commit/402ae39466920e70eae54d4f492304cd769ce057))
* **security:** refuse cross-site cookie requests to the api ([#98](https://github.com/OneLiteFeatherNET/ingot/issues/98)) ([642a61f](https://github.com/OneLiteFeatherNET/ingot/commit/642a61f5dd87d6d3970f3633baf5d28fb6dc95d4))
* **settings:** encrypt secret values of shared settings at rest ([#164](https://github.com/OneLiteFeatherNET/ingot/issues/164)) ([d674b9a](https://github.com/OneLiteFeatherNET/ingot/commit/d674b9a08c364c078b76365285ba5a5162250494))
* **status:** add environment switch for the remote version check ([#130](https://github.com/OneLiteFeatherNET/ingot/issues/130)) ([73ba882](https://github.com/OneLiteFeatherNET/ingot/commit/73ba8822d49b3bf1c85bcf052d66eddd0276ab41))
* **status:** add readiness endpoint at /api/status/ready ([#97](https://github.com/OneLiteFeatherNET/ingot/issues/97)) ([3d0bcb5](https://github.com/OneLiteFeatherNET/ingot/commit/3d0bcb5405eeb295017051784ddb4713c03285f6))
* **storage:** let plugins register storage providers ([#222](https://github.com/OneLiteFeatherNET/ingot/issues/222)) ([4bcb05f](https://github.com/OneLiteFeatherNET/ingot/commit/4bcb05f60e30cb8d049377d0a9d9211d8f39d76f))
* **ui:** add a repositories administration page and regroup the navigation ([#145](https://github.com/OneLiteFeatherNET/ingot/issues/145)) ([dcd8294](https://github.com/OneLiteFeatherNET/ingot/commit/dcd8294a69ad1eba8dbbfd04d0108b932e8ea0bd))
* **ui:** add a responsive administration layout with an actions menu ([#172](https://github.com/OneLiteFeatherNET/ingot/issues/172)) ([a9b7573](https://github.com/OneLiteFeatherNET/ingot/commit/a9b7573bbe71ac48b28cad97973b9edba6d837f2))
* **ui:** add the nexus import wizard ([#147](https://github.com/OneLiteFeatherNET/ingot/issues/147)) ([c77b878](https://github.com/OneLiteFeatherNET/ingot/commit/c77b878a0aba918064e79ebbcfee739ec96f6ce5))
* **ui:** edit and check artifact permission nodes ([#33](https://github.com/OneLiteFeatherNET/ingot/issues/33)) ([5e37766](https://github.com/OneLiteFeatherNET/ingot/commit/5e37766f2fa78368350bb36f65fc5213fdc89287))
* **ui:** group and name the administration pages by operator task ([#204](https://github.com/OneLiteFeatherNET/ingot/issues/204)) ([6ea55fe](https://github.com/OneLiteFeatherNET/ingot/commit/6ea55fe05d96186bc3cac74e5ef21f29ecbe1203))
* **ui:** group the navigation and add side navigation for access and settings ([#82](https://github.com/OneLiteFeatherNET/ingot/issues/82)) ([8caa338](https://github.com/OneLiteFeatherNET/ingot/commit/8caa338aaaf4ad44dad8fb1cc820bcd4644e09ef))
* **ui:** keep the administration tree in reach at every width ([#207](https://github.com/OneLiteFeatherNET/ingot/issues/207)) ([436e6bc](https://github.com/OneLiteFeatherNET/ingot/commit/436e6bc6d93a89173bc070a38ef80ba5ca989827))
* **ui:** manage hosted, proxy and group repositories ([#39](https://github.com/OneLiteFeatherNET/ingot/issues/39)) ([5f727c2](https://github.com/OneLiteFeatherNET/ingot/commit/5f727c26c85cc8f4b37b36e836163674ac954057))
* **ui:** move authentication settings under access ([#68](https://github.com/OneLiteFeatherNET/ingot/issues/68)) ([298e5e3](https://github.com/OneLiteFeatherNET/ingot/commit/298e5e31a88674de945381b9ecd4648677905431))
* **ui:** move the traffic chart into an analytics page ([#69](https://github.com/OneLiteFeatherNET/ingot/issues/69)) ([b2d8640](https://github.com/OneLiteFeatherNET/ingot/commit/b2d8640202d6b4b3b20fda933314f887ddbc64e3))
* **ui:** replace the settings card grid with an administration overview ([#206](https://github.com/OneLiteFeatherNET/ingot/issues/206)) ([1f34a26](https://github.com/OneLiteFeatherNET/ingot/commit/1f34a266cc7d9db7d3f5979767339335c357c288))
* **ui:** retire the maven settings page in favour of repositories ([#173](https://github.com/OneLiteFeatherNET/ingot/issues/173)) ([769cc3a](https://github.com/OneLiteFeatherNET/ingot/commit/769cc3a17c00beebacc1de81cb64208c08e6e62c))
* **ui:** use a steel blue default colour for the dashboard ([#180](https://github.com/OneLiteFeatherNET/ingot/issues/180)) ([f5ef44a](https://github.com/OneLiteFeatherNET/ingot/commit/f5ef44aa5a83d10d8a5acbd180969122748d905a))
* **ui:** use the full width for lists and two grids on wide screens ([#196](https://github.com/OneLiteFeatherNET/ingot/issues/196)) ([6159218](https://github.com/OneLiteFeatherNET/ingot/commit/61592187d5175c200e7d9d85805eac98c1f855ea))
* **web:** drain in-flight requests and report draining on readiness ([#107](https://github.com/OneLiteFeatherNET/ingot/issues/107)) ([fa0ab50](https://github.com/OneLiteFeatherNET/ingot/commit/fa0ab50b648c5d7da72dc5ee5f77fddcd7745ba5))
* **web:** shed requests above an opt-in concurrency limit ([#115](https://github.com/OneLiteFeatherNET/ingot/issues/115)) ([0415672](https://github.com/OneLiteFeatherNET/ingot/commit/04156722dcfe21033460203a2f85f06adb79a764))


### Bug Fixes

* **api:** complete the served openapi document ([#134](https://github.com/OneLiteFeatherNET/ingot/issues/134)) ([72fc406](https://github.com/OneLiteFeatherNET/ingot/commit/72fc406bfe7d4cb5d020bb3c922eb46c7fca0a3e))
* **chart:** keep the upstream resource names when upgrading from the reposilite chart ([#183](https://github.com/OneLiteFeatherNET/ingot/issues/183)) ([f879900](https://github.com/OneLiteFeatherNET/ingot/commit/f8799009e9ba820adc41e3ef0145c3e42c6efa2d))
* **configuration:** log unreadable shared and local settings ([#96](https://github.com/OneLiteFeatherNET/ingot/issues/96)) ([4e4489d](https://github.com/OneLiteFeatherNET/ingot/commit/4e4489d344d1424dfee550ada658384674b3a738))
* **console:** connect the remote console with a session login ([#29](https://github.com/OneLiteFeatherNET/ingot/issues/29)) ([03f687c](https://github.com/OneLiteFeatherNET/ingot/commit/03f687c060d73ce77077e70876c8b154d09905cf))
* **console:** keep the console running after a malformed command ([76b74eb](https://github.com/OneLiteFeatherNET/ingot/commit/76b74eb3dd11c0d6fbf811e8d0c58dc57e15f056))
* **console:** stop failing log subscribers from escalating errors ([#34](https://github.com/OneLiteFeatherNET/ingot/issues/34)) ([62b8bdf](https://github.com/OneLiteFeatherNET/ingot/commit/62b8bdf45eebd371acb487f13679e1050cca6584))
* **database:** serialise schema initialisation across replicas ([#103](https://github.com/OneLiteFeatherNET/ingot/issues/103)) ([fbe48d6](https://github.com/OneLiteFeatherNET/ingot/commit/fbe48d6e75691b2431b7b849c5e818425fdc0fdb))
* **deps:** update fast-uri in the frontend ([#66](https://github.com/OneLiteFeatherNET/ingot/issues/66)) ([da58c74](https://github.com/OneLiteFeatherNET/ingot/commit/da58c743606c4831ed734f086fabae1a4c83a7ba))
* **deps:** update guava and the mariadb connector ([#60](https://github.com/OneLiteFeatherNET/ingot/issues/60)) ([2e6f891](https://github.com/OneLiteFeatherNET/ingot/commit/2e6f891eb5fb537dd9b8edc166f6103c0f1a999c))
* **deps:** update markdown-it and follow-redirects in the frontend ([#54](https://github.com/OneLiteFeatherNET/ingot/issues/54)) ([90c2bac](https://github.com/OneLiteFeatherNET/ingot/commit/90c2bac3daa9f37eac944c06630170e8118fda37))
* **deps:** update nanoid and source-map-js in the site lockfile ([2b2eda0](https://github.com/OneLiteFeatherNET/ingot/commit/2b2eda03ba02a4b77ecdcf2ee90a9f0fb2f6b0b9))
* **deps:** update vite in the frontend ([#56](https://github.com/OneLiteFeatherNET/ingot/issues/56)) ([afee6c3](https://github.com/OneLiteFeatherNET/ingot/commit/afee6c38ac941a52a19bae4bc4a160d359779575))
* **docker:** serve every dashboard route from the dashboard image ([#31](https://github.com/OneLiteFeatherNET/ingot/issues/31)) ([7ef3171](https://github.com/OneLiteFeatherNET/ingot/commit/7ef3171ae2e5b2c9811107d47d93c80670294088))
* **docker:** upgrade libexpat in the dashboard image ([#49](https://github.com/OneLiteFeatherNET/ingot/issues/49)) ([a71d395](https://github.com/OneLiteFeatherNET/ingot/commit/a71d395372f0acd445d6e12415a681c737c652da))
* **docker:** upgrade vulnerable packages in both images ([#61](https://github.com/OneLiteFeatherNET/ingot/issues/61)) ([a8ae6c2](https://github.com/OneLiteFeatherNET/ingot/commit/a8ae6c26ce657bc299a91f910162353cee359433))
* **frontend:** add bypass blocks, route focus and dashboard caching headers ([#99](https://github.com/OneLiteFeatherNET/ingot/issues/99)) ([1542ef0](https://github.com/OneLiteFeatherNET/ingot/commit/1542ef07ae5d4019a2df796a95d27925a4bbb312))
* **frontend:** read api instants as epoch seconds ([#25](https://github.com/OneLiteFeatherNET/ingot/issues/25)) ([5e2eab4](https://github.com/OneLiteFeatherNET/ingot/commit/5e2eab4d9a3120f6eae1028527be00ae778b3b60))
* **frontend:** stop padding dashboard assets with zero bytes ([#23](https://github.com/OneLiteFeatherNET/ingot/issues/23)) ([91a9079](https://github.com/OneLiteFeatherNET/ingot/commit/91a907903d0943558e9f0b7904fd812001cae934))
* **frontend:** tidy the details card and breadcrumb on the browse page ([#28](https://github.com/OneLiteFeatherNET/ingot/issues/28)) ([fa0c8d1](https://github.com/OneLiteFeatherNET/ingot/commit/fa0c8d1a11c7f3241dfef5d1c64c571365329e56))
* **http:** add vary accept and no-cache to error responses ([#93](https://github.com/OneLiteFeatherNET/ingot/issues/93)) ([c944be4](https://github.com/OneLiteFeatherNET/ingot/commit/c944be4291152dc6648d70d6fff7413a87582ec5))
* **identity:** enforce admin area permission nodes ([#41](https://github.com/OneLiteFeatherNET/ingot/issues/41)) ([fc8b45a](https://github.com/OneLiteFeatherNET/ingot/commit/fc8b45a15940b97a97b82169d8fd55fb09f44448))
* **identity:** report instance admin rights in the session details ([#215](https://github.com/OneLiteFeatherNET/ingot/issues/215)) ([ef5fb2c](https://github.com/OneLiteFeatherNET/ingot/commit/ef5fb2c2e6c97e1ea66e1b3794d96eec41320999))
* **javadocs:** evict javadoc caches on every replica ([#119](https://github.com/OneLiteFeatherNET/ingot/issues/119)) ([21ff958](https://github.com/OneLiteFeatherNET/ingot/commit/21ff958ed89c9387f7fd442852ca1d6c2f142550))
* keep mirror credentials when saving repository settings ([#47](https://github.com/OneLiteFeatherNET/ingot/issues/47)) ([0b92ee6](https://github.com/OneLiteFeatherNET/ingot/commit/0b92ee6cd896ff9d3b698dfa54ad921d922db737))
* keep the session when browsing a group repository ([#46](https://github.com/OneLiteFeatherNET/ingot/issues/46)) ([5cba28a](https://github.com/OneLiteFeatherNET/ingot/commit/5cba28a0562b84042d3167f1180a623630d49e3f))
* **logging:** mute exposed statement logs again after the 1.x package rename ([#194](https://github.com/OneLiteFeatherNET/ingot/issues/194)) ([83b1c8f](https://github.com/OneLiteFeatherNET/ingot/commit/83b1c8f26c905385a65ed7dd77ae6fabbfce3a0c))
* **login:** accept legacy ldap credentials in the login dialog ([193ba59](https://github.com/OneLiteFeatherNET/ingot/commit/193ba59f5493d52703fd135d418471e4b9fa45c6))
* **maven:** allow sigstore bundles in the default mirror extensions ([#92](https://github.com/OneLiteFeatherNET/ingot/issues/92)) ([7d42b90](https://github.com/OneLiteFeatherNET/ingot/commit/7d42b907e37542f61e88bb4944fba8a445ec355a))
* **maven:** fetch mirrored files on the requesting thread ([#64](https://github.com/OneLiteFeatherNET/ingot/issues/64)) ([fba7038](https://github.com/OneLiteFeatherNET/ingot/commit/fba7038e2d6eb073c92537cfa4e16f556dee354f))
* **maven:** let a repository with an empty entry take a type ([#45](https://github.com/OneLiteFeatherNET/ingot/issues/45)) ([00ee5ab](https://github.com/OneLiteFeatherNET/ingot/commit/00ee5ab151b61639d2dff79dd766a6ae192c3b3b))
* **maven:** send private cache-control for access-controlled repository content ([#91](https://github.com/OneLiteFeatherNET/ingot/issues/91)) ([053aa15](https://github.com/OneLiteFeatherNET/ingot/commit/053aa1546cea3dba1100323f9ec979ef72c061b8))
* **maven:** serialise metadata updates across instances ([#110](https://github.com/OneLiteFeatherNET/ingot/issues/110)) ([6d9eef5](https://github.com/OneLiteFeatherNET/ingot/commit/6d9eef5f2a8d4992979c530e42784ab7e714a005))
* **scheduling:** expire and take over cleanup and audit job leases ([#106](https://github.com/OneLiteFeatherNET/ingot/issues/106)) ([9b6cc2f](https://github.com/OneLiteFeatherNET/ingot/commit/9b6cc2fcc31f9abd32ed3c7434234ebbf40df35b))
* **search:** report the opensearch index uuid as its identity ([#235](https://github.com/OneLiteFeatherNET/ingot/issues/235)) ([1890126](https://github.com/OneLiteFeatherNET/ingot/commit/189012696250eb074af6257032685069edf1af71))
* **search:** skip rows the database refuses to key in the token sweep ([#216](https://github.com/OneLiteFeatherNET/ingot/issues/216)) ([2bc0f16](https://github.com/OneLiteFeatherNET/ingot/commit/2bc0f16c9a8fbb01edcd24653f009611ab2e4566))
* **security:** harden http authentication, headers and error bodies ([#81](https://github.com/OneLiteFeatherNET/ingot/issues/81)) ([0bb7399](https://github.com/OneLiteFeatherNET/ingot/commit/0bb73997efbde7291bb9a6f49131e9b462877377))
* **settings:** accept weak etags from compressing proxies ([#44](https://github.com/OneLiteFeatherNET/ingot/issues/44)) ([44e1334](https://github.com/OneLiteFeatherNET/ingot/commit/44e1334490fb5188b2c600671a1e5d12c1683a5c))
* **settings:** render the authentication settings form ([#24](https://github.com/OneLiteFeatherNET/ingot/issues/24)) ([06d6e1b](https://github.com/OneLiteFeatherNET/ingot/commit/06d6e1b0f814008afffeb61e79678f571cc3e486))
* **statistics:** flush resolved request counts on shutdown ([#104](https://github.com/OneLiteFeatherNET/ingot/issues/104)) ([1edc022](https://github.com/OneLiteFeatherNET/ingot/commit/1edc0228605006d70f88cdfb7c81769c6c7437ab))
* **statistics:** honour the limit of the phrase statistics endpoint ([#94](https://github.com/OneLiteFeatherNET/ingot/issues/94)) ([c0235c1](https://github.com/OneLiteFeatherNET/ingot/commit/c0235c15017d4f337e3db37ebd983a2f3d906e7a))
* **storage:** answer s3 failures with 5xx instead of not found ([#55](https://github.com/OneLiteFeatherNET/ingot/issues/55)) ([857e42b](https://github.com/OneLiteFeatherNET/ingot/commit/857e42b20b473ad4419f2132365cf43866559f8c))
* **storage:** count existing files against the storage quota ([#51](https://github.com/OneLiteFeatherNET/ingot/issues/51)) ([2ea7f20](https://github.com/OneLiteFeatherNET/ingot/commit/2ea7f2028b5cfed45dcebceb7bd38af7698f9674)), closes [#15](https://github.com/OneLiteFeatherNET/ingot/issues/15)
* **storage:** count stored bytes of s3 repositories ([#26](https://github.com/OneLiteFeatherNET/ingot/issues/26)) ([4d8aedc](https://github.com/OneLiteFeatherNET/ingot/commit/4d8aedc11857e6622768cd9f6545c9dd7d48399a))
* **storage:** keep unchanged repositories live across settings reloads ([#43](https://github.com/OneLiteFeatherNET/ingot/issues/43)) ([ca5763f](https://github.com/OneLiteFeatherNET/ingot/commit/ca5763f06b96c04d91248249ecf58e91ab9678fb))
* **storage:** move uploads into place atomically ([#105](https://github.com/OneLiteFeatherNET/ingot/issues/105)) ([c5561cc](https://github.com/OneLiteFeatherNET/ingot/commit/c5561cc8e44f4dd0c452afc12fb228b3a0dcb3d2))
* **storage:** read file details under the file lock ([#53](https://github.com/OneLiteFeatherNET/ingot/issues/53)) ([7b3466c](https://github.com/OneLiteFeatherNET/ingot/commit/7b3466c085438925508226b20fdb80a567969746))
* **storage:** skip caching mutable S3 metadata and add retry jitter ([#95](https://github.com/OneLiteFeatherNET/ingot/issues/95)) ([07b4421](https://github.com/OneLiteFeatherNET/ingot/commit/07b4421a0dd99f21364252c367593d193cf9862c))
* **storage:** warn when a repository walk cannot read its root ([#30](https://github.com/OneLiteFeatherNET/ingot/issues/30)) ([2cf1d05](https://github.com/OneLiteFeatherNET/ingot/commit/2cf1d0532498985c26d24e77c1cea00d5bc5f9f4))
* **ui:** align the navigation rail and top bar and group the account actions ([#189](https://github.com/OneLiteFeatherNET/ingot/issues/189)) ([2df1130](https://github.com/OneLiteFeatherNET/ingot/commit/2df113059b1aecb5c0c193daffd443a728ae361f))
* **ui:** draw the administration tree on the settings hub ([#174](https://github.com/OneLiteFeatherNET/ingot/issues/174)) ([7539b9b](https://github.com/OneLiteFeatherNET/ingot/commit/7539b9b37fc3d591c0c09435c6f60cb61e924497))
* **ui:** drop the authentication tab from settings ([#71](https://github.com/OneLiteFeatherNET/ingot/issues/71)) ([f7a3973](https://github.com/OneLiteFeatherNET/ingot/commit/f7a39739cc8f23dd685a54149be8491b2d405179))
* **ui:** drop the brand rule under the top bar ([#203](https://github.com/OneLiteFeatherNET/ingot/issues/203)) ([751fe81](https://github.com/OneLiteFeatherNET/ingot/commit/751fe819bb8ec7b6bd2ebeb669a86a74d5b4c16b))
* **ui:** keep instance-wide and repeated controls off the repository page ([#230](https://github.com/OneLiteFeatherNET/ingot/issues/230)) ([4726517](https://github.com/OneLiteFeatherNET/ingot/commit/4726517fbf69a1f03ccd3e9dae250c7760f9e655))
* **ui:** keep long addresses inside the audit actor column ([#36](https://github.com/OneLiteFeatherNET/ingot/issues/36)) ([156efe0](https://github.com/OneLiteFeatherNET/ingot/commit/156efe052ead599a482dc6a4b989b1cefaeedfb5))
* **ui:** keep the administration header readable on detail pages and narrow screens ([#218](https://github.com/OneLiteFeatherNET/ingot/issues/218)) ([2830ad6](https://github.com/OneLiteFeatherNET/ingot/commit/2830ad6c7082fab1fe3e173e06979cf1a2018d9b))
* **ui:** keep the administration navigation the same on every page ([#158](https://github.com/OneLiteFeatherNET/ingot/issues/158)) ([c0c747f](https://github.com/OneLiteFeatherNET/ingot/commit/c0c747f291f34a005292b33620439e1cd202bd85))
* **ui:** keep the focus in the sign in field the visitor chose ([#175](https://github.com/OneLiteFeatherNET/ingot/issues/175)) ([57a39b1](https://github.com/OneLiteFeatherNET/ingot/commit/57a39b1ac75d12c749ff6d999227ef116de56e03))
* **ui:** keep the navigation within a 320 px wide screen ([#191](https://github.com/OneLiteFeatherNET/ingot/issues/191)) ([d5ef366](https://github.com/OneLiteFeatherNET/ingot/commit/d5ef366cf73be6c57e3c4d8deb4604caaf7d9598))
* **ui:** keep the row actions menu open while the page scrolls ([#185](https://github.com/OneLiteFeatherNET/ingot/issues/185)) ([2baa969](https://github.com/OneLiteFeatherNET/ingot/commit/2baa969c3545601916eb64c9c9837841899716dd))
* **ui:** make the browse snippet readable and scope the repository filter ([#165](https://github.com/OneLiteFeatherNET/ingot/issues/165)) ([9acaf3a](https://github.com/OneLiteFeatherNET/ingot/commit/9acaf3ab094bab870720a4953f59454dd1da7081))
* **ui:** move the read-only switch and correct the dashboard figures ([#162](https://github.com/OneLiteFeatherNET/ingot/issues/162)) ([4bd9702](https://github.com/OneLiteFeatherNET/ingot/commit/4bd97020535de8daa08265ccb9b3a4da63bc4057))
* **ui:** send the access token with the plugin and support requests ([#198](https://github.com/OneLiteFeatherNET/ingot/issues/198)) ([77fa4d8](https://github.com/OneLiteFeatherNET/ingot/commit/77fa4d8fb41127856637e2aa5bca449e88f4051e))
* **ui:** set the administration group headings apart from their entries ([#212](https://github.com/OneLiteFeatherNET/ingot/issues/212)) ([56d38ca](https://github.com/OneLiteFeatherNET/ingot/commit/56d38ca1c3058364b355e99e35de39b90e32ede8))
* **ui:** show each detail page title and way back only once ([#228](https://github.com/OneLiteFeatherNET/ingot/issues/228)) ([50070a6](https://github.com/OneLiteFeatherNET/ingot/commit/50070a6f9811f0b8087da18c2937a4c80eb4cc8e))
* **ui:** show machine accounts to the users area ([#74](https://github.com/OneLiteFeatherNET/ingot/issues/74)) ([71a7c12](https://github.com/OneLiteFeatherNET/ingot/commit/71a7c12c8b0ef8b7f4c14d2bf25c5ad36d807b45))
* **ui:** show the right message when the first admin setup fails ([#40](https://github.com/OneLiteFeatherNET/ingot/issues/40)) ([4856ed7](https://github.com/OneLiteFeatherNET/ingot/commit/4856ed700f8d4ee2988e8be2d89d16edd2e82268))
* **ui:** theme select fields and stop their jump on focus ([#48](https://github.com/OneLiteFeatherNET/ingot/issues/48)) ([a1d7fbf](https://github.com/OneLiteFeatherNET/ingot/commit/a1d7fbfe53cc5fe6f9165ef2e84dfe1da8b2f4bc))
* **ui:** wrap the browse snippet and name the repository filter in the browser ([#184](https://github.com/OneLiteFeatherNET/ingot/issues/184)) ([1886d99](https://github.com/OneLiteFeatherNET/ingot/commit/1886d99442b8a58a5f475cfffeda59d937e725d2))


### Performance Improvements

* **storage:** stream s3 uploads as multipart instead of a temp file ([#112](https://github.com/OneLiteFeatherNET/ingot/issues/112)) ([8cd73db](https://github.com/OneLiteFeatherNET/ingot/commit/8cd73db03fb687a7fd8802051898a26702f53eec))

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
