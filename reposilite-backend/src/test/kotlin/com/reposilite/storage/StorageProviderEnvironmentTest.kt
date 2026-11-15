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

    private fun contextWith(variables: Map<String, String>): StorageProviderContext =
        StorageProviderContext.of(
            journalist = journalist,
            failureFacade = FailureFacade(journalist),
            workingDirectory = Path.of("work"),
            repositoryName = "releases",
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
    fun `environment refuses a name that is not an environment variable name`() {
        assertThatThrownBy { contextWith(emptyMap()).environment("azure-account") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("azure-account")
    }

}
