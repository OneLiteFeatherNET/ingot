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

package net.onelitefeather.ingot.format

import com.reposilite.maven.Maven2Format
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@OptIn(ExperimentalIngotFormatApi::class)
internal class RepositoryFormatsTest {

    @Test
    fun `the installed formats hold maven2 and nothing else`() {
        assertThat(RepositoryFormats.installed().ids)
            .describedAs("this version ships maven2 as its only repository format")
            .containsExactly("maven2")
    }

    @Test
    fun `a format is found by its id`() {
        val formats = RepositoryFormats(listOf(Maven2Format(), FakeRepositoryFormat("fake")))

        assertThat(formats.get("fake")?.id)
            .describedAs("a format is looked up by the id it declares")
            .isEqualTo("fake")
        assertThat(formats.get("raw"))
            .describedAs("an id that no format declares finds nothing")
            .isNull()
    }

    @Test
    fun `duplicate format ids fail with both implementing classes named`() {
        assertThatThrownBy {
            RepositoryFormats(listOf(Maven2Format(), FakeRepositoryFormat("fake"), DuplicateFakeRepositoryFormat("fake")))
        }
            .describedAs("two formats with the same id must stop the start")
            .hasMessageContaining(FakeRepositoryFormat::class.java.name)
            .hasMessageContaining(DuplicateFakeRepositoryFormat::class.java.name)
    }

    @Test
    fun `a registry without maven2 is refused`() {
        assertThatThrownBy { RepositoryFormats(listOf(FakeRepositoryFormat("fake"))) }
            .describedAs("maven2 is the default format of every repository and must always be present")
            .hasMessageContaining("maven2")
    }

}
