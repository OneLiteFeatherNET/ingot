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
import com.reposilite.shared.http.HttpRemoteClientProvider
import com.reposilite.shared.http.RemoteClientProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pins the statuses a client gets today when a proxy repository cannot get a file from its upstream, using the real HTTP
 * client against an in-process upstream. Artifacts are looked up in local storage first, metadata at the upstream first.
 */
internal class UpstreamStatusLegacyTest : UpstreamStatusSpecification() {

    override fun realProvider(): RemoteClientProvider = HttpRemoteClientProvider

    @ParameterizedTest(name = "{0}: artifact {1}, metadata {2}")
    @CsvSource(
        "p404, 404, 406",
        "p410, 404, 406",
        "p500, 404, 406",
        "html, 404, 406",
        "refused, 404, 400",
        "slow, 404, 400",
    )
    fun `should answer a direct proxy with the pinned status when the upstream fails`(repository: String, artifactStatus: Int, metadataStatus: Int) {
        assertThat(statusOf(artifact(repository))).describedAs("artifact status of $repository").isEqualTo(artifactStatus)
        assertThat(statusOf(metadata(repository))).describedAs("metadata status of $repository").isEqualTo(metadataStatus)
    }

    @ParameterizedTest(name = "{0}: artifact {1}, metadata {2}")
    @CsvSource(
        "p404, 404, 406",
        "p500, 404, 406",
        "refused, 404, 400",
        "slow, 404, 400",
    )
    fun `should answer a head request exactly like a get request when the upstream fails`(repository: String, artifactStatus: Int, metadataStatus: Int) {
        assertThat(statusOf(artifactDetails(repository))).describedAs("artifact head status of $repository").isEqualTo(artifactStatus)
        assertThat(statusOf(metadataDetails(repository))).describedAs("metadata head status of $repository").isEqualTo(metadataStatus)
    }

    @ParameterizedTest(name = "{0}: artifact {1}, metadata {2}")
    @CsvSource(
        "g500-404, 404, 406",
        "g404-410, 404, 406",
    )
    fun `should answer a group whose members all fail with the pinned status`(group: String, artifactStatus: Int, metadataStatus: Int) {
        assertThat(statusOf(artifact(group))).describedAs("artifact status of $group").isEqualTo(artifactStatus)
        assertThat(statusOf(metadata(group))).describedAs("metadata status of $group").isEqualTo(metadataStatus)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("g404-ok", "g500-ok", "grefused-ok", "gslow-ok")
    fun `should serve a group from its second member when the first member fails`(group: String) {
        assertServed(artifact(group))
        assertServed(metadata(group))
    }

    @Test
    fun `should serve a stored local copy while the upstream is unreachable`() {
        // given: a copy stored in a proxy whose upstream refuses connections
        addFileToRepository(FileSpec("refused", ARTIFACT, "stored"))
        addFileToRepository(FileSpec("refused", METADATA, "stored"))

        // then: both paths serve the copy
        assertServed(artifact("refused"))
        assertServed(metadata("refused"))
    }

    @Test
    fun `should serve a stored local copy while the upstream answers a server error`() {
        addFileToRepository(FileSpec("p500", ARTIFACT, "stored"))
        addFileToRepository(FileSpec("p500", METADATA, "stored"))

        assertServed(artifact("p500"))
        assertServed(metadata("p500"))
    }

    @Test
    fun `should answer 406 for an artifact once a resolved metadata file pinned the upstream and it answers a server error`() {
        // given: a pom that resolved from the upstream, which pins the host because nothing is stored
        assertServed(lookup("flaky", POM))
        upstream.flaky.set(Flaky.ERROR)

        // when: the artifact next to it is requested while the upstream fails
        val status = statusOf(artifact("flaky"))

        // then: the pinned host is asked first, so the upstream failure shows
        assertThat(status).describedAs("status of a pinned upstream that answers 500").isEqualTo(406)
    }

    @Test
    fun `should answer 400 for an artifact once a resolved metadata file pinned the upstream and it times out`() {
        assertServed(lookup("flaky", POM))
        upstream.flaky.set(Flaky.HANG)

        val status = statusOf(artifact("flaky"))

        assertThat(status).describedAs("status of a pinned upstream that times out").isEqualTo(400)
    }

    @Test
    fun `should answer 500 when the fetch itself throws an unexpected exception`() {
        assertThat(statusOf(artifact("boom"))).describedAs("artifact status of an unexpected exception").isEqualTo(500)
        assertThat(statusOf(metadata("boom"))).describedAs("metadata status of an unexpected exception").isEqualTo(500)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("refused", "slow")
    fun `should answer 400 with a generic message that holds no class name or exception text`(repository: String) {
        val message = errorOf(metadata(repository)).message

        assertThat(message).describedAs("message of $repository").contains("Upstream request failed").doesNotContain("java.", "Exception")
    }

    @Test
    fun `should record no missing metadata entry when the upstream answers 404`() {
        // when: the metadata file is requested and the upstream answers 404
        statusOf(metadata("cached404"))

        // then: nothing is recorded as a miss
        val cache = mavenFacade.getRepository("cached404")!!.resolutionCache!!
        assertThat(cache.lookup(location(METADATA), false)).describedAs("cache entry after an upstream 404").isNull()
    }

}
