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

package net.onelitefeather.ingot.cdn.purge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The transport is a fake that records its calls, so no test opens a socket. */
internal class CdnPurgeListenerTest {

    private class RecordingTransport(private val failure: Throwable? = null) : PurgeTransport {
        val calls = mutableListOf<Triple<String, String, String?>>()

        override fun post(url: String, body: String, bearerToken: String?) {
            calls += Triple(url, body, bearerToken)
            failure?.let { throw it }
        }
    }

    private fun settings(token: String? = null, purgeUrl: String? = "https://cdn.example.test/purge") = CdnPurgeSettings(
        purgeUrl = purgeUrl,
        baseUrl = "https://repo.example.com",
        token = token,
    )

    @Test
    fun `should send one request with the public url of a deployed file`() {
        val transport = RecordingTransport()
        val listener = CdnPurgeListener(settings(), transport) { _, _ -> }

        listener.purge(repository = "releases", path = "com/example/app/1.0/app-1.0.jar")

        assertThat(transport.calls).describedAs("requests for one deploy").hasSize(1)
        assertThat(transport.calls.single().first).describedAs("purge URL of the request").isEqualTo("https://cdn.example.test/purge")
        assertThat(transport.calls.single().second)
            .describedAs("JSON body of the request")
            .isEqualTo("""{"urls": ["https://repo.example.com/releases/com/example/app/1.0/app-1.0.jar"]}""")
    }

    @Test
    fun `should send the bearer token only when it is set`() {
        val withToken = RecordingTransport()
        CdnPurgeListener(settings(token = "secret-token"), withToken) { _, _ -> }.purge("releases", "app.jar")
        assertThat(withToken.calls.single().third).describedAs("bearer token of a configured request").isEqualTo("secret-token")

        val withoutToken = RecordingTransport()
        CdnPurgeListener(settings(token = null), withoutToken) { _, _ -> }.purge("releases", "app.jar")
        assertThat(withoutToken.calls.single().third).describedAs("bearer token of an unconfigured request").isNull()
    }

    @Test
    fun `should send nothing when the purge url is not set`() {
        val transport = RecordingTransport()
        val listener = CdnPurgeListener(settings(purgeUrl = null), transport) { _, _ -> }

        listener.purge("releases", "app.jar")

        assertThat(transport.calls).describedAs("requests with the purge URL off").isEmpty()
    }

    @Test
    fun `should log a failed purge once and return normally`() {
        val failures = mutableListOf<String>()
        val transport = RecordingTransport(failure = IllegalStateException("purge endpoint answered 500"))
        val listener = CdnPurgeListener(settings(), transport) { message, _ -> failures += message }

        listener.purge("releases", "app.jar")

        assertThat(failures).describedAs("logged failures of one refused purge").hasSize(1)
    }

}
