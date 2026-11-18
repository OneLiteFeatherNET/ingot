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
 * Describes the login challenge of `POST /api/auth/login`. The generator knows neither the optional `challenge` member of
 * `LoginRequest`, which the server keeps untyped so that a malformed one is answered with a fresh challenge, nor the second
 * shape of the `429`. This adds `ChallengeResponse` and `ChallengeRequest` as component schemas, the nullable `challenge`
 * member, and lists the plain error and the challenge request as alternatives of the `429`. Nothing else changes.
 */
object LoginChallengeSchemas {

    private const val LOGIN_PATH = "/api/auth/login"
    private const val ERROR_SCHEMA = "ErrorResponse"
    private const val LOGIN_REQUEST_SCHEMA = "LoginRequest"
    private const val REQUEST_SCHEMA = "ChallengeRequest"
    private const val RESPONSE_SCHEMA = "ChallengeResponse"

    private val MAPPER = ObjectMapper()

    private val RESPONSE_SCHEMA_JSON = """
        {
          "type": "object",
          "description": "The answer to the login challenge that a ChallengeRequest asked for, sent again with the same provider, username and password. Read only while the name is delayed and a login challenge provider is registered, ignored otherwise.",
          "required": ["provider", "answer"],
          "properties": {
            "provider": { "type": "string", "description": "The id of the login challenge provider that the ChallengeRequest named." },
            "answer": { "type": "object", "description": "The answer, specific to the provider and handed to it unchanged.", "additionalProperties": true }
          }
        }
    """.trimIndent()

    private val REQUEST_SCHEMA_JSON = """
        {
          "description": "The 429 of a dashboard login whose name is delayed and that the registered login challenge provider wants answered first. message and detail carry the code challenge_required. Retry-After is set as on the plain 429, and waiting it out stays a way to the next password check.",
          "allOf": [
            { "${'$'}ref": "#/components/schemas/$ERROR_SCHEMA" },
            {
              "type": "object",
              "required": ["provider", "parameters"],
              "properties": {
                "provider": { "type": "string", "description": "The id of the registered login challenge provider." },
                "parameters": { "type": "object", "description": "Public values the client needs to present or solve the challenge, specific to the provider. Never a secret.", "additionalProperties": true }
              }
            }
          ]
        }
    """.trimIndent()

    /**
     * Adds the challenge to the login route. A document this does not recognise (no component schemas, no `LoginRequest`
     * schema, or no JSON `429` on the login route) is returned unchanged. All checks run before the first mutation.
     */
    fun apply(document: ObjectNode): ObjectNode {
        val schemas = document.path("components").path("schemas") as? ObjectNode ?: return document
        val loginRequest = schemas.path(LOGIN_REQUEST_SCHEMA).path("properties") as? ObjectNode ?: return document
        val throttled = document.path("paths").path(LOGIN_PATH).path("post").path("responses").path("429")
            .path("content").path("application/json") as? ObjectNode ?: return document

        schemas.set<ObjectNode>(RESPONSE_SCHEMA, MAPPER.readTree(RESPONSE_SCHEMA_JSON) as ObjectNode)
        schemas.set<ObjectNode>(REQUEST_SCHEMA, MAPPER.readTree(REQUEST_SCHEMA_JSON) as ObjectNode)

        val challenge = loginRequest.putObject("challenge").putArray("oneOf")
        challenge.addObject().put("\$ref", "#/components/schemas/$RESPONSE_SCHEMA")
        challenge.addObject().put("type", "null")

        val alternatives = throttled.putObject("schema").putArray("oneOf")
        alternatives.addObject().put("\$ref", "#/components/schemas/$ERROR_SCHEMA")
        alternatives.addObject().put("\$ref", "#/components/schemas/$REQUEST_SCHEMA")
        return document
    }
}
