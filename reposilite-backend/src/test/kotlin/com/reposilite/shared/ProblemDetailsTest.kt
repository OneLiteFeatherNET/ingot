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
package com.reposilite.shared

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.extensions.error
import com.reposilite.status.FailureFacade
import com.reposilite.status.infrastructure.FailureHandler
import com.reposilite.web.infrastructure.HttpResponseExceptionHandler
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpResponseException
import io.javalin.json.JavalinJackson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class ProblemDetailsTest {

    private val leakedText = "jdbc:secret://internal-host/srv/data"

    private val javalin: Javalin = Javalin.create { config ->
        config.jsonMapper(JavalinJackson(DEFAULT_OBJECT_MAPPER))
        config.routes.get("/api/missing") { ctx -> ctx.error(notFound("No such thing")) }
        config.routes.get("/api/rejected") { _ -> throw BadRequestResponse("Rejected by Javalin") }
        config.routes.get("/api/boom") { _ -> throw IllegalStateException(leakedText) }
        config.routes.exception(HttpResponseException::class.java, HttpResponseExceptionHandler())
        config.routes.exception(Exception::class.java, FailureHandler(FailureFacade(InMemoryLogger())))
    }.start(0)

    private val client = HttpClient.newHttpClient()

    @AfterEach
    fun stop() {
        javalin.stop()
    }

    private fun get(path: String, accept: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:${javalin.port()}$path")).GET()
        accept?.let { request.header("Accept", it) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(response: HttpResponse<String>): JsonNode =
        DEFAULT_OBJECT_MAPPER.readTree(response.body())

    private fun contentType(response: HttpResponse<String>): String =
        response.headers().firstValue("Content-Type").orElse("")

    @Test
    fun `should answer application json with the legacy and the problem fields by default`() {
        val response = get("/api/missing")

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(contentType(response)).startsWith("application/json")
        val body = json(response)
        assertThat(body["status"].asInt()).isEqualTo(404)
        assertThat(body["message"].asText()).isEqualTo("No such thing")
        assertThat(body["type"].asText()).isEqualTo("about:blank")
        assertThat(body["title"].asText()).isEqualTo("Not Found")
        assertThat(body["detail"].asText()).isEqualTo("No such thing")
        assertThat(body["instance"].asText()).isEqualTo("/api/missing")
        assertThat(body.has("problems")).isFalse
    }

    @Test
    fun `should answer application problem json with the same body when the client prefers it`() {
        val plain = json(get("/api/missing"))

        val response = get("/api/missing", accept = "application/problem+json")

        assertThat(contentType(response)).startsWith("application/problem+json")
        assertThat(json(response)).isEqualTo(plain)
    }

    @Test
    fun `should keep application json when the client prefers it over problem json`() {
        val response = get("/api/missing", accept = "application/json, application/problem+json;q=0.5")

        assertThat(contentType(response)).startsWith("application/json")
    }

    @Test
    fun `should keep application json for a wildcard accept header`() {
        val response = get("/api/missing", accept = "*/*")

        assertThat(contentType(response)).startsWith("application/json")
    }

    @Test
    fun `should answer a javalin http response exception in the same shape`() {
        val response = get("/api/rejected")

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(contentType(response)).startsWith("application/json")
        val body = json(response)
        assertThat(body["status"].asInt()).isEqualTo(400)
        assertThat(body["message"].asText()).isEqualTo("Rejected by Javalin")
        assertThat(body["title"].asText()).isEqualTo("Bad Request")
        assertThat(body["instance"].asText()).isEqualTo("/api/rejected")
    }

    @Test
    fun `should answer an unhandled exception with a generic 500 body without exception text`() {
        val response = get("/api/boom")

        assertThat(response.statusCode()).isEqualTo(500)
        assertThat(contentType(response)).startsWith("application/json")
        val body = json(response)
        assertThat(body["status"].asInt()).isEqualTo(500)
        assertThat(body["title"].asText()).isEqualTo("Internal Server Error")
        assertThat(response.body())
            .doesNotContain(leakedText)
            .doesNotContain("IllegalStateException")
            .doesNotContain("at com.reposilite")
    }

    @Test
    fun `should keep the problems list of a refused list`() {
        val error = ErrorResponse(400, "refused", listOf(ErrorProblem("a.b", "unknown")))

        val body = DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(error)

        assertThat(body["problems"][0]["node"].asText()).isEqualTo("a.b")
        assertThat(body["detail"].asText()).isEqualTo("refused")
    }

    @Test
    fun `should keep the constructors and properties that plugins were compiled against`() {
        val type = ErrorResponse::class.java

        assertThat(type.getConstructor(Int::class.javaPrimitiveType, String::class.java, List::class.java)).isNotNull
        assertThat(type.getConstructor(io.javalin.http.HttpStatus::class.java, String::class.java)).isNotNull
        assertThat(type.getMethod("getStatus").returnType).isEqualTo(Int::class.javaPrimitiveType)
        assertThat(type.getMethod("getMessage").returnType).isEqualTo(String::class.java)
        assertThat(type.getMethod("getProblems").returnType).isEqualTo(List::class.java)
    }

}
