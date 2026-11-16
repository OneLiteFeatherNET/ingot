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

@file:Suppress("FunctionName")

package com.reposilite.console

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.RoutePermission.READ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kong.unirest.core.Unirest

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ConsoleApiIntegrationTest : ReposiliteSpecification() {

    private val json = ObjectMapper()

    private fun basic(name: String, secret: String): String =
        "Basic " + java.util.Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

    @Test
    fun `should list the console commands with their dangerous and sensitive flags`() {
        // given: an instance admin, who holds the console area
        val (name, secret) = useDefaultManagementToken()

        // when: the command listing is requested
        val response = Unirest.get("$base/api/console/commands").header("Authorization", basic(name, secret)).asString()

        // then: the listing has version 1, a sensitive token generator and a dangerous stop
        assertThat(response.status).isEqualTo(200)
        val commands = json.readTree(response.body).get("commands").associateBy { it.get("name").asText() }
        assertThat(json.readTree(response.body).get("version").asInt()).isEqualTo(1)
        assertThat(commands.getValue("token-generate").get("sensitive").asBoolean()).`as`("sensitive token-generate").isTrue
        assertThat(commands.getValue("stop").get("dangerous").asBoolean()).`as`("dangerous stop").isTrue
        assertThat(commands.getValue("status").get("dangerous").asBoolean()).`as`("status is not dangerous").isFalse
    }

    @Test
    fun `should refuse the command listing without the console area and disclose no command name`() {
        // given: a token without any admin area
        val (name, secret) = useAuth("reader", "reader-secret", listOf(), routes = mapOf("/" to READ))

        // when: the command listing is requested
        val response = Unirest.get("$base/api/console/commands").header("Authorization", basic(name, secret)).asString()

        // then: the request is refused and the body names no command
        assertThat(response.status).isEqualTo(403)
        assertThat(response.body).doesNotContain("token-revoke").doesNotContain("stop")
    }

    @Test
    fun `should refuse the command listing without credentials and disclose no command name`() {
        // given: a request that carries no credentials

        // when: the command listing is requested anonymously
        val response = Unirest.get("$base/api/console/commands").asString()

        // then: the request is refused as unauthorized and the body names no command
        assertThat(response.status).isEqualTo(401)
        assertThat(response.body).doesNotContain("token-revoke").doesNotContain("stop")
    }

    @Test
    fun `should hold a dangerous structured command without confirmation and leave the token in place`() {
        // given: a token that a console user may revoke
        val (name, secret) = useDefaultManagementToken()
        useAuth("ci", "ci-secret")

        // when: the revoke runs in structured mode without confirmation
        val response = Unirest.post("$base/api/console/execute?structured=true")
            .header("Authorization", basic(name, secret))
            .body("token-revoke ci")
            .asString()

        // then: the answer is a 409 with the confirmation body, and the token still exists
        assertThat(response.status).isEqualTo(409)
        val body = json.readTree(response.body)
        assertThat(body.get("error").asText()).isEqualTo("confirmationRequired")
        assertThat(body.get("command").asText()).isEqualTo("token-revoke")
        assertThat(useFacade<AccessTokenFacade>().getAccessToken("ci")).`as`("token ci after the held revoke").isNotNull
    }

    @Test
    fun `should revoke the token in structured mode once it is confirmed`() {
        // given: a token that a console user may revoke
        val (name, secret) = useDefaultManagementToken()
        useAuth("ci-confirmed", "ci-secret")

        // when: the revoke runs in structured mode with confirmation
        val response = Unirest.post("$base/api/console/execute?structured=true&confirm=true")
            .header("Authorization", basic(name, secret))
            .body("token-revoke ci-confirmed")
            .asString()

        // then: the command succeeded with exit code 0 and the token is gone
        assertThat(response.status).isEqualTo(200)
        val body = json.readTree(response.body)
        assertThat(body.get("exitCode").asInt()).isEqualTo(0)
        assertThat(body.get("node").asText()).isNotBlank()
        assertThat(useFacade<AccessTokenFacade>().getAccessToken("ci-confirmed")).`as`("token after confirmed revoke").isNull()
    }

    @Test
    fun `should answer the tokens as structured data next to the text lines`() {
        // given: an instance admin and a token with a route
        val (name, secret) = useDefaultManagementToken()
        useAuth("data-ci", "data-secret", routes = mapOf("/" to READ))

        // when: tokens runs in structured mode
        val body = json.readTree(
            Unirest.post("$base/api/console/execute?structured=true").header("Authorization", basic(name, secret)).body("tokens").asString().body
        )

        // then: the data lists the token by name with its routes, and the output keeps its text lines
        val token = body.get("data").single { it.get("name").asText() == "data-ci" }
        assertThat(token.get("routes").single().get("path").asText()).isEqualTo("/")
        assertThat(body.get("output").first().get("text").asText()).startsWith("Tokens (")
    }

    @Test
    fun `should answer the status as structured data with the failure count`() {
        // given: an instance admin
        val (name, secret) = useDefaultManagementToken()

        // when: status runs in structured mode
        val body = json.readTree(
            Unirest.post("$base/api/console/execute?structured=true").header("Authorization", basic(name, secret)).body("status").asString().body
        )

        // then: the data holds the status fields as numbers and booleans
        assertThat(body.get("data").get("failures").isInt).`as`("failures is a number").isTrue
        assertThat(body.get("data").get("active").isBoolean).`as`("active is a boolean").isTrue
    }

    @Test
    fun `should answer the statistics as structured data with the record counts`() {
        // given: an instance admin
        val (name, secret) = useDefaultManagementToken()

        // when: stats runs in structured mode
        val body = json.readTree(
            Unirest.post("$base/api/console/execute?structured=true").header("Authorization", basic(name, secret)).body("stats").asString().body
        )

        // then: the data holds the unique and all record counts and the matched requests
        assertThat(body.get("data").get("uniqueRecords").isNumber).isTrue
        assertThat(body.get("data").get("allRecords").isNumber).isTrue
        assertThat(body.get("data").get("requests").isArray).isTrue
    }

    @Test
    fun `should answer null data for a command that provides none`() {
        // given: an instance admin
        val (name, secret) = useDefaultManagementToken()

        // when: help runs in structured mode
        val body = json.readTree(
            Unirest.post("$base/api/console/execute?structured=true").header("Authorization", basic(name, secret)).body("help").asString().body
        )

        // then: data is null and the text lines are there
        assertThat(body.path("data").isNull).isTrue
        assertThat(body.get("output").size()).isGreaterThan(0)
    }

    @Test
    fun `should answer plain executions with the exact JSON body they always had`() {
        // given: an instance admin
        val (name, secret) = useDefaultManagementToken()

        // when: a succeeding and a failing command run without structured mode
        val succeeded = Unirest.post("$base/api/console/execute")
            .header("Authorization", basic(name, secret))
            .body("level info")
            .asString()
        val failed = Unirest.post("$base/api/console/execute")
            .header("Authorization", basic(name, secret))
            .body("nosuchcommand")
            .asString()

        // then: each body is the plain answer, the same JSON as before, with no exit code or node
        assertThat(succeeded.status).isEqualTo(200)
        assertThat(json.readTree(succeeded.body))
            .`as`("plain body of a succeeded command")
            .isEqualTo(json.readTree("""{"status":"SUCCEEDED","response":["The new logging level has been set to info"]}"""))
        assertThat(failed.status).isEqualTo(200)
        assertThat(json.readTree(failed.body))
            .`as`("plain body of a failed command")
            .isEqualTo(json.readTree("""{"status":"FAILED","response":["Unknown command nosuchcommand"]}"""))
    }

}
