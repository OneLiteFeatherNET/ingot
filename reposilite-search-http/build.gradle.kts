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

// A library, not a plugin: no service file and no event. Each plugin that uses it bundles its own copy into its shadow jar,
// because the plugin loader gives every plugin its own class loader and one plugin cannot see another plugin's classes.
plugins {
    kotlin("jvm")
}

dependencies {
    // BackendBusyException and the opt-in marker of the search API. The plugins that use this module provide them at run time.
    compileOnly(project(":reposilite-backend"))
    testImplementation(project(":reposilite-backend"))
}
