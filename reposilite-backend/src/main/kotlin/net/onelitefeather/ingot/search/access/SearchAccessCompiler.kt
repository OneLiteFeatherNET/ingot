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
package net.onelitefeather.ingot.search.access

import com.reposilite.maven.RepositoryVisibility
import net.onelitefeather.ingot.identity.model.AccessSubject

/**
 * Compiles the grants of one caller into a [SearchAccess] (artifact search spec, 5.3). The compiler reads the subject the
 * evaluator reads and never the tables. A null subject is the anonymous caller.
 */
internal object SearchAccessCompiler {

    /** The most terms one repository may carry; above it the request fails with 503 (5.3). */
    const val MAX_TERMS_PER_REPOSITORY: Int = 200

    /** The most terms of all repositories together (5.3). */
    const val MAX_TERMS_TOTAL: Int = 1000

    /** The row filter of [subject] over [repositories], keyed by repository name with its visibility. */
    fun compile(subject: AccessSubject?, repositories: Map<String, RepositoryVisibility>): SearchAccess =
        TODO("task 14: compile caller grants into a row filter")
}
