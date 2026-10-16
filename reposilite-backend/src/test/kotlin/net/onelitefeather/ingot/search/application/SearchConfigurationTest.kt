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

import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.local.LocalConfigurationFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The local keys of artifact search and their environment variables (artifact search spec, 7.3). */
internal class SearchConfigurationTest {

    @Test
    fun `search is enabled and uses the database engine by default`() {
        val configuration = LocalConfiguration()

        assertThat(configuration.searchEnabled.get()).`as`("searchEnabled").isTrue()
        assertThat(configuration.searchEngine.get()).`as`("searchEngine").isEqualTo("database")
    }

    @Test
    fun `the environment variable INGOT_LOCAL_SEARCHENABLED switches search off`() {
        val configuration = LocalConfiguration()

        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = configuration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_SEARCHENABLED" to "false")),
        )

        assertThat(configuration.searchEnabled.get()).`as`("searchEnabled after the variable").isFalse()
    }

    @Test
    fun `the environment variable INGOT_LOCAL_SEARCHENGINE selects the engine`() {
        val configuration = LocalConfiguration()

        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = configuration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_SEARCHENGINE" to "database")),
        )

        assertThat(configuration.searchEngine.get()).`as`("searchEngine after the variable").isEqualTo("database")
    }

}
