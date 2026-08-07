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

// Module directories keep their reposilite- names so upstream merges stay mechanical, and so
// plugins that depend on reposilite-backend only have to swap the group id. Only the product
// itself is branded: the server bundle publishes as net.onelitefeather.ingot:ingot.
rootProject.name = "ingot-parent"

val cores = Runtime.getRuntime().availableProcessors()
gradle.startParameter.maxWorkerCount = maxOf(1, minOf(cores - 2, 16))

include(
    "reposilite-frontend",
    "reposilite-backend"
)

// Plugins aren't needed to build the server jar, so the Docker build trims them from the context (see .dockerignore).
if (rootDir.resolve("reposilite-plugins").isDirectory) {
    include(
        "reposilite-plugins",
        "reposilite-plugins:checksum-plugin",
        "reposilite-plugins:example-plugin",
        "reposilite-plugins:groovy-plugin",
        "reposilite-plugins:migration-plugin",
        "reposilite-plugins:prometheus-plugin",
        "reposilite-plugins:swagger-plugin"
    )
}
