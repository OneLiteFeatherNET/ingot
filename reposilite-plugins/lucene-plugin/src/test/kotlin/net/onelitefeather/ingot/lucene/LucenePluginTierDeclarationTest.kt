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

package net.onelitefeather.ingot.lucene

import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LucenePluginTierDeclarationTest {

    @Test
    fun `should declare the lucene-search tier as experimental`() {
        // when: the tier is read from the plugin annotation by reflection
        val tier = LucenePlugin::class.java.getAnnotation(Plugin::class.java)?.tier

        // then: the plugin opts in to the experimental search API, so it is experimental
        assertThat(tier)
            .describedAs("tier of lucene-plugin")
            .isEqualTo(PluginTier.EXPERIMENTAL)
    }

}
