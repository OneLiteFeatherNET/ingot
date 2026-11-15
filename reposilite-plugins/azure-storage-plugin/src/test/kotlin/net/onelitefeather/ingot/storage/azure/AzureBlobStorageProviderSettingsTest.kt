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

package net.onelitefeather.ingot.storage.azure

import com.reposilite.configuration.shared.api.Sensitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The settings of one `azure-blob` repository (design D10): the defaults, the endpoint and which fields each authentication mode reads. */
internal class AzureBlobStorageProviderSettingsTest {

    @Test
    fun `should always write the type as azure-blob`() {
        // when: the settings are created with their defaults
        val settings = AzureBlobStorageProviderSettings()

        // then: the type is the Azure type, and no configuration can change it
        assertThat(settings.type).`as`("storage type").isEqualTo("azure-blob")
    }

    @Test
    fun `should use the documented defaults when only the account and container are set`() {
        // when: the settings are created with the account and container alone
        val settings = AzureBlobStorageProviderSettings(accountName = "account", container = "repositories")

        // then: every other field has its documented default
        assertThat(settings.endpoint).`as`("endpoint").isEmpty()
        assertThat(settings.prefix).`as`("prefix").isEmpty()
        assertThat(settings.authentication).`as`("authentication").isEqualTo("default-credential")
        assertThat(settings.blockSize).`as`("block size in bytes").isEqualTo(8L * 1024 * 1024)
        assertThat(settings.accountKey).`as`("account key").isEmpty()
        assertThat(settings.sasToken).`as`("SAS token").isEmpty()
        assertThat(settings.connectionString).`as`("connection string").isEmpty()
    }

    @Test
    fun `should derive the endpoint from the account name when the endpoint is empty`() {
        // when: no endpoint is configured
        val settings = AzureBlobStorageProviderSettings(accountName = "account", container = "repositories")

        // then: the public Blob endpoint of the account is used
        assertThat(settings.resolvedEndpoint()).isEqualTo("https://account.blob.core.windows.net")
    }

    @Test
    fun `should use the configured endpoint as it is when one is set`() {
        // when: an Azurite endpoint is configured
        val settings = AzureBlobStorageProviderSettings(
            endpoint = "http://127.0.0.1:10000/devstoreaccount1",
            accountName = "devstoreaccount1",
            container = "repositories",
        )

        // then: the public endpoint is not derived
        assertThat(settings.resolvedEndpoint()).isEqualTo("http://127.0.0.1:10000/devstoreaccount1")
    }

    @Test
    fun `should accept default credential without any secret`() {
        // when: the default credential mode is selected with no secret at all
        val settings = AzureBlobStorageProviderSettings(accountName = "account", container = "repositories")

        // then: the settings are complete
        assertThat(settings.authenticationError()).`as`("refusal").isNull()
    }

    @Test
    fun `should refuse account key mode when the account key is empty even if a SAS token is set`() {
        // when: the account key mode is selected and only the SAS token is present
        val settings = AzureBlobStorageProviderSettings(
            accountName = "account",
            container = "repositories",
            authentication = "account-key",
            sasToken = "sv=2026-01-01&sig=value",
        )

        // then: the refusal names the account key, because the mode reads no other field
        assertThat(settings.authenticationError()).`as`("refusal").contains("accountKey")
    }

    @Test
    fun `should refuse SAS mode when the SAS token is empty even if an account key is set`() {
        // when: the SAS mode is selected and only the account key is present
        val settings = AzureBlobStorageProviderSettings(
            accountName = "account",
            container = "repositories",
            authentication = "sas",
            accountKey = "a-key-value",
        )

        // then: the refusal names the SAS token
        assertThat(settings.authenticationError()).`as`("refusal").contains("sasToken")
    }

    @Test
    fun `should refuse connection string mode when the connection string is empty`() {
        // when: the connection string mode is selected without a connection string
        val settings = AzureBlobStorageProviderSettings(
            accountName = "account",
            container = "repositories",
            authentication = "connection-string",
        )

        // then: the refusal names the connection string
        assertThat(settings.authenticationError()).`as`("refusal").contains("connectionString")
    }

    @Test
    fun `should accept account key mode when the account key is set`() {
        // when: the account key mode is selected with its key
        val settings = AzureBlobStorageProviderSettings(
            accountName = "account",
            container = "repositories",
            authentication = "account-key",
            accountKey = "a-key-value",
        )

        // then: the settings are complete
        assertThat(settings.authenticationError()).`as`("refusal").isNull()
    }

    @Test
    fun `should not reveal the value of a secret in the refusal`() {
        // when: the SAS mode is selected, the SAS token is missing, and the account key holds a recognisable value
        val settings = AzureBlobStorageProviderSettings(
            accountName = "account",
            container = "repositories",
            authentication = "sas",
            accountKey = "recognisable-account-key-value",
        )

        // then: the refusal names the field and carries no secret value
        assertThat(settings.authenticationError())
            .`as`("refusal")
            .contains("sasToken")
            .doesNotContain("recognisable-account-key-value")
    }

    @Test
    fun `should refuse an unknown authentication mode and name the mode`() {
        // when: an authentication mode that the plugin does not know is configured
        val settings = AzureBlobStorageProviderSettings(
            accountName = "account",
            container = "repositories",
            authentication = "managed-magic",
        )

        // then: the refusal names the mode
        assertThat(settings.authenticationError()).`as`("refusal").contains("managed-magic")
    }

    @Test
    fun `should mark every secret field as sensitive`() {
        // when: the getters of the secret fields are inspected
        val unmarked = listOf("getAccountKey", "getSasToken", "getConnectionString").filterNot { getter ->
            AzureBlobStorageProviderSettings::class.java.getMethod(getter).isAnnotationPresent(Sensitive::class.java)
        }

        // then: every secret getter carries the sensitive marker
        assertThat(unmarked).`as`("getters without the sensitive marker").isEmpty()
    }
}
