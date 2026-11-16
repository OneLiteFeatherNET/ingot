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
import java.time.Duration

internal class S3PresignConfigTest {

    @Test
    fun `should keep the redirects off and the default lifetime when no variable is set`() {
        val config = S3PresignConfig.read(emptyMap())

        assertThat(config.enabled).describedAs("switch without variables").isFalse
        assertThat(config.ttl).describedAs("lifetime without variables").isEqualTo(Duration.ofSeconds(300))
        assertThat(config.endpoint).describedAs("endpoint without variables").isNull()
    }

    @Test
    fun `should turn the redirects on from the INGOT variable regardless of case`() {
        val config = S3PresignConfig.read(mapOf("INGOT_S3_PRESIGNED_REDIRECTS" to "TRUE"))

        assertThat(config.enabled).describedAs("switch set to TRUE").isTrue
    }

    @Test
    fun `should read the legacy REPOSILITE variable when the INGOT variable is not set`() {
        val config = S3PresignConfig.read(mapOf("REPOSILITE_S3_PRESIGNED_REDIRECTS" to "true"))

        assertThat(config.enabled).describedAs("legacy switch set to true").isTrue
    }

    @Test
    fun `should prefer the INGOT variable over the legacy one`() {
        val config = S3PresignConfig.read(
            mapOf("INGOT_S3_PRESIGNED_REDIRECTS" to "false", "REPOSILITE_S3_PRESIGNED_REDIRECTS" to "true")
        )

        assertThat(config.enabled).describedAs("INGOT switch set to false").isFalse
    }

    @Test
    fun `should refuse an unknown switch value and name the variable`() {
        assertThatThrownBy { S3PresignConfig.read(mapOf("INGOT_S3_PRESIGNED_REDIRECTS" to "yes")) }
            .describedAs("start with an unknown switch value")
            .hasMessageContaining("INGOT_S3_PRESIGNED_REDIRECTS")
    }

    @Test
    fun `should refuse a lifetime below 60 seconds and name the variable`() {
        assertThatThrownBy { S3PresignConfig.read(mapOf("INGOT_S3_PRESIGN_TTL_SECONDS" to "59")) }
            .describedAs("start with a lifetime of 59 seconds")
            .hasMessageContaining("INGOT_S3_PRESIGN_TTL_SECONDS")
    }

    @Test
    fun `should refuse a lifetime above 3600 seconds and name the variable`() {
        assertThatThrownBy { S3PresignConfig.read(mapOf("INGOT_S3_PRESIGN_TTL_SECONDS" to "3601")) }
            .describedAs("start with a lifetime of 3601 seconds")
            .hasMessageContaining("INGOT_S3_PRESIGN_TTL_SECONDS")
    }

    @Test
    fun `should refuse a lifetime that is not a whole number and name the variable`() {
        assertThatThrownBy { S3PresignConfig.read(mapOf("INGOT_S3_PRESIGN_TTL_SECONDS" to "five minutes")) }
            .describedAs("start with a lifetime that is not a number")
            .hasMessageContaining("INGOT_S3_PRESIGN_TTL_SECONDS")
    }

    @Test
    fun `should accept the bounds of the lifetime`() {
        assertThat(S3PresignConfig.read(mapOf("INGOT_S3_PRESIGN_TTL_SECONDS" to "60")).ttl)
            .describedAs("lifetime of 60 seconds").isEqualTo(Duration.ofSeconds(60))
        assertThat(S3PresignConfig.read(mapOf("INGOT_S3_PRESIGN_TTL_SECONDS" to "3600")).ttl)
            .describedAs("lifetime of 3600 seconds").isEqualTo(Duration.ofSeconds(3600))
    }

    @Test
    fun `should use the presign endpoint variable when it is set`() {
        val config = S3PresignConfig.read(mapOf("INGOT_S3_PRESIGN_ENDPOINT" to "https://files.example.test"))

        assertThat(config.endpoint).describedAs("endpoint of the variable").isEqualTo("https://files.example.test")
    }

}
