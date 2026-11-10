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

package com.reposilite.maven

/**
 * Which versions a repository accepts on deploy. Absent in the settings means [MIXED], which is what every repository
 * did before the policy existed. The policy is checked for deploys only.
 *
 * - [RELEASE] refuses a deploy into a `-SNAPSHOT` version directory.
 * - [SNAPSHOT] refuses a deploy into any other version directory, artifact metadata excepted.
 * - [MIXED] accepts both.
 */
enum class VersionPolicy {
    RELEASE,
    SNAPSHOT,
    MIXED,
}
