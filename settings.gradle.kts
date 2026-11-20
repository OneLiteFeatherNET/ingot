/*
 * Copyright (c) 2023 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    // Lets Gradle provision a JDK 21 for the Lucene plugin tests when the build itself runs on 17.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Module directories keep their reposilite- names so upstream merges stay mechanical, and so
// plugins that depend on reposilite-backend only have to swap the group id. Only the product
// itself is branded: the server bundle publishes as net.onelitefeather.ingot:ingot.
rootProject.name = "ingot-parent"

val cores = Runtime.getRuntime().availableProcessors()
gradle.startParameter.maxWorkerCount = maxOf(1, minOf(cores - 2, 16))

include(
    "reposilite-frontend",
    "reposilite-backend",
    "reposilite-search-http",
    "ingot-cli"
)

// The consumer fixture compiles a sample plugin against the slim plugin API jar alone (openspec change add-plugin-api-artifact).
// reposilite-test is not in the Docker build context, so the fixture is included only when its directory is present.
if (rootDir.resolve("reposilite-test/plugin-api-consumer").isDirectory) {
    include("reposilite-test:plugin-api-consumer")
}

// Plugins aren't needed to build the server jar, so the Docker build trims them from the context (see .dockerignore).
// The image keeps only the bundled metrics plugin, so each plugin is included when its directory is present.
if (rootDir.resolve("reposilite-plugins").isDirectory) {
    include("reposilite-plugins")
    listOf("azure-storage-plugin", "cdn-purge-plugin", "checksum-plugin", "elasticsearch-plugin", "example-plugin", "gcs-storage-plugin", "groovy-plugin", "javadoc-plugin", "lucene-plugin", "meilisearch-plugin", "migration-plugin", "nexus-import-plugin", "opensearch-plugin", "prometheus-plugin", "storage-cache-plugin", "swagger-plugin")
        .filter { rootDir.resolve("reposilite-plugins/$it").isDirectory }
        .forEach { include("reposilite-plugins:$it") }
}
