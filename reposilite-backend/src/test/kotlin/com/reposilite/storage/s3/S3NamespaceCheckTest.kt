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

package com.reposilite.storage.s3

import net.onelitefeather.ingot.storage.capability.findSharedNamespaceConflicts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The S3 conflicts are the conflicts of the generic namespace check, for each of the four shapes that the S3 parity tests pin.
 */
internal class S3NamespaceCheckTest {

    private fun s3(bucket: String = "shared", prefix: String = "", sharedBucket: Boolean = false) =
        S3StorageProviderSettings(bucketName = bucket, endpoint = "https://s3.example.com", prefix = prefix, sharedBucket = sharedBucket)

    private fun assertSameConflicts(repositories: List<Pair<String, S3StorageProviderSettings>>) {
        val s3Conflicts = findS3SharedBucketConflicts(repositories)
        val genericConflicts = findSharedNamespaceConflicts(repositories).keys
        assertThat(genericConflicts).`as`("conflicts of the generic check").isEqualTo(s3Conflicts)
    }

    @Test
    fun `equal S3 namespaces give the same conflicts in the generic check`() {
        assertSameConflicts(listOf("alpha" to s3(), "beta" to s3()))
    }

    @Test
    fun `a nested S3 prefix gives the same conflicts in the generic check`() {
        assertSameConflicts(listOf("alpha" to s3(prefix = "team"), "beta" to s3(prefix = "team/sub")))
    }

    @Test
    fun `sibling S3 namespaces give the same conflicts in the generic check`() {
        assertSameConflicts(listOf("alpha" to s3(sharedBucket = true), "beta" to s3(sharedBucket = true)))
    }

    @Test
    fun `a blank S3 bucket gives the same conflicts in the generic check`() {
        assertSameConflicts(listOf("alpha" to s3(bucket = ""), "beta" to s3(bucket = "")))
    }

}
