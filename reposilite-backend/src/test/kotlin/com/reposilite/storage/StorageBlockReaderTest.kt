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

package com.reposilite.storage

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import com.reposilite.storage.s3.S3StorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A mapper that accepts more than the default one, with the storage problem handler installed, as the shared configuration mapper does.
 * The storage blocks must be read with the features of this mapper, not with a reader of their own.
 */
class StorageBlockReaderTest {

    private fun relaxedMapper() = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build()
        .registerKotlinModule()
        .also { it.addHandler(StorageProviderSerdes.StorageProviderDeserializationProblemHandler()) }

    @Test
    fun `should parse a fs block with comments and a trailing comma when the configured mapper accepts them`() {
        val json = """{"type":"fs", /* kept on disk */ "quota":"10GB",}"""

        val settings = relaxedMapper().readValue(json, StorageProviderSettings::class.java)

        assertThat(settings).describedAs("settings of the fs block").isInstanceOf(FileSystemStorageProviderSettings::class.java)
        assertThat((settings as FileSystemStorageProviderSettings).quota).describedAs("quota of the fs block").isEqualTo("10GB")
    }

    @Test
    fun `should parse an s3 block with comments when the configured mapper accepts them`() {
        val json = """{"bucketName":"releases", // the bucket
            "type":"s3"}"""

        val settings = relaxedMapper().readValue(json, StorageProviderSettings::class.java)

        assertThat(settings).describedAs("settings of the s3 block").isInstanceOf(S3StorageProviderSettings::class.java)
    }

    @Test
    fun `should keep an unregistered block with comments as its document when the configured mapper accepts them`() {
        val json = """{"type":"azure-blob", /* plugin */ "container":"releases",}"""
        val mapper = relaxedMapper()

        val settings = mapper.readValue(json, StorageProviderSettings::class.java)

        assertThat(settings).describedAs("settings of an unregistered type").isInstanceOf(UnresolvedStorageProviderSettings::class.java)
        assertThat(mapper.writeValueAsString(settings))
            .describedAs("written document of the unregistered block")
            .isEqualTo("""{"type":"azure-blob","container":"releases"}""")
    }

}
