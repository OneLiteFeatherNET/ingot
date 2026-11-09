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

package com.reposilite.shared.extensions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ErrorCacheControlTest {

    @Test
    fun `should leave an api error without a cache header as it is`() {
        // when: an error answer under /api has no Cache-Control yet
        val value = errorCacheControl("/api/missing", null)

        // then: nothing is added, because bypassExternalCache decides the api answers
        assertThat(value).describedAs("Cache-Control of an api error").isNull()
    }

    @Test
    fun `should keep the cache header of an api error`() {
        // when: an error answer under /api already carries a Cache-Control value
        val value = errorCacheControl("/api/missing", "no-cache")

        // then: the value is kept as it is
        assertThat(value).describedAs("Cache-Control of an api error").isEqualTo("no-cache")
    }

    @Test
    fun `should answer no-cache for an error outside the api without a cache header`() {
        // when: a repository path answers an error with no Cache-Control yet
        val value = errorCacheControl("/releases/com/example/app/1.0/app-1.0.jar", null)

        // then: a shared cache has to revalidate the answer
        assertThat(value).describedAs("Cache-Control of a repository error").isEqualTo("no-cache")
    }

    @Test
    fun `should keep an existing cache header of an error outside the api`() {
        // when: a refused repository answer already carries the stricter value
        val value = errorCacheControl("/releases/com/example/app/1.0/app-1.0.jar", "no-cache, no-store, max-age=0")

        // then: the stricter value is kept
        assertThat(value).describedAs("Cache-Control of a refused repository answer").isEqualTo("no-cache, no-store, max-age=0")
    }

    @Test
    fun `should treat a path that only starts with the letters of api as outside the api`() {
        // when: a repository is named api-docs, so its path starts with api but is not under /api/
        val value = errorCacheControl("/api-docs/com/example/missing.jar", null)

        // then: the error is answered as not cacheable like any other repository error
        assertThat(value).describedAs("Cache-Control of an error on /api-docs").isEqualTo("no-cache")
    }

}
