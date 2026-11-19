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

package net.onelitefeather.ingot.web.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LoginChallengeSchemasTest {

    private val mapper = ObjectMapper()

    @Test
    fun `offers the error answer and the challenge request as alternatives of the login 429`() {
        val processed = LoginChallengeSchemas.apply(loginDocument())

        assertThat(processed.at("/paths/~1api~1auth~1login/post/responses/429/content/application~1json/schema/oneOf").map { it.path("\$ref").asText() })
            .describedAs("the 429 of POST /api/auth/login lists the plain error and the challenge request")
            .containsExactly("#/components/schemas/ErrorResponse", "#/components/schemas/ChallengeRequest")
    }

    @Test
    fun `the challenge request carries the error members and requires provider and parameters`() {
        val processed = LoginChallengeSchemas.apply(loginDocument())

        assertThat(processed.at("/components/schemas/ChallengeRequest/allOf/0/\$ref").asText()).isEqualTo("#/components/schemas/ErrorResponse")
        assertThat(processed.at("/components/schemas/ChallengeRequest/allOf/1/required").map { it.asText() }).containsExactly("provider", "parameters")
        assertThat(processed.at("/components/schemas/ChallengeRequest/allOf/1/properties/parameters/type").asText()).isEqualTo("object")
    }

    @Test
    fun `the login request gains the optional nullable challenge member`() {
        val processed = LoginChallengeSchemas.apply(loginDocument())

        assertThat(processed.at("/components/schemas/LoginRequest/properties/challenge/oneOf").map { it.toString() })
            .containsExactly("""{"${'$'}ref":"#/components/schemas/ChallengeResponse"}""", """{"type":"null"}""")
        assertThat(processed.at("/components/schemas/LoginRequest/required").isMissingNode).describedAs("challenge is not required").isTrue()
        assertThat(processed.at("/components/schemas/ChallengeResponse/required").map { it.asText() }).containsExactly("provider", "answer")
    }

    @Test
    fun `keeps the other members of the login request and the error schema unchanged`() {
        val before = loginDocument()
        val password = before.at("/components/schemas/LoginRequest/properties/password").deepCopy<ObjectNode>()
        val error = before.at("/components/schemas/ErrorResponse").deepCopy<ObjectNode>()

        val processed = LoginChallengeSchemas.apply(before)

        assertThat(processed.at("/components/schemas/LoginRequest/properties/password")).isEqualTo(password)
        assertThat(processed.at("/components/schemas/ErrorResponse")).isEqualTo(error)
    }

    @Test
    fun `returns the document unchanged when the login route has no 429 response`() {
        val before = mapper.readTree(
            """{"openapi":"3.1.0","paths":{"/api/auth/login":{"post":{"responses":{}}}},"components":{"schemas":{$SCHEMAS}}}"""
        ) as ObjectNode
        val expected = before.deepCopy()

        assertThat(LoginChallengeSchemas.apply(before)).isEqualTo(expected)
    }

    private fun loginDocument(): ObjectNode =
        mapper.readTree("""{"openapi":"3.1.0","paths":{$LOGIN_PATH},"components":{"schemas":{$SCHEMAS}}}""") as ObjectNode

    private companion object {
        const val ERROR_REF = """{"${'$'}ref":"#/components/schemas/ErrorResponse"}"""
        const val LOGIN_PATH = """"/api/auth/login":{"post":{"responses":{"429":{"description":"Too Many Requests","content":{"application/json":{"schema":$ERROR_REF}}}}}}"""
        const val SCHEMAS = """"ErrorResponse":{"type":"object","properties":{"message":{"type":"string"}}},""" +
            """"LoginRequest":{"type":"object","properties":{"password":{"type":["string","null"]},"challenge":{"type":"object"}}}"""
    }
}
