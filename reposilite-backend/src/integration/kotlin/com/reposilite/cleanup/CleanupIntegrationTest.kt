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

@file:Suppress("FunctionName")

package com.reposilite.cleanup

import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.cleanup.infrastructure.CleanupPreviewResponse
import com.reposilite.cleanup.infrastructure.CleanupRunResponse
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class CleanupIntegrationTest : ReposiliteSpecification() {

    private val keepNewestOnly = """
        {"policies":[{"id":"keep-newest","enabled":false,"repositories":["releases"],"scope":"RELEASES","keepLatest":1}]}
    """.trimIndent()

    /** Two releases of one artifact, each with a pom and a jar. */
    private fun deployTwoReleases() {
        listOf("1.0", "1.1").forEach { version ->
            useDocument("releases", "com/example/app/$version", "app-$version.pom", "<project/>", store = true)
            useDocument("releases", "com/example/app/$version", "app-$version.jar", "jar-content", store = true)
        }
    }

    private fun configurePolicies(body: String) {
        val (token, secret) = useDefaultManagementToken()
        val response = Unirest.put("$base/api/settings/domain/cleanup")
            .basicAuth(token, secret)
            .body(body)
            .asEmpty()
        assertThat(response.status).describedAs("policy update").isEqualTo(OK.code)
    }

    private fun statusOfRelease(path: String): Int =
        Unirest.get("$base/releases/$path").asEmpty().status

    @Test
    fun `should list the planned deletions of a dry run and leave every file in place`() {
        // given: a policy that keeps the newest of two releases, and both releases deployed
        configurePolicies(keepNewestOnly)
        deployTwoReleases()
        val (token, secret) = useDefaultManagementToken()

        // when: the policy is previewed
        val preview = Unirest.post("$base/api/cleanup/policies/keep-newest/dry-run")
            .basicAuth(token, secret)
            .asJacksonObject(CleanupPreviewResponse::class)

        // then: the older release is listed with its totals
        assertThat(preview.status).isEqualTo(OK.code)
        assertThat(preview.body.deletions.map { it.path }).containsExactly("com/example/app/1.0")
        assertThat(preview.body.count).isEqualTo(1)
        assertThat(preview.body.totalBytes).isPositive()
        assertThat(preview.body.warnings).isEmpty()

        // and: nothing was deleted
        assertThat(statusOfRelease("com/example/app/1.0/app-1.0.pom")).isEqualTo(OK.code)
    }

    @Test
    fun `should delete the planned versions and record the run`() {
        // given: a policy that keeps the newest of two releases, and both releases deployed
        configurePolicies(keepNewestOnly)
        deployTwoReleases()
        val (token, secret) = useDefaultManagementToken()

        // when: the policy is run
        val run = Unirest.post("$base/api/cleanup/policies/keep-newest/run")
            .basicAuth(token, secret)
            .asJacksonObject(CleanupRunResponse::class)

        // then: the run succeeded and deleted one version
        assertThat(run.status).isEqualTo(OK.code)
        assertThat(run.body.status.name).isEqualTo("SUCCEEDED")
        assertThat(run.body.deletedCount).isEqualTo(1)
        assertThat(run.body.deletedBytes).isPositive()
        assertThat(run.body.trigger.name).isEqualTo("MANUAL")

        // and: the older release is gone while the newest one stays
        assertThat(statusOfRelease("com/example/app/1.0/app-1.0.pom")).isEqualTo(NOT_FOUND.code)
        assertThat(statusOfRelease("com/example/app/1.1/app-1.1.pom")).isEqualTo(OK.code)
    }

    @Test
    fun `should list the recorded runs newest first and filter them by policy`() {
        // given: one run of the policy
        configurePolicies(keepNewestOnly)
        deployTwoReleases()
        val (token, secret) = useDefaultManagementToken()
        Unirest.post("$base/api/cleanup/policies/keep-newest/run").basicAuth(token, secret).asEmpty()

        // when: all runs and the runs of one policy are listed
        val all = Unirest.get("$base/api/cleanup/runs")
            .basicAuth(token, secret)
            .asJacksonObject(Array<CleanupRunResponse>::class)
        val other = Unirest.get("$base/api/cleanup/runs?policy=unknown")
            .basicAuth(token, secret)
            .asJacksonObject(Array<CleanupRunResponse>::class)

        // then: the run is listed for its policy and for no other
        assertThat(all.body.map { it.policyId }).containsExactly("keep-newest")
        assertThat(other.body).isEmpty()
    }

    @Test
    fun `should refuse a policy without any criterion because it would delete every version`() {
        // given: a policy that names no count and no age limit
        val (token, secret) = useDefaultManagementToken()
        val body = """{"policies":[{"id":"everything","enabled":false,"repositories":["releases"],"scope":"RELEASES"}]}"""

        // when: the policy is saved
        val response = Unirest.put("$base/api/settings/domain/cleanup")
            .basicAuth(token, secret)
            .body(body)
            .asEmpty()

        // then: the settings are rejected
        assertThat(response.status).isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `should answer not found for a policy that does not exist`() {
        // given: no policy with this id
        val (token, secret) = useDefaultManagementToken()

        // when: a dry run is requested for it
        val response = Unirest.post("$base/api/cleanup/policies/missing/dry-run")
            .basicAuth(token, secret)
            .asEmpty()

        // then: the request is answered with not found
        assertThat(response.status).isEqualTo(NOT_FOUND.code)
    }

    @Test
    fun `should reject cleanup requests without manager permission`() {
        assertManagerOnlyGetEndpoint("/api/cleanup/runs")
    }

}
