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

@file:OptIn(ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.cache

import com.reposilite.storage.StorageProvider
import com.reposilite.storage.s3.RelatedKeyRule
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.capability.RelatedKeyAware
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CacheRelatedKeyTest {

    @TempDir
    lateinit var directory: Path

    /** A delegate that answers like the S3 storage: it takes the related key rule of the format that stores in it. */
    private class S3LikeDelegate : StorageProvider by RecordingDelegate(), RelatedKeyAware {

        var rule: RelatedKeyRule? = null
            private set

        override fun useRelatedKeys(rule: RelatedKeyRule) {
            this.rule = rule
        }

    }

    @Test
    fun `the cache forwards the related key rule to an S3-like delegate`() {
        val delegate = S3LikeDelegate()
        val provider: StorageProvider = CacheStorageProvider(delegate = delegate, directory = directory)
        val rule: RelatedKeyRule = { key -> listOf("$key.sha1") }

        assertThat(provider)
            .describedAs("the repository factory reads the related key rule only from a provider that is RelatedKeyAware")
            .isInstanceOf(RelatedKeyAware::class.java)

        (provider as RelatedKeyAware).useRelatedKeys(rule)

        assertThat(delegate.rule)
            .describedAs("the head cache of the delegate must receive the rule of the format")
            .isSameAs(rule)
    }

    @Test
    fun `the cache takes the related key rule without failing when the delegate takes none`() {
        val provider: StorageProvider = CacheStorageProvider(delegate = RecordingDelegate(), directory = directory)
        val rule: RelatedKeyRule = { key -> listOf("$key.sha1") }

        assertThat(provider).isInstanceOf(RelatedKeyAware::class.java)

        (provider as RelatedKeyAware).useRelatedKeys(rule)
    }

}
