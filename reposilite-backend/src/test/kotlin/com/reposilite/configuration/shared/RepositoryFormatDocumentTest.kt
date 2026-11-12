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

package com.reposilite.configuration.shared

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.asSuccess
import panda.std.reactive.mutableReference

/**
 * The stored document of the maven domain as the format SPI leaves it: a configuration written by Reposilite 3.5.28 loads
 * unchanged, a repository without a format is saved without the key, an explicit maven2 is kept, a stored unknown format
 * does not stop the load, and an update that names an unknown format is refused. The document is kept in memory, so no file
 * is read or written.
 */
internal class RepositoryFormatDocumentTest {

    private class MemoryProvider(var content: String) : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) {
            this.content = content
        }

        override fun fetchConfiguration(): String =
            content

        override fun isUpdateRequired(): Boolean =
            false

        override fun isMutable(): Boolean =
            false

        override fun name(): String =
            "memory"
    }

    private val journalist = InMemoryLogger()

    private fun facade(provider: MemoryProvider): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(MavenSettings::class.java to mutableReference(MavenSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
            secretReferences = null,
        )

    private fun legacyDocument(): String =
        RepositoryFormatDocumentTest::class.java.getResourceAsStream("/configuration/configuration.shared.3.5.28.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun SharedConfigurationFacade.mavenRepositories(): List<RepositorySettings> =
        getDomainSnapshot<MavenSettings>("maven")!!.settings.repositories

    private fun storedRepositories(content: String): List<JsonNode> =
        DEFAULT_OBJECT_MAPPER.readTree(content).path("maven").path("repositories").toList()

    @Test
    fun `a configuration written by reposilite 3_5_28 loads with every repository as maven2`() {
        // given: a shared configuration as Reposilite 3.5.28 wrote it, with no format key anywhere
        val provider = MemoryProvider(legacyDocument())
        val facade = facade(provider)

        // when: the document is loaded at startup
        val result = facade.loadSharedSettingsFromString(provider.content)

        // then: the load succeeds and the repositories are the ones the document names
        assertThat(result.isOk).describedAs("loading a 3.5.28 document: ${result.errorOrNull()}").isTrue
        assertThat(facade.mavenRepositories().map { it.id })
            .describedAs("the repositories of the 3.5.28 document")
            .containsExactly("releases", "snapshots", "private", "central")
        assertThat(facade.mavenRepositories().map { it.format })
            .describedAs("a document without the key leaves the format absent, which means maven2")
            .containsOnlyNulls()
    }

    @Test
    fun `saving the 3_5_28 document after an edit writes no format key for any repository`() {
        // given: a 3.5.28 document loaded into the facade
        val provider = MemoryProvider(legacyDocument())
        val facade = facade(provider)
        facade.loadSharedSettingsFromString(provider.content)

        // when: the description of releases is edited and the domain is saved
        val result = facade.updateDomain<MavenSettings>("maven", ifMatch = null) { current ->
            current.copy(repositories = current.repositories.map {
                if (it.id == "releases") it.copy(description = "Stable releases") else it
            }).asSuccess()
        }

        // then: the save succeeds, the edit is stored and no repository carries a format key
        assertThat(result?.isOk).describedAs("saving the edited domain").isTrue
        val stored = storedRepositories(provider.content)
        assertThat(stored.single { it.path("id").asText() == "releases" }.path("description").asText())
            .describedAs("the edit is stored")
            .isEqualTo("Stable releases")
        assertThat(stored.map { it.has("format") })
            .describedAs("a repository whose format was absent is stored without the key")
            .containsOnly(false)
    }

    @Test
    fun `saving the domain keeps an explicit maven2 format`() {
        // given: a repository that names maven2 explicitly
        val provider = MemoryProvider("""{ "maven": { "repositories": [ { "id": "releases" }, { "id": "snapshots", "format": "maven2" } ] } }""")
        val facade = facade(provider)
        facade.loadSharedSettingsFromString(provider.content)

        // when: an unrelated field of the other repository is edited and the domain is saved
        facade.updateDomain<MavenSettings>("maven", ifMatch = null) { current ->
            current.copy(repositories = current.repositories.map {
                if (it.id == "releases") it.copy(description = "Edited") else it
            }).asSuccess()
        }

        // then: snapshots still names maven2, and releases still has no key
        val stored = storedRepositories(provider.content)
        assertThat(stored.single { it.path("id").asText() == "snapshots" }.path("format").asText())
            .describedAs("an explicit format is kept as it was written")
            .isEqualTo("maven2")
        assertThat(stored.single { it.path("id").asText() == "releases" }.has("format"))
            .describedAs("a repository without the key stays without it")
            .isFalse
    }

    @Test
    fun `a stored repository with an unknown format does not stop the document from loading`() {
        // given: a document written by a later version, with a raw repository this version does not know
        val provider = MemoryProvider("""{ "maven": { "repositories": [ { "id": "releases" }, { "id": "files", "format": "raw" } ] } }""")
        val facade = facade(provider)

        // when: the document is loaded at startup
        val result = facade.loadSharedSettingsFromString(provider.content)

        // then: the load succeeds and both repositories are kept, so the start can report the unknown one and serve the rest
        assertThat(result.isOk).describedAs("loading a rolled back document: ${result.errorOrNull()}").isTrue
        assertThat(facade.mavenRepositories().map { it.id to it.format })
            .describedAs("the stored repositories, the unknown format included")
            .containsExactly("releases" to null, "files" to "raw")
    }

    @Test
    fun `an update that names an unknown format is refused and the stored document stays as it was`() {
        // given: a loaded document and a provider whose content is kept for comparison
        val before = """{ "maven": { "repositories": [ { "id": "releases" } ] } }"""
        val provider = MemoryProvider(before)
        val facade = facade(provider)
        facade.loadSharedSettingsFromString(provider.content)

        // when: an update adds a repository with a format that is not installed
        val result = facade.updateDomain<MavenSettings>("maven", ifMatch = null) { current ->
            current.copy(repositories = current.repositories + RepositorySettings("files", format = "raw")).asSuccess()
        }

        // then: the update is refused as a bad request that names the repository and the known ids, and nothing is stored
        assertThat(result?.isErr).describedAs("the update with an unknown format is refused").isTrue
        assertThat(result!!.error.status).isEqualTo(HttpStatus.BAD_REQUEST.code)
        assertThat(result.error.message).describedAs("the message of the refused update").contains("'files'", "maven2")
        assertThat(provider.content).describedAs("the stored document").isEqualTo(before)
    }

    @Test
    fun `an update that adds a group of maven2 repositories is accepted`() {
        // given: a loaded document with one maven2 repository
        val provider = MemoryProvider("""{ "maven": { "repositories": [ { "id": "releases" } ] } }""")
        val facade = facade(provider)
        facade.loadSharedSettingsFromString(provider.content)

        // when: a group is added that proxies a local repository of its own format
        val result = facade.updateDomain<MavenSettings>("maven", ifMatch = null) { current ->
            current.copy(repositories = current.repositories + RepositorySettings(
                id = "public",
                type = RepositoryType.GROUP,
                proxied = listOf(MirroredRepositorySettings(reference = "releases")),
            )).asSuccess()
        }

        // then: the group is accepted, because releases is maven2 like the group
        assertThat(result?.isOk).describedAs("a group of maven2 repositories is accepted").isTrue
    }

    private fun <T> panda.std.Result<T, out Exception>.errorOrNull(): Exception? =
        if (isErr) error else null

}
