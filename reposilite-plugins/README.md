# Plugins
Basic plugins for Ingot, maintained with the core project.

* [checksum-plugin](./checksum-plugin) - Generates a missing checksum file (`.md5`, `.sha1`, `.sha256` or `.sha512`) when a client requests it for an artifact that exists. Repositories with mirror hosts are skipped.
* [example-plugin](./example-plugin) - Basic template of an Ingot plugin in Java with Gradle KTS configuration (preferred over Groovy script file).
* [groovy-plugin](./groovy-plugin) - Utility plugin that enables scripting API in Groovy. In general, you should probably use Java/Kotlin for better experience and performance, but that's still an option - especially useful for quick prototyping of dirty scripts.
* [migration-plugin](./migration-plugin) - Converts old `tokens.dat` file from Reposilite 2.x into the new json scheme used by 3.x 
* [nexus-import-plugin](./nexus-import-plugin) - The Nexus import. Bundled in the container image and loaded only when `INGOT_NEXUS_IMPORT_ENABLED` is `true`.
* [opensearch-plugin](./opensearch-plugin) - Registers the `opensearch` search backend. Bundled in the container image and loaded only when `INGOT_OPENSEARCH_ENABLED` is `true`.
* [prometheus-plugin](./prometheus-plugin) - Exposes Prometheus metrics. Bundled in the container image and enabled by `INGOT_METRICS_ENABLED=true`.
* [swagger-plugin](./swagger-plugin) - Exposes Swagger UI `/swagger` for builtin OpenApi `/openapi` endpoint 

If you're interested in extending Ingot with a new plugin, you should probably create a new repository.
Let us know about your work, so we can list your project below!

### 3rd plugins

* ~ None ~
