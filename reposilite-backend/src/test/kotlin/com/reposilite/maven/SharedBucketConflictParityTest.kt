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

import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.s3.S3StorageProviderSettings
import com.reposilite.storage.s3.findS3SharedBucketConflicts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val CONFLICT_MESSAGE =
    "Its S3 key namespace overlaps another repository sharing the same bucket. Give each repository a distinct 'prefix', or enable 'sharedBucket' on every repository sharing the bucket."

/**
 * Pins the S3 shared-bucket refusals as the repository provider reports them today. Both repositories of a conflict are refused,
 * so no S3 client is ever built by these tests.
 */
internal class SharedBucketConflictParityTest : MavenSpecification() {

    override fun repositories(): List<RepositorySettings> = emptyList()

    private fun s3(bucket: String = "shared", prefix: String = "", sharedBucket: Boolean = false) =
        S3StorageProviderSettings(bucketName = bucket, endpoint = "https://s3.example.com", prefix = prefix, sharedBucket = sharedBucket)

    @Test
    fun `equal namespaces are both refused with the S3 message and named in the failure`() {
        // given: two repositories that resolve to the same bucket and the same key prefix
        mavenSettings.update(MavenSettings(repositories = listOf(
            RepositorySettings(id = "alpha", storageProvider = s3()),
            RepositorySettings(id = "beta", storageProvider = s3()),
        )))

        // when: the repositories are loaded
        val failures = failureFacade.getFailures().joinToString("\n")

        // then: neither repository is served and both refusals carry the message
        assertThat(mavenFacade.getRepository("alpha")).`as`("alpha is refused").isNull()
        assertThat(mavenFacade.getRepository("beta")).`as`("beta is refused").isNull()
        assertThat(failures).`as`("failure of alpha").contains("Cannot load repository 'alpha'", CONFLICT_MESSAGE)
        assertThat(failures).`as`("failure of beta").contains("Cannot load repository 'beta'", CONFLICT_MESSAGE)
    }

    @Test
    fun `a nested prefix is refused with the same S3 message`() {
        // given: one prefix nests under the other in the same bucket
        mavenSettings.update(MavenSettings(repositories = listOf(
            RepositorySettings(id = "alpha", storageProvider = s3(prefix = "team")),
            RepositorySettings(id = "beta", storageProvider = s3(prefix = "team/sub")),
        )))

        // when: the repositories are loaded
        val failures = failureFacade.getFailures().joinToString("\n")

        // then: both are refused with the S3 message
        assertThat(mavenFacade.getRepository("alpha")).`as`("alpha is refused").isNull()
        assertThat(mavenFacade.getRepository("beta")).`as`("beta is refused").isNull()
        assertThat(failures).`as`("failures of the nested prefixes").contains("Cannot load repository 'alpha'", CONFLICT_MESSAGE)
    }

    @Test
    fun `sibling namespaces of a shared bucket take no part in the conflicts`() {
        // given: two repositories in one bucket that resolve to sibling prefixes
        val repositories = listOf(
            "alpha" to s3(sharedBucket = true),
            "beta" to s3(sharedBucket = true),
        )

        // when: the conflicts are computed
        val conflicts = findS3SharedBucketConflicts(repositories)

        // then: no repository is in conflict
        assertThat(conflicts).`as`("conflicts of sibling namespaces").isEmpty()
    }

    @Test
    fun `a blank bucket takes no part in the conflicts`() {
        // given: two repositories with the same endpoint and no bucket name
        val repositories = listOf(
            "alpha" to s3(bucket = ""),
            "beta" to s3(bucket = " "),
        )

        // when: the conflicts are computed
        val conflicts = findS3SharedBucketConflicts(repositories)

        // then: no repository is in conflict
        assertThat(conflicts).`as`("conflicts of blank buckets").isEmpty()
    }

}
