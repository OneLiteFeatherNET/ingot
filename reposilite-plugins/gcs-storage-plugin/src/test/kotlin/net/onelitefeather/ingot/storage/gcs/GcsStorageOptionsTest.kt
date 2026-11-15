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

package net.onelitefeather.ingot.storage.gcs

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.http.HttpTransportOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.util.Base64

/** The client options (design D1 and D2): the HTTP transport always, and the credentials that the authentication mode selects. */
internal class GcsStorageOptionsTest {

    private val clientEmail = "repositories@example-project.iam.gserviceaccount.com"

    /** A service account key with a freshly generated key pair. It is never sent anywhere, the options are only inspected. */
    private fun serviceAccountKey(): String {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded)
        return """
            {
              "type": "service_account",
              "project_id": "example-project",
              "private_key_id": "key-1",
              "private_key": "-----BEGIN PRIVATE KEY-----\n$pem\n-----END PRIVATE KEY-----\n",
              "client_email": "$clientEmail",
              "client_id": "1",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
        """.trimIndent()
    }

    @Test
    fun `should use the http transport and never the grpc transport`() {
        // when: the options of an adc repository are built
        val options = storageOptionsOf(GcsStorageProviderSettings(bucket = "repositories"))

        // then: the transport is the HTTP transport
        assertThat(options.transportOptions).`as`("transport").isInstanceOf(HttpTransportOptions::class.java)
    }

    @Test
    fun `should use the service account key of the settings with service-account-key`() {
        // when: the options of a repository with a key are built
        val options = storageOptionsOf(
            GcsStorageProviderSettings(
                bucket = "repositories",
                authentication = "service-account-key",
                credentialsJson = serviceAccountKey(),
            ),
        )

        // then: the credentials are the service account of the key
        assertThat(options.credentials).`as`("credentials").isInstanceOf(ServiceAccountCredentials::class.java)
        assertThat((options.credentials as ServiceAccountCredentials).clientEmail).`as`("client email").isEqualTo(clientEmail)
    }

    @Test
    fun `should leave the credentials to the environment with adc without reading the key`() {
        // when: the options of an adc repository are built with a key that is not valid JSON
        val options = storageOptionsOf(GcsStorageProviderSettings(bucket = "repositories", credentialsJson = "not json"))

        // then: no credential is set on the options, so the client uses the environment, and the key was never parsed
        assertThat(options.credentials).`as`("explicit credentials").isNull()
    }

}
