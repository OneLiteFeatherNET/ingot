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

internal class SecondFactorAlternativeTest {

    private val mapper = ObjectMapper()

    @Test
    fun `adds the MFA required answer as a component schema with its flag and methods`() {
        val processed = SecondFactorAlternative.apply(loginDocument())

        assertThat(processed.at("/components/schemas/SecondFactorRequiredResponse/required").map { it.asText() })
            .describedAs("the MFA answer must name its two fields as required")
            .containsExactly("mfaRequired", "methods")
        assertThat(processed.at("/components/schemas/SecondFactorRequiredResponse/properties/mfaRequired/type").asText())
            .describedAs("mfaRequired is a boolean flag")
            .isEqualTo("boolean")
        assertThat(processed.at("/components/schemas/SecondFactorRequiredResponse/properties/methods/type").asText())
            .describedAs("methods is a list of method names")
            .isEqualTo("array")
    }

    @Test
    fun `offers the login answer and the MFA required answer as alternatives on the login 200 response`() {
        val processed = SecondFactorAlternative.apply(loginDocument())

        assertThat(processed.at("/paths/~1api~1auth~1login/post/responses/200/content/application~1json/schema/oneOf").map { it.path("\$ref").asText() })
            .describedAs("the 200 response of POST /api/auth/login must list both answers")
            .containsExactly(
                "#/components/schemas/LoginResponse",
                "#/components/schemas/SecondFactorRequiredResponse",
            )
    }

    @Test
    fun `keeps the LoginResponse schema unchanged`() {
        val before = loginDocument()
        val expected = before.path("components").path("schemas").path("LoginResponse").deepCopy<ObjectNode>()

        val processed = SecondFactorAlternative.apply(before)

        assertThat(processed.at("/components/schemas/LoginResponse"))
            .describedAs("LoginResponse must stay as generated, so existing clients keep their contract")
            .isEqualTo(expected)
    }

    @Test
    fun `leaves the other operations of the document untouched`() {
        val before = loginDocument(extraPath = """"/api/other":{"get":{"responses":{"200":{"description":"OK"}}}}""")
        val expected = before.at("/paths/~1api~1other").deepCopy<ObjectNode>()

        val processed = SecondFactorAlternative.apply(before)

        assertThat(processed.at("/paths/~1api~1other"))
            .describedAs("only the login route gains the alternative")
            .isEqualTo(expected)
    }

    @Test
    fun `returns the document unchanged when the login route is missing`() {
        val before = document(paths = "", schemas = LOGIN_RESPONSE_SCHEMA)
        val expected = before.deepCopy()

        val processed = SecondFactorAlternative.apply(before)

        assertThat(processed)
            .describedAs("a document without the login route must be served as it is")
            .isEqualTo(expected)
    }

    @Test
    fun `does not add the MFA required schema when the login route is missing`() {
        val processed = SecondFactorAlternative.apply(document(paths = "", schemas = LOGIN_RESPONSE_SCHEMA))

        assertThat(processed.at("/components/schemas/SecondFactorRequiredResponse").isMissingNode)
            .describedAs("the MFA schema must not be added when nothing references it")
            .isTrue()
    }

    @Test
    fun `returns the document unchanged when the login route has no 200 response`() {
        val before = document(
            paths = """$LOGIN_PATH_KEY:{"post":{"responses":{"401":{"description":"Unauthorized"}}}}""",
            schemas = LOGIN_RESPONSE_SCHEMA,
        )
        val expected = before.deepCopy()

        val processed = SecondFactorAlternative.apply(before)

        assertThat(processed)
            .describedAs("a login route without a 200 response must be served as it is")
            .isEqualTo(expected)
    }

    @Test
    fun `returns the document unchanged when the login 200 response has no JSON content`() {
        val before = document(
            paths = """$LOGIN_PATH_KEY:{"post":{"responses":{"200":{"description":"OK","content":{"text/plain":{"schema":{"type":"string"}}}}}}}""",
            schemas = LOGIN_RESPONSE_SCHEMA,
        )
        val expected = before.deepCopy()

        val processed = SecondFactorAlternative.apply(before)

        assertThat(processed)
            .describedAs("a 200 response without application/json content must be served as it is")
            .isEqualTo(expected)
    }

    @Test
    fun `returns the document unchanged when it has no component schemas`() {
        val before = mapper.readTree("""{"openapi":"3.1.0","paths":{$LOGIN_PATH}}""") as ObjectNode
        val expected = before.deepCopy()

        val processed = SecondFactorAlternative.apply(before)

        assertThat(processed)
            .describedAs("a document without component schemas must be served as it is")
            .isEqualTo(expected)
    }

    private fun loginDocument(extraPath: String? = null): ObjectNode {
        val paths = listOfNotNull(LOGIN_PATH, extraPath).joinToString(",")
        return mapper.readTree(
            """{"openapi":"3.1.0","paths":{$paths},"components":{"schemas":{$LOGIN_RESPONSE_SCHEMA}}}"""
        ) as ObjectNode
    }

    private fun document(paths: String, schemas: String): ObjectNode =
        mapper.readTree("""{"openapi":"3.1.0","paths":{$paths},"components":{"schemas":{$schemas}}}""") as ObjectNode

    private companion object {
        const val LOGIN_RESPONSE_SCHEMA = """"LoginResponse":{"type":"object","properties":{"mustChangePassword":{"type":"boolean"}}}"""
        const val LOGIN_PATH_KEY = """"/api/auth/login""""
        const val LOGIN_PATH = """$LOGIN_PATH_KEY:{"post":{"responses":{"200":{"description":"OK","content":{"application/json":{"schema":{"${'$'}ref":"#/components/schemas/LoginResponse"}}}}}}}"""
    }
}
