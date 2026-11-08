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

package com.reposilite.maven

import com.reposilite.maven.specification.UpstreamStatusSpecification
import com.reposilite.maven.specification.UpstreamStub.Flaky
import com.reposilite.shared.http.ConfiguredHttpRemoteClientProvider
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.shared.http.UpstreamStatusMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The statuses a proxy repository answers with INGOT_PROXY_UPSTREAM_STATUS=strict. Artifact and metadata paths answer the same.
 */
internal class UpstreamStatusStrictTest : UpstreamStatusSpecification() {

    override fun realProvider(): RemoteClientProvider = ConfiguredHttpRemoteClientProvider(UpstreamStatusMode.STRICT, millisPerTimeoutUnit = 100)

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
        "p404, 404",
        "p410, 404",
        "p500, 502",
        "html, 502",
        "refused, 502",
        "slow, 504",
    )
    fun `should answer a direct proxy with the same status on the artifact and the metadata path`(repository: String, status: Int) {
        assertThat(statusOf(artifact(repository))).describedAs("artifact status of $repository").isEqualTo(status)
        assertThat(statusOf(metadata(repository))).describedAs("metadata status of $repository").isEqualTo(status)
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
        "p404, 404",
        "p500, 502",
        "refused, 502",
        "slow, 504",
    )
    fun `should answer a head request exactly like a get request`(repository: String, status: Int) {
        assertThat(statusOf(artifactDetails(repository))).describedAs("artifact head status of $repository").isEqualTo(status)
        assertThat(statusOf(metadataDetails(repository))).describedAs("metadata head status of $repository").isEqualTo(status)
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
        "g404-410, 404",
        "g500-404, 502",
    )
    fun `should answer a group whose members all fail with the worst status`(group: String, status: Int) {
        assertThat(statusOf(artifact(group))).describedAs("artifact status of $group").isEqualTo(status)
        assertThat(statusOf(metadata(group))).describedAs("metadata status of $group").isEqualTo(status)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("g404-ok", "g500-ok", "grefused-ok", "gslow-ok")
    fun `should serve a group from its second member when the first member misses or fails`(group: String) {
        assertServed(artifact(group))
        assertServed(metadata(group))
    }

    @Test
    fun `should serve a stored local copy while the upstream is unreachable`() {
        addFileToRepository(FileSpec("refused", ARTIFACT, "stored"))
        addFileToRepository(FileSpec("refused", METADATA, "stored"))

        assertServed(artifact("refused"))
        assertServed(metadata("refused"))
    }

    @Test
    fun `should answer 502 for an artifact once a resolved metadata file pinned the upstream and it answers a server error`() {
        assertServed(lookup("flaky", POM))
        upstream.flaky.set(Flaky.ERROR)

        assertThat(statusOf(artifact("flaky"))).describedAs("status of a pinned upstream that answers 500").isEqualTo(502)
    }

    @Test
    fun `should answer 504 for an artifact once a resolved metadata file pinned the upstream and it times out`() {
        assertServed(lookup("flaky", POM))
        upstream.flaky.set(Flaky.HANG)

        assertThat(statusOf(artifact("flaky"))).describedAs("status of a pinned upstream that times out").isEqualTo(504)
    }

    @Test
    fun `should answer 500 when the fetch itself throws an unexpected exception`() {
        assertThat(statusOf(artifact("boom"))).describedAs("artifact status of an unexpected exception").isEqualTo(500)
    }

    @Test
    fun `should record a missing metadata entry when the upstream answers 404`() {
        statusOf(metadata("cached404"))

        val cache = mavenFacade.getRepository("cached404")!!.resolutionCache!!
        assertThat(cache.lookup(location(METADATA), false)).describedAs("cache entry after an upstream 404").isEqualTo(ResolutionCache.Origin.MissingMetadata)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("refused", "slow", "p500", "html")
    fun `should not leak a class name or an exception text into the message`(repository: String) {
        val message = errorOf(artifact(repository)).message

        assertThat(message).describedAs("message of $repository").doesNotContain("java.", "Exception")
    }

}
