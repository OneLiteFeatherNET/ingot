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

package net.onelitefeather.ingot.identity.login.api

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The body of the `429` that asks a delayed dashboard login for a challenge. */
internal class ChallengeRequestTest {

    private val body = DEFAULT_OBJECT_MAPPER.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
        ChallengeRequest(provider = "example-pow", parameters = mapOf("seed" to "abc", "difficulty" to 20), instance = "/api/auth/login")
    )

    @Test
    fun `carries the code challenge_required in message and detail`() {
        assertThat(body["status"].asInt()).isEqualTo(429)
        assertThat(body["message"].asText()).isEqualTo("challenge_required")
        assertThat(body["detail"].asText()).isEqualTo("challenge_required")
    }

    @Test
    fun `carries every member of the plain error answer`() {
        val plain = DEFAULT_OBJECT_MAPPER.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
            ErrorResponse(TOO_MANY_REQUESTS, "challenge_required").also { it.instance = "/api/auth/login" }
        )

        plain.fieldNames().forEach { name ->
            assertThat(body[name]).describedAs("member $name of the plain error answer").isEqualTo(plain[name])
        }
    }

    @Test
    fun `names the provider and passes its parameters through unchanged`() {
        assertThat(body["provider"].asText()).isEqualTo("example-pow")
        assertThat(body["parameters"].toString()).isEqualTo("""{"seed":"abc","difficulty":20}""")
    }

}
