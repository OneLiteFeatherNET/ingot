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

import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.UploadChecksumMismatchEvent
import com.reposilite.maven.api.UploadChecksumOutcome
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.CONFLICT
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.io.File

/**
 * The deploy path with a real filesystem repository under a temporary directory. Every test deploys an artifact and then
 * its checksum file, the order Maven and Gradle use, and reads what the facade answers, what the storage holds and which
 * events were emitted.
 */
internal class UploadChecksumDeployTest : MavenSpecification() {

    private val jarPath = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val jarBytes = "the bytes of the library"
    private val correctSha1 = DigestUtils.sha1Hex(jarBytes)
    private val wrongSha1 = DigestUtils.sha1Hex("bytes of another file")

    private val uploadEvents = mutableListOf<UploadChecksumMismatchEvent>()
    private val deployEvents = mutableListOf<DeployEvent>()

    override fun repositories() = listOf(
        RepositorySettings("reject-repo", uploadChecksumPolicy = UploadChecksumPolicy.REJECT),
        RepositorySettings("warn-repo", uploadChecksumPolicy = UploadChecksumPolicy.WARN),
        RepositorySettings("default-repo"),
        RepositorySettings("ignore-repo", uploadChecksumPolicy = UploadChecksumPolicy.IGNORE),
    )

    /** The instance default is reject, so a repository that leaves the key out is refused, and one that sets ignore is not. */
    override fun uploadChecksumDefault(): UploadChecksumPolicy = UploadChecksumPolicy.REJECT

    init {
        extensions.registerEvent { event: UploadChecksumMismatchEvent -> uploadEvents += event }
        extensions.registerEvent { event: DeployEvent -> deployEvents += event }
    }

    @Test
    fun `should store a matching checksum file under reject`() {
        // given: an artifact that is deployed
        deploy("reject-repo", jarPath, jarBytes)

        // when: its matching sha1 file is deployed
        val result = deploy("reject-repo", "$jarPath.sha1", correctSha1)

        // then: the checksum file is stored and no mismatch is recorded
        assertOk(result)
        assertThat(stored("reject-repo", "$jarPath.sha1")).describedAs("stored matching checksum file").isEqualTo(correctSha1)
        assertThat(uploadEvents).describedAs("mismatch events for a matching file").isEmpty()
    }

    @Test
    fun `should refuse a mismatching checksum file under reject with 400 and store nothing`() {
        // given: an artifact that is deployed
        deploy("reject-repo", jarPath, jarBytes)

        // when: a sha1 file with another digest is deployed
        val result = deploy("reject-repo", "$jarPath.sha1", wrongSha1)

        // then: the answer is 400 and names the algorithm
        val error = assertError(result)
        assertThat(error.status).describedAs("status of a refused checksum file").isEqualTo(BAD_REQUEST.code)
        assertThat(error.message).describedAs("message of a refused checksum file").contains("SHA1")

        // and: no checksum file is stored and the artifact is unchanged
        assertThat(exists("reject-repo", "$jarPath.sha1")).describedAs("checksum file after refusal").isFalse
        assertThat(stored("reject-repo", jarPath)).describedAs("artifact after refusal").isEqualTo(jarBytes)
        assertThat(directoryEntries("reject-repo", "com/example/lib/1.0.0"))
            .describedAs("files left in the version directory after refusal")
            .containsExactly("lib-1.0.0.jar")
    }

    @Test
    fun `should record a refused checksum file as one event with outcome rejected`() {
        // given: an artifact that is deployed
        deploy("reject-repo", jarPath, jarBytes)

        // when: a mismatching sha1 file is refused
        deploy("reject-repo", "$jarPath.sha1", wrongSha1)

        // then: one mismatch event records the refusal
        assertThat(uploadEvents.map { it.outcome }).describedAs("outcomes of the refused checksum file").containsExactly(UploadChecksumOutcome.REJECTED)
    }

    @Test
    fun `should emit no deploy event for a refused checksum file`() {
        // given: an artifact that is deployed
        deploy("reject-repo", jarPath, jarBytes)

        // when: a mismatching sha1 file is refused
        deploy("reject-repo", "$jarPath.sha1", wrongSha1)

        // then: only the artifact is announced as deployed
        assertThat(deployEvents.map { it.gav.toString() }).describedAs("deploy events").containsExactly(jarPath)
    }

    @Test
    fun `should count a refused checksum file as its own deploy outcome`() {
        // given: an artifact that is deployed
        deploy("reject-repo", jarPath, jarBytes)

        // when: a mismatching sha1 file is refused
        deploy("reject-repo", "$jarPath.sha1", wrongSha1)

        // then: the refusal is counted under checksum_mismatch and not as a plain rejection
        val deploys = mavenFacade.operationTotals().deploys
        assertThat(deploys[DeployOutcome.CHECKSUM_MISMATCH]).describedAs("checksum mismatch deploys").isEqualTo(1L)
        assertThat(deploys[DeployOutcome.REJECTED]).describedAs("plain rejected deploys").isEqualTo(0L)
    }

    @Test
    fun `should answer 409 and not 400 when the checksum file already exists and redeployment is off`() {
        // given: an artifact and its matching checksum file
        deploy("reject-repo", jarPath, jarBytes)
        deploy("reject-repo", "$jarPath.sha1", correctSha1)

        // when: a mismatching sha1 file is deployed again
        val result = deploy("reject-repo", "$jarPath.sha1", wrongSha1)

        // then: the redeployment conflict wins, as it does without the policy
        assertThat(assertError(result).status).describedAs("status of a redeployment of a checksum file").isEqualTo(CONFLICT.code)
        assertThat(stored("reject-repo", "$jarPath.sha1")).describedAs("checksum file after the conflict").isEqualTo(correctSha1)
    }

    @Test
    fun `should store a mismatching checksum file under warn and record it as accepted`() {
        // given: an artifact that is deployed to a repository with the warn policy
        deploy("warn-repo", jarPath, jarBytes)

        // when: a mismatching sha1 file is deployed
        val result = deploy("warn-repo", "$jarPath.sha1", wrongSha1)

        // then: the file is stored and answered as usual
        assertOk(result)
        assertThat(stored("warn-repo", "$jarPath.sha1")).describedAs("stored mismatch under warn").isEqualTo(wrongSha1)

        // and: the mismatch is recorded as accepted
        assertThat(uploadEvents.map { it.outcome }).describedAs("outcomes under warn").containsExactly(UploadChecksumOutcome.ACCEPTED)
    }

    @Test
    fun `should store a checksum file that precedes its artifact even under reject`() {
        // when: a mismatching sha1 file is deployed before its artifact
        val result = deploy("reject-repo", "$jarPath.sha1", wrongSha1)

        // then: it is stored, because there is no artifact to check it against yet
        assertOk(result)
        assertThat(stored("reject-repo", "$jarPath.sha1")).describedAs("checksum file that precedes its artifact").isEqualTo(wrongSha1)
        assertThat(uploadEvents).describedAs("events for a checksum file that precedes its artifact").isEmpty()
    }

    @Test
    fun `should refuse a mismatch with the instance default when the repository leaves the key out`() {
        // given: an artifact in a repository without its own policy
        deploy("default-repo", jarPath, jarBytes)

        // when: a mismatching sha1 file is deployed
        val result = deploy("default-repo", "$jarPath.sha1", wrongSha1)

        // then: the instance default of reject applies
        assertThat(assertError(result).status).describedAs("status under the instance default").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `should store a mismatch when the repository sets ignore over the instance default of reject`() {
        // given: an artifact in a repository that sets ignore
        deploy("ignore-repo", jarPath, jarBytes)

        // when: a mismatching sha1 file is deployed
        val result = deploy("ignore-repo", "$jarPath.sha1", wrongSha1)

        // then: the repository value wins and the file is stored without a record
        assertOk(result)
        assertThat(stored("ignore-repo", "$jarPath.sha1")).describedAs("stored mismatch under the repository value ignore").isEqualTo(wrongSha1)
        assertThat(uploadEvents).describedAs("events under the repository value ignore").isEmpty()
    }

    private fun deploy(repository: String, path: String, content: String) =
        mavenFacade.deployFile(
            DeployRequest(
                repository = mavenFacade.getRepository(repository)!!,
                gav = path.toLocation(),
                by = "deployer",
                content = content.byteInputStream(),
                generateChecksums = false,
            )
        )

    private fun exists(repository: String, path: String): Boolean =
        mavenFacade.getRepository(repository)!!.storageProvider.exists(path.toLocation())

    private fun stored(repository: String, path: String): String =
        assertOk(mavenFacade.getRepository(repository)!!.storageProvider.getFile(path.toLocation())).readBytes().toString(Charsets.UTF_8)

    private fun directoryEntries(repository: String, path: String): List<String> =
        File(workingDirectory, "repositories/$repository/$path").list()?.toList().orEmpty()

}
