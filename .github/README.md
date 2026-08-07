<div align="center">
 <h1>Ingot</h1>
 <p><strong>A self-hosted Maven repository manager for the JVM ecosystem.</strong></p>
 <p>
  <a href="https://github.com/OneLiteFeatherNET/ingot/blob/main/LICENSE">
   <img alt="License: Apache-2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" />
  </a>
 </p>
</div>

Ingot stores, serves and proxies build artifacts. An ingot is a cast bar of metal: a
standardised, immutable, valuable unit that gets stored and passed on. A build artifact
is the same thing, so that is the name.

It speaks the plain Maven HTTP protocol, which means `mvn deploy` and `gradle publish`
work against it without a custom plugin or client. It is a single JVM process with a
small footprint, aimed at teams who want a repository manager they can run on a cheap
VM instead of a dedicated server.

Ingot is developed by [OneLiteFeather](https://github.com/OneLiteFeatherNET) and is free
and open source software under the Apache License 2.0.

## Relationship to Reposilite

**Ingot is a fork of [Reposilite](https://github.com/dzikoysk/reposilite) by
[dzikoysk](https://github.com/dzikoysk) and contributors, licensed under the
Apache License 2.0.** The upstream project did the original work that Ingot builds on,
and the original copyright notices are retained throughout the source tree.

Ingot is a separate product with its own roadmap, releases and maintainers. It is **not
affiliated with, endorsed by, or sponsored by** the upstream Reposilite project or its
authors. "Reposilite" is the name of the upstream project and is not used as part of the
Ingot product name.

See [NOTICE](https://github.com/OneLiteFeatherNET/ingot/blob/main/NOTICE) for the
full attribution and the statement of changes required by section 4(b) of the license.

> **Status:** the product is branded as Ingot and the build publishes as
> `net.onelitefeather.ingot`. The module directories and the Java packages stay
> `reposilite-*` and `com.reposilite.*` on purpose, so existing Reposilite plugins keep
> compiling. No Ingot release has been cut yet, so the quickstart below builds from
> source.

## Features

Artifact hosting

- Hosted `releases`, `snapshots` and `private` repositories out of the box, with per
  repository visibility (public, hidden, private)
- Deployment over the standard Maven HTTP protocol, so `mvn deploy`, `gradle publish`
  and sbt work unchanged
- `maven-metadata.xml` maintained by the server, optional redeployment of the same
  version, and automatic pruning of superseded snapshot builds
- MD5 and SHA-1 checksums generated on deploy; the bundled checksum plugin adds
  SHA-256 and SHA-512 on demand

Proxying and caching

- Mirror remote repositories, optionally storing fetched artifacts locally so builds
  keep working when the upstream is unreachable
- Per mirror allow lists for groups and file extensions, connection timeouts,
  Basic or custom header authentication, and HTTP or SOCKS proxy support

Storage and persistence

- Local filesystem storage with disk quotas (fixed size or percentage)
- S3 compatible object storage with a configurable endpoint, so AWS S3, MinIO and
  similar services work
- SQLite (default) and MariaDB for metadata and tokens; MySQL, PostgreSQL and H2 are
  present but marked experimental in the configuration

Access control

- Access tokens with route based read and write permissions, scoped by path prefix
- Persistent and temporary tokens, plus a manager permission for administrative access
- LDAP authentication, optional brute force protection, and built in TLS

Web interface

- File browser with upload and delete, breadcrumb navigation and ready to paste
  Maven, Gradle and sbt snippets
- Schema driven settings editor, token management, and a live server console
- Dashboard with instance status and resolved request charts

API and observability

- REST API with a generated OpenAPI scheme; the bundled Swagger plugin serves a UI for it
- Latest version resolution endpoints and an SVG version badge endpoint for READMEs
- Resolved artifact statistics and instance health endpoints; the bundled Prometheus
  plugin exposes metrics
- Browsable Javadoc rendered directly from deployed `-javadoc.jar` files

Extensibility

- Plugin system loaded from a plugins directory, with dependency ordering
- Bundled plugins: checksum, Prometheus, Swagger, Groovy scripting, and a migration
  plugin that imports Reposilite 2.x tokens

## Quickstart

Ingot needs Java 17 or newer. The bundled Docker Compose example runs the server with a
64 MB heap, which is enough for a small team.

### From source

```bash
git clone https://github.com/OneLiteFeatherNET/ingot.git
cd ingot
./gradlew :reposilite-backend:shadowJar

java -Xmx64M -jar reposilite-backend/build/libs/ingot-*.jar
```

The server listens on port `8080` by default. A fresh instance has no access tokens, so
create one either with the `--token name:secret` flag on startup or with the
`token-generate` command in the server console, then open <http://localhost:8080> and
sign in with it.

### With Docker

The repository ships a `Dockerfile` and a `docker-compose.yml`. The Compose file reads
its settings from `.env` (port, heap size, JVM and server arguments) and stores data in a
named volume.

```bash
docker compose up -d
```

Released images go to `ghcr.io/onelitefeathernet/ingot`, with a `nightly` tag rebuilt on
every push to `main`. `INGOT_OPTS` passes startup parameters to the server;
`REPOSILITE_OPTS` is still read as a fallback so an existing manifest starts unchanged.

### Publishing to it

```xml
<distributionManagement>
  <repository>
    <id>ingot</id>
    <url>https://your-host.example/releases</url>
  </repository>
</distributionManagement>
```

Add the matching token as a `<server>` entry in your `settings.xml`, then run
`mvn deploy`. The web interface generates the equivalent snippets for Gradle and sbt.

## Documentation

<!-- TODO(onelitefeather): a published documentation site. The `reposilite-site` module
     builds one, but nothing deploys it yet. -->

Configuration, deployment and authentication guides live as Markdown in the
[`reposilite-site/data/guides`](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-site/data/guides)
directory of this repository. Guides that describe a distribution channel Ingot does not
have yet, such as the Arch package and the Helm chart, say so at the top.

## Contributing

Bug reports, feature requests and pull requests are welcome. Start with
[CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).
Security issues should go through the [security policy](SECURITY.md) rather than a public
issue.

<!-- TODO(onelitefeather): community link for user questions and discussion. -->

### Building and running locally

```bash
# Backend only, with hot classpath from Gradle
./gradlew run

# Frontend dev server
cd reposilite-frontend && npm install && npm run full

# Full test suite
./gradlew test
```

The backend is Kotlin on [Javalin](https://javalin.io) with
[Exposed](https://github.com/JetBrains/Exposed) for persistence and the AWS SDK for S3
storage, tested with JUnit 5 and Testcontainers. The frontend is Vue 3 with Vite,
WindiCSS and JsonForms.

## License

Ingot is licensed under the [Apache License 2.0](https://github.com/OneLiteFeatherNET/ingot/blob/main/LICENSE),
the same license as the upstream Reposilite project it is derived from. Attribution and
the statement of changes are recorded in
[NOTICE](https://github.com/OneLiteFeatherNET/ingot/blob/main/NOTICE).
