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

@file:OptIn(ExperimentalIngotStorageApi::class)

package com.reposilite.storage

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.StorageProviderContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

class StorageProviderEnvironmentTest {

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    private fun contextWith(variables: Map<String, String>, providerType: String = "azure"): StorageProviderContext =
        StorageProviderContext.of(
            journalist = journalist,
            failureFacade = FailureFacade(journalist),
            workingDirectory = Path.of("work"),
            repositoryName = "releases",
            providerType = providerType,
            environment = variables::get,
        )

    @Test
    fun `environment reads the INGOT name first`() {
        val context = contextWith(mapOf("INGOT_AZURE_ACCOUNT" to "ingot", "REPOSILITE_AZURE_ACCOUNT" to "reposilite"))

        assertThat(context.environment("AZURE_ACCOUNT"))
            .describedAs("the INGOT name must win over the REPOSILITE name")
            .isEqualTo("ingot")
    }

    @Test
    fun `environment falls back to the REPOSILITE name`() {
        val context = contextWith(mapOf("REPOSILITE_AZURE_ACCOUNT" to "reposilite"))

        assertThat(context.environment("AZURE_ACCOUNT"))
            .describedAs("the REPOSILITE name must still be read when the INGOT name is not set")
            .isEqualTo("reposilite")
    }

    @Test
    fun `environment gives null when neither name is set`() {
        assertThat(contextWith(emptyMap()).environment("AZURE_ACCOUNT"))
            .describedAs("an unset variable has no value")
            .isNull()
    }

    @Test
    fun `environment refuses the settings master key`() {
        val context = contextWith(mapOf("INGOT_SETTINGS_KEY" to "master-key"))

        assertThatThrownBy { context.environment("SETTINGS_KEY") }
            .describedAs("the master key that encrypts the settings must never reach a storage provider")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("SETTINGS_KEY")
    }

    @Test
    fun `environment refuses the local database password`() {
        val context = contextWith(mapOf("INGOT_LOCAL_DATABASE_PASSWORD" to "database-secret"))

        assertThatThrownBy { context.environment("LOCAL_DATABASE_PASSWORD") }
            .describedAs("the local database credentials are core secrets and stay with core")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("LOCAL_DATABASE_PASSWORD")
    }

    @Test
    fun `environment refuses a variable outside the namespace of its provider`() {
        val context = contextWith(mapOf("INGOT_GCS_PROJECT" to "ingot"), providerType = "azure")

        assertThatThrownBy { context.environment("GCS_PROJECT") }
            .describedAs("the azure provider must not read the settings of the gcs provider")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("AZURE_")
    }

    @Test
    fun `environment refuses the core settings even for a provider named after them`() {
        val context = contextWith(mapOf("INGOT_SETTINGS_KEY" to "master-key"), providerType = "settings")

        assertThatThrownBy { context.environment("SETTINGS_KEY") }
            .describedAs("the reserved prefixes win over a provider whose type matches them")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("SETTINGS_KEY")
    }

    @Test
    fun `environment refuses a name that is not an environment variable name`() {
        assertThatThrownBy { contextWith(emptyMap()).environment("azure-account") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("azure-account")
    }

}
