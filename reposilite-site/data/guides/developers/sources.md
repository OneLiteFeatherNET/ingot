---
id: sources
title: Sources
---

Ingot lives in a single repository:

* [OneLiteFeatherNET / Ingot](https://github.com/OneLiteFeatherNET/ingot) - Main project repository
  * [Backend](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-backend) - Main sources of Ingot
  * [Frontend](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-frontend) - Dashboard implementation in [Vue 3](https://vuejs.org/)
  * [Plugins](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-plugins) - Official extensions to Ingot
  * [Website](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-site) - These guides, in [Next.js](https://nextjs.org/) ([React](https://reactjs.org/))

The module directories are still named `reposilite-*`, and the Kotlin and Java packages are still
`com.reposilite.*`. That is deliberate: it keeps merges with upstream mechanical and lets an
existing Reposilite plugin build against Ingot by changing its dependency's group id to
`net.onelitefeather.ingot` and nothing else.

### Upstream

Ingot is a fork of [Reposilite](https://github.com/dzikoysk/reposilite) by
[@dzikoysk](https://github.com/dzikoysk) and contributors, licensed under the Apache License 2.0.

### Libraries

Projects Ingot is built on, several of them written for Reposilite:

* [dzikoysk / CDN](https://github.com/dzikoysk/cdn) - Configuration library used by Ingot to handle the `.cdn` format
* [reposilite-playground / Javalin OpenApi](https://github.com/reposilite-playground/javalin-openapi) - OpenApi plugin for Javalin built on top of annotation processing, with support for [Swagger](https://swagger.io/) and [ReDoc](https://github.com/Redocly/redoc)
* [reposilite-playground / Javalin RFCs](https://github.com/reposilite-playground/javalin-rfcs) - Set of extension methods, alternative routing and coroutines plugin for Javalin
* [reposilite-playground / Journalist](https://github.com/reposilite-playground/journalist) - Tiny logging abstraction that provides non-static loggers, with support for SLF4J
* [panda-lang / Expressible](https://github.com/panda-lang/expressible) - Dependency free utility library for Java and Kotlin, dedicated for functional codebases that require enhanced response handling
* [javalin / Javalin](https://github.com/javalin/javalin) - Simple web framework behind Ingot
* [JetBrains / Exposed](https://github.com/JetBrains/Exposed) - SQL framework used for the database layer

### State of sources

Reposilite 1.x and 2.x were written in Java, but in 3.x the time has come for Kotlin.
The decision to move to Kotlin was caused by several factors:

1. Sources consistency - Main sources, unit & integration tests and build script files are fully written in Kotlin.
   We're glad we could finally get rid of Groovy.
2. Extensibility - Ingot is built on top of [Javalin](https://javalin.io/), web framework already written in Kotlin. 
   It's relatively small library, so we often need to extend it with a bit of custom methods.
   To avoid mess created by static utility methods, we're extending base objects with extension functions.
3. Nullability - A possibility to replace _Optionals_ with nullable types and overall better support on language level reduces complexity.
4. Functional programming - Suited to FP-like codebase on syntax & std level.
5. Relatively easy - There is no big difference between Kotlin and Java,
   every open-minded Java developer should be able to write in Kotlin within an hour.

If you're a Java developer that never had contact with Kotlin, check our [Kotlin guide](/guide/kotlin) in the context of Ingot sources!
