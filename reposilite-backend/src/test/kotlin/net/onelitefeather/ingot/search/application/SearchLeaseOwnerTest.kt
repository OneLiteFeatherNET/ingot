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

package net.onelitefeather.ingot.search.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The owner of the search lease survives a restart of the server, because it is kept with the data directory. */
internal class SearchLeaseOwnerTest {

    @TempDir
    lateinit var root: Path

    @Test
    fun `the owner of a data directory is the same after a restart`() {
        val first = SearchLeaseOwner.of(root)

        val afterRestart = SearchLeaseOwner.of(root)

        assertThat(afterRestart).`as`("owner after a restart of the same data directory").isEqualTo(first)
    }

    @Test
    fun `two data directories have two owners`() {
        val other = root.resolve("other")

        assertThat(SearchLeaseOwner.of(other)).`as`("owner of another data directory")
            .isNotEqualTo(SearchLeaseOwner.of(root))
    }

    @Test
    fun `the owner is a non-blank identifier that fits the claimant column`() {
        val owner = SearchLeaseOwner.of(root)

        assertThat(owner).`as`("owner of the data directory").isNotBlank().hasSizeLessThanOrEqualTo(64)
    }

}
