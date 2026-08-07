/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.status

import com.reposilite.status.StatusFacade.Companion.UNKNOWN_VERSION
import com.reposilite.status.StatusFacade.Companion.extractRemoteVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StatusFacadeTest {

    @Test
    fun `should read the version from a github release document`() {
        // given: the payload shape of the GitHub releases API
        val response = """{"url":"https://api.github.com/repos/OneLiteFeatherNET/ingot/releases/1","tag_name":"v3.5.29","draft":false}"""

        // when: the remote version is extracted
        val version = extractRemoteVersion(response)

        // then: the tag name is returned without its v prefix
        assertThat(version).isEqualTo("3.5.29")
    }

    @Test
    fun `should tolerate whitespace around the tag name`() {
        // given: a release document formatted across multiple lines
        val response = """
            {
              "tag_name" : "v4.0.0"
            }
        """.trimIndent()

        // when: the remote version is extracted
        val version = extractRemoteVersion(response)

        // then: the tag name is still found
        assertThat(version).isEqualTo("4.0.0")
    }

    @Test
    fun `should pass through a plain version response`() {
        // given: an endpoint that answers with the bare version, such as a Maven latest-version query
        val response = "3.5.29\n"

        // when: the remote version is extracted
        val version = extractRemoteVersion(response)

        // then: the response is used as is
        assertThat(version).isEqualTo("3.5.29")
    }

    @Test
    fun `should report an unknown version for a json document without a tag name`() {
        // given: a JSON error payload instead of a release
        val response = """{"message":"Not Found"}"""

        // when: the remote version is extracted
        val version = extractRemoteVersion(response)

        // then: the version is reported as unknown rather than as the raw payload
        assertThat(version).isEqualTo(UNKNOWN_VERSION)
    }

    @Test
    fun `should report an unknown version for an empty response`() {
        // given: an endpoint that answers with nothing
        val response = "   "

        // when: the remote version is extracted
        val version = extractRemoteVersion(response)

        // then: the version is reported as unknown
        assertThat(version).isEqualTo(UNKNOWN_VERSION)
    }

}
