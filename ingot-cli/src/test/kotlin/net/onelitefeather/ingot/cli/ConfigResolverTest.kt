/*
 * Copyright (c) 2023 dzikoysk
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
package net.onelitefeather.ingot.cli

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ConfigResolverTest {

    private val contextFile = ConfigFile.parse(
        """
        current: prod
        contexts:
          - name: prod
            url: https://context.example
        """.trimIndent()
    )

    @Test
    fun `should take the flag before the environment and the context`() {
        // given: a flag, an environment variable and a context that each name a URL
        // when: the settings are resolved
        val config = ConfigResolver.resolve(
            flags = mapOf("url" to "https://flag.example"),
            env = mapOf("INGOT_URL" to "https://env.example"),
            file = contextFile,
        )

        // then: the flag wins
        assertThat(config.url).isEqualTo("https://flag.example")
    }

    @Test
    fun `should take the environment before the context`() {
        // given: an environment variable and a context that name different URLs
        // when: the settings are resolved without a flag
        val config = ConfigResolver.resolve(flags = emptyMap(), env = mapOf("INGOT_URL" to "https://env.example"), file = contextFile)

        // then: requests go to the environment's URL, not the context's
        assertThat(config.url).isEqualTo("https://env.example")
    }

    @Test
    fun `should take the URL of the selected context when nothing else names one`() {
        // given: a context that names a URL and no other source
        // when: the settings are resolved
        val config = ConfigResolver.resolve(flags = emptyMap(), env = emptyMap(), file = contextFile)

        // then: the current context supplies the URL
        assertThat(config.url).isEqualTo("https://context.example")
    }

    @Test
    fun `should refuse a config file that holds a token value`() {
        // given: a config file with a token inline
        // when: the file is parsed
        // then: parsing is refused and the message points to tokenFile
        assertThatThrownBy {
            ConfigFile.parse("contexts:\n  - name: prod\n    url: https://x\n    token: name:secret\n")
        }
            .isInstanceOf(ConfigException::class.java)
            .hasMessageContaining("tokenFile")
            .hasMessageNotContaining("name:secret")
    }

    @Test
    fun `should refuse insecure TLS in the config file`() {
        // given: a config file that asks for insecure TLS
        // when: the file is parsed
        // then: parsing is refused, because insecure TLS is a flag or an environment variable only
        assertThatThrownBy {
            ConfigFile.parse("contexts:\n  - name: prod\n    url: https://x\n    insecure: true\n")
        }.isInstanceOf(ConfigException::class.java)
    }

    @Test
    fun `should refuse a token given as a flag`() {
        // given: the removed --token flag
        // when: the settings are resolved
        // then: the resolution is refused and names the environment variables
        assertThatThrownBy {
            ConfigResolver.resolve(flags = mapOf("token" to "name:secret"), env = emptyMap(), file = ConfigFile.parse(""))
        }
            .isInstanceOf(ConfigException::class.java)
            .hasMessageContaining("INGOT_TOKEN")
            .hasMessageNotContaining("name:secret")
    }

    @Test
    fun `should not read the REPOSILITE prefixed variables`() {
        // given: only the old Reposilite variable is set
        // when: the settings are resolved
        val config = ConfigResolver.resolve(flags = emptyMap(), env = mapOf("REPOSILITE_URL" to "https://old.example"), file = ConfigFile.parse(""))

        // then: the old variable is ignored
        assertThat(config.url).isNull()
    }

}
