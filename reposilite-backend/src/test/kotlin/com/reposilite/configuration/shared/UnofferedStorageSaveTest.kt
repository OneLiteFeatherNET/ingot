package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.asSuccess
import panda.std.reactive.mutableReference

/**
 * A stored storage block of a type that the served list does not offer is kept as it was written when the domain is saved for an
 * unrelated reason. The document is kept in memory, so no file is read or written.
 */
internal class UnofferedStorageSaveTest {

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

    private val unofferedBlock = """{"type":"azure-blob","container":"releases","quota":1.10,"nested":{"b":2,"a":1}}"""

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

    /** The tree read with exact decimals, so the number text of a stored block is part of the comparison. */
    private fun exactTree(text: String): JsonNode =
        DEFAULT_OBJECT_MAPPER.copy()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .readTree(text)

    @Test
    fun `an unrelated save keeps the stored block of a type that is not offered`() {
        // given: a repository on a storage type that no loaded provider offers, next to a repository on the default storage
        val provider = MemoryProvider("""{ "maven": { "repositories": [ { "id": "releases", "storageProvider": $unofferedBlock }, { "id": "snapshots" } ] } }""")
        val facade = facade(provider)
        facade.loadSharedSettingsFromString(provider.content)

        // when: the description of the other repository is edited and the domain is saved
        val result = facade.updateDomain<MavenSettings>("maven", ifMatch = null) { current ->
            current.copy(repositories = current.repositories.map {
                if (it.id == "snapshots") it.copy(description = "Nightly builds") else it
            }).asSuccess()
        }

        // then: the save succeeds, the edit is stored and the block of the unoffered type is written back unchanged
        assertThat(result?.isOk).describedAs("saving the domain after an unrelated edit").isTrue
        val stored = exactTree(provider.content).path("maven").path("repositories")
        assertThat(stored.single { it.path("id").asText() == "snapshots" }.path("description").asText())
            .describedAs("the edit of the other repository is stored")
            .isEqualTo("Nightly builds")
        assertThat(stored.single { it.path("id").asText() == "releases" }.path("storageProvider").toString())
            .describedAs("stored block of the type that is not offered, field order and number text included")
            .isEqualTo(exactTree(unofferedBlock).toString())
    }

}
