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

package net.onelitefeather.ingot.opensearch

import com.reposilite.ReposiliteObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The index definition of mapping version one (design D3), compared with a golden file. */
internal class OpenSearchIndexDefinitionTest {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    private val golden = mapper.readTree(
        OpenSearchIndexDefinitionTest::class.java
            .getResourceAsStream("/net/onelitefeather/ingot/opensearch/index-definition-v1.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("the golden index definition is missing from the test resources"),
    )

    @Test
    fun `should match the golden definition of mapping version one`() {
        // when: the definition is created with one replica
        val definition = OpenSearchIndexDefinition.create(replicas = 1)

        // then: it equals the golden file
        assertThat(definition).`as`("index definition").isEqualTo(golden)
    }

    @Test
    fun `should reject every field that is not in the mapping`() {
        // then: the mapping is strict, so an unknown field fails the write
        assertThat(golden.at("/mappings/dynamic").asText()).`as`("dynamic setting").isEqualTo("strict")
    }

    @Test
    fun `should record the mapping version in the index metadata`() {
        // when: the definition is created
        val definition = OpenSearchIndexDefinition.create(replicas = 1)

        // then: the version is in the metadata of the mapping
        assertThat(definition.at("/mappings/_meta/ingot_mapping_version").asInt())
            .`as`("ingot_mapping_version")
            .isEqualTo(OpenSearchIndexDefinition.MAPPING_VERSION)
    }

    @Test
    fun `should create one shard`() {
        // when: the definition is created
        val definition = OpenSearchIndexDefinition.create(replicas = 1)

        // then: the index has one primary shard
        assertThat(definition.at("/settings/index/number_of_shards").asInt()).`as`("primary shards").isEqualTo(1)
    }

    @Test
    fun `should take the replica count from the settings`() {
        // when: the definition is created with two replicas
        val definition = OpenSearchIndexDefinition.create(replicas = 2)

        // then: the replica count is the configured one
        assertThat(definition.at("/settings/index/number_of_replicas").asInt()).`as`("replicas").isEqualTo(2)
    }

    @Test
    fun `should declare every field of the document with its type`() {
        // when: the field names of the mapping are read
        val fields = OpenSearchIndexDefinition.fieldNames()

        // then: the mapping declares the twenty-three fields of design D3
        assertThat(fields).`as`("mapped fields").hasSize(23).contains("gav", "artifact_key", "tokens", "snapshot", "indexed_at")
    }

}
