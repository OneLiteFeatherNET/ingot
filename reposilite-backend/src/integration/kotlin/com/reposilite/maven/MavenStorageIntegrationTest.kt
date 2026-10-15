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

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.maven.infrastructure.StorageUsageResponse
import io.javalin.http.HttpStatus.ACCEPTED
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MavenStorageIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `should reject storage usage requests without manager permission`() {
        assertManagerOnlyGetEndpoint("/api/maven/storage")
    }

    @Test
    fun `should report the bytes stored in each repository once a refresh has finished`() {
        // given: a five byte artifact in the releases repository
        useDocument("releases", "com/example/app/1.0/app-1.0.jar", "app-1.0.jar", content = "12345", store = true)
        val (token, secret) = useDefaultManagementToken()

        // when: a refresh is requested, and the computation it started is awaited
        val refresh = Unirest.post("$base/api/maven/storage/refresh")
            .basicAuth(token, secret)
            .asEmpty()
        assertThat(refresh.status).isEqualTo(ACCEPTED.code)
        mavenFacade.refreshStorageUsage().join()

        // then: the usage is served with its timestamp and no computation running
        val usage = Unirest.get("$base/api/maven/storage")
            .basicAuth(token, secret)
            .asJacksonObject(StorageUsageResponse::class)
        assertThat(usage.status).isEqualTo(OK.code)
        val body = usage.body
        assertThat(body.computing).isFalse
        assertThat(body.computedAt).isNotNull
        assertThat(body.totalBytes).isEqualTo(5L)

        val releases = body.repositories.single { it.id == "releases" }
        assertThat(releases.usedBytes).isEqualTo(5L)
        assertThat(releases.fileCount).isEqualTo(1L)
        assertThat(releases.quota).isEqualTo("100%")
        assertThat(releases.quotaBytes).isGreaterThan(0L)

        val snapshots = body.repositories.single { it.id == "snapshots" }
        assertThat(snapshots.usedBytes).isEqualTo(0L)
    }

}
