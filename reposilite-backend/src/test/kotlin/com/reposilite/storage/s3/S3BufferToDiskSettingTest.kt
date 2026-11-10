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

package com.reposilite.storage.s3

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class S3BufferToDiskSettingTest {

    @Test
    fun `should select the temporary file path when INGOT_S3_BUFFER_TO_DISK is true`() {
        // given: the setting under its own name
        val environment = mapOf("INGOT_S3_BUFFER_TO_DISK" to "true")

        // when: the setting is read
        val bufferToDisk = S3BufferToDiskSetting.read(environment)

        // then: uploads go through the temporary file
        assertThat(bufferToDisk).describedAs("buffer to disk with INGOT_S3_BUFFER_TO_DISK=true").isTrue()
    }

    @Test
    fun `should select the temporary file path when only the REPOSILITE_ spelling is true`() {
        // given: the legacy spelling alone
        val environment = mapOf("REPOSILITE_S3_BUFFER_TO_DISK" to "true")

        // when: the setting is read
        val bufferToDisk = S3BufferToDiskSetting.read(environment)

        // then: the fallback takes effect
        assertThat(bufferToDisk).describedAs("buffer to disk with only REPOSILITE_S3_BUFFER_TO_DISK=true").isTrue()
    }

    @Test
    fun `should prefer INGOT_S3_BUFFER_TO_DISK over the REPOSILITE_ spelling`() {
        // given: both spellings, with conflicting values
        val environment = mapOf(
            "INGOT_S3_BUFFER_TO_DISK" to "false",
            "REPOSILITE_S3_BUFFER_TO_DISK" to "true",
        )

        // when: the setting is read
        val bufferToDisk = S3BufferToDiskSetting.read(environment)

        // then: the INGOT_ value wins
        assertThat(bufferToDisk).describedAs("buffer to disk when INGOT_ is false and REPOSILITE_ is true").isFalse()
    }

    @Test
    fun `should stream uploads when the setting is not set`() {
        // when: the setting is read from an environment without it
        val bufferToDisk = S3BufferToDiskSetting.read(emptyMap())

        // then: the default is false, which streams the upload
        assertThat(bufferToDisk).describedAs("buffer to disk when the setting is absent").isFalse()
    }

    @Test
    fun `should refuse a value other than true or false and name the variable`() {
        // given: a value that is neither true nor false
        val environment = mapOf("INGOT_S3_BUFFER_TO_DISK" to "yes")

        // when: the setting is read
        // then: the start is stopped, and the error names the variable
        assertThatThrownBy { S3BufferToDiskSetting.read(environment) }
            .describedAs("error for INGOT_S3_BUFFER_TO_DISK=yes")
            .hasMessageContaining("INGOT_S3_BUFFER_TO_DISK")
    }

}
