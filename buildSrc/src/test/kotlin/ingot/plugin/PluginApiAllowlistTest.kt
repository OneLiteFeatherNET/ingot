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

package ingot.plugin

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PluginApiAllowlistTest {

    @Test
    fun parseSkipsBlankLinesAndComments() {
        val names = PluginApiAllowlist.parse(
            """
            # Classes of the slim plugin API artifact.

            net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
            """.trimIndent(),
        )

        assertThat(names)
            .describedAs("only the class names are read, in file order")
            .containsExactly("net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi")
    }

    // A nested type of the API, such as the variants of a sealed hierarchy, is listed by its binary name with a dollar sign.
    @Test
    fun parseAcceptsNestedClassNames() {
        val names = PluginApiAllowlist.parse("net.onelitefeather.ingot.search.access.PathPredicate\$All")

        assertThat(names)
            .describedAs("a binary name of a nested class is a valid entry")
            .containsExactly("net.onelitefeather.ingot.search.access.PathPredicate\$All")
    }

    // Legacy com.reposilite types are provided by the server and never listed, so a name outside the ingot namespace is refused.
    @Test
    fun parseRejectsNameOutsideIngotNamespace() {
        assertThatThrownBy { PluginApiAllowlist.parse("com.reposilite.plugin.api.ReposilitePlugin") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("com.reposilite.plugin.api.ReposilitePlugin")
    }
}
