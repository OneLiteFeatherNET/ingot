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

import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import net.onelitefeather.ingot.format.ExperimentalIngotFormatApi
import net.onelitefeather.ingot.format.FakeRepositoryFormat
import net.onelitefeather.ingot.format.RepositoryFormats
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The format rules of the maven settings, checked against a registry built in the test. No server is started. */
@OptIn(ExperimentalIngotFormatApi::class)
internal class RepositoryFormatSettingsTest {

    private val formats = RepositoryFormats(listOf(Maven2Format(), FakeRepositoryFormat("fake")))

    @Test
    fun `a repository without a format is accepted`() {
        val settings = MavenSettings(repositories = listOf(RepositorySettings("releases")))

        assertThat(settings.formatViolations(formats))
            .describedAs("a repository that names no format is maven2 and needs no rule")
            .isEmpty()
    }

    @Test
    fun `an unknown format id is a violation naming the repository and the known ids`() {
        val settings = MavenSettings(repositories = listOf(RepositorySettings("files", format = "raw")))

        assertThat(settings.formatViolations(formats))
            .describedAs("a format that no installed format provides must be refused")
            .singleElement()
            .asString()
            .contains("'files'", "'raw'", "maven2", "fake")
    }

    @Test
    fun `the id maven is refused with a hint to use maven2 or leave the format empty`() {
        val settings = MavenSettings(repositories = listOf(RepositorySettings("releases", format = "maven")))

        assertThat(settings.formatViolations(formats))
            .describedAs("maven is the layout name, the format id is maven2")
            .singleElement()
            .asString()
            .contains("'releases'", "'maven2'", "empty")
    }

    @Test
    fun `a group that proxies a repository of another format is a violation naming both`() {
        val settings = MavenSettings(repositories = listOf(
            RepositorySettings("fake-hosted", format = "fake", type = RepositoryType.HOSTED),
            RepositorySettings("public", type = RepositoryType.GROUP, proxied = listOf(
                MirroredRepositorySettings(reference = "fake-hosted"),
            )),
        ))

        assertThat(settings.formatViolations(formats))
            .describedAs("a group proxies repositories of its own format only")
            .singleElement()
            .asString()
            .contains("'public'", "'fake-hosted'", "maven2", "fake")
    }

    @Test
    fun `a group that proxies a repository of its own format is accepted`() {
        val settings = MavenSettings(repositories = listOf(
            RepositorySettings("hosted", type = RepositoryType.HOSTED, format = "maven2"),
            RepositorySettings("public", type = RepositoryType.GROUP, proxied = listOf(
                MirroredRepositorySettings(reference = "hosted"),
            )),
        ))

        assertThat(settings.formatViolations(formats))
            .describedAs("a group of maven2 repositories is a valid group")
            .isEmpty()
    }

    @Test
    fun `the format rules are not part of the load check, so a stored unknown format keeps the document loadable`() {
        val settings = MavenSettings(repositories = listOf(RepositorySettings("files", format = "raw")))

        assertThat(settings.violations())
            .describedAs("a document written by a later version must not reject every repository at start")
            .isEmpty()
    }

}
