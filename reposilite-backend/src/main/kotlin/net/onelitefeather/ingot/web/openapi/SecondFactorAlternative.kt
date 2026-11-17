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

/**
 * Describes the answer of `POST /api/auth/login` for a user with a second factor. The login answers 200 with either the
 * session ([LoginResponse]) or the MFA required body (`SecondFactorRequiredResponse`). The generator only knows the first,
 * so this adds the second as a component schema and lists both as alternatives of the 200 response. The schema of
 * `LoginResponse` stays as generated, so the change is additive for existing clients.
 */
object SecondFactorAlternative {

    const val LOGIN_PATH = "/api/auth/login"
    const val LOGIN_SCHEMA = "LoginResponse"
    const val MFA_SCHEMA = "SecondFactorRequiredResponse"

    private val MAPPER = ObjectMapper()

    private val MFA_SCHEMA_JSON = """
        {
          "type": "object",
          "description": "The answer of a password login for a user with a second factor. No session is started and no session cookie is set. The challenge cookie is set, and the verification at /api/auth/mfa/verify completes the sign-in.",
          "required": ["mfaRequired", "methods"],
          "properties": {
            "mfaRequired": { "type": "boolean", "description": "Always true in this answer." },
            "methods": {
              "type": "array",
              "description": "The methods the verification takes: AUTHENTICATOR, RECOVERY_CODE or PASSKEY.",
              "items": { "type": "string" }
            }
          }
        }
    """.trimIndent()

    fun apply(document: ObjectNode): ObjectNode {
        val schemas = document.path("components").path("schemas") as? ObjectNode
            ?: throw IllegalStateException("The OpenAPI document has no component schemas")
        val login = document.path("paths").path(LOGIN_PATH).path("post").path("responses").path("200")
        check(login.isObject) { "The OpenAPI document has no 200 response for POST $LOGIN_PATH" }

        schemas.set<ObjectNode>(MFA_SCHEMA, MAPPER.readTree(MFA_SCHEMA_JSON) as ObjectNode)

        val schema = login.path("content").path("application/json") as? ObjectNode
            ?: throw IllegalStateException("The 200 response of POST $LOGIN_PATH has no application/json content")
        val alternatives = schema.putObject("schema").putArray("oneOf")
        alternatives.addObject().put("\$ref", "#/components/schemas/$LOGIN_SCHEMA")
        alternatives.addObject().put("\$ref", "#/components/schemas/$MFA_SCHEMA")
        return document
    }
}
