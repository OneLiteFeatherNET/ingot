/*
 * Copyright (c) 2026 OneLiteFeather
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

// Consumer fixture of the openspec change add-plugin-api-artifact. It compiles a sample plugin against the slim plugin API
// jar alone. It depends on no project of the monorepo except through the jar, and on no legacy base class.
plugins {
    kotlin("jvm")
}

// The slim jar is a file dependency. Its producer is the pluginApiJar task of reposilite-backend, so no project dependency reaches the
// server classes and the sample compiles against the jar alone.
val slimPluginApiJar = project(":reposilite-backend").tasks.named<Jar>("pluginApiJar")

dependencies {
    // Kotlin standard library at the version the server compiles with, pinned by the root build.
    implementation(kotlin("stdlib"))
    compileOnly(files(slimPluginApiJar))
    testImplementation(files(slimPluginApiJar))
}
