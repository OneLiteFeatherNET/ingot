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

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import net.onelitefeather.ingot.repository.RepositoryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The format of a repository as the server starts it: a repository without a format is maven2, an unknown one is left out. */
internal class RepositoryFormatStartTest : MavenSpecification() {

    override fun repositories() = listOf(
        RepositorySettings("releases", visibility = PUBLIC),
        // Written by a later version: this server has no raw format, so the repository must not be served as Maven
        RepositorySettings("files", visibility = PUBLIC, format = "raw"),
    )

    private fun registry(): RepositoryRegistry =
        mavenFacade.repositoryRegistry()

    @Test
    fun `a repository without a format is maven2 in the facade and in the registry`() {
        assertThat(mavenFacade.getRepository("releases")?.format)
            .describedAs("a repository that names no format is served as maven2")
            .isEqualTo("maven2")
        assertThat(registry().repository("releases")?.format)
            .describedAs("the registry reports the resolved format, not the absent key")
            .isEqualTo("maven2")
    }

    @Test
    fun `a stored repository with an unknown format is left out at start and the failure names it`() {
        assertThat(mavenFacade.getRepository("files"))
            .describedAs("a repository of a format this server does not know must not be served")
            .isNull()
        assertThat(failureFacade.getFailures().joinToString("\n"))
            .describedAs("the start must record a failure that names the repository")
            .contains("files")
    }

    @Test
    fun `the other repositories serve while a repository of an unknown format is left out`() {
        assertThat(mavenFacade.getRepository("releases"))
            .describedAs("releases must still start next to the repository of the unknown format")
            .isNotNull
    }

}
