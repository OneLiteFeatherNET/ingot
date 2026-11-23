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

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class S3PathStyleAccessTest {

    @Test
    fun `should use virtual host addressing when the setting is false and the property is true`() {
        val settings = S3StorageProviderSettings(pathStyleAccess = false)

        assertThat(settings.resolvePathStyleAccess(propertyValue = "true")).describedAs("setting false beats property true").isFalse()
    }

    @Test
    fun `should use path style when the setting is true and the property is absent`() {
        val settings = S3StorageProviderSettings(pathStyleAccess = true)

        assertThat(settings.resolvePathStyleAccess(propertyValue = null)).describedAs("setting true without property").isTrue()
    }

    @Test
    fun `should use path style when the setting is unset and the property is true`() {
        val settings = S3StorageProviderSettings()

        assertThat(settings.resolvePathStyleAccess(propertyValue = "true")).describedAs("unset falls back to the property").isTrue()
    }

    @Test
    fun `should use virtual host addressing when the setting is unset and the property is absent`() {
        val settings = S3StorageProviderSettings()

        assertThat(settings.resolvePathStyleAccess(propertyValue = null)).describedAs("unset without property").isFalse()
    }

    @Test
    fun `should load a document without the field as unset and write no value for it`() {
        val settings = DEFAULT_OBJECT_MAPPER.readValue("""{"type":"s3","bucketName":"maven"}""", S3StorageProviderSettings::class.java)
        val written = DEFAULT_OBJECT_MAPPER.readTree(DEFAULT_OBJECT_MAPPER.writeValueAsString(settings))

        assertThat(settings.pathStyleAccess).describedAs("field of an old document").isNull()
        assertThat(written.get("pathStyleAccess")?.isNull ?: true).describedAs("written value of an unset field").isTrue()
    }
}
