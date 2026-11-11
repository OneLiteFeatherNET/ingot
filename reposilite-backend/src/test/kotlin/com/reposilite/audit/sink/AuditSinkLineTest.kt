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

package com.reposilite.audit.sink

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AuditSinkLineTest {

    private fun line(
        actor: String = "admin",
        actorAddress: String? = null,
        details: Map<String, Any?>? = null,
    ) = AuditSinkLine(
        time = Instant.parse("2026-11-10T08:15:30Z"),
        node = "ingot-a",
        action = TOKEN_CREATED,
        actor = actor,
        actorAddress = actorAddress,
        repository = null,
        path = null,
        details = details,
        success = true,
    )

    @Test
    fun `writes the documented fields in the documented order`() {
        val encoded = line(actorAddress = "10.0.0.2", details = mapOf("token" to "deploy-bot")).encode()

        assertThat(encoded).isEqualTo(
            """{"schema":"ingot.audit","version":1,"time":"2026-11-10T08:15:30Z","node":"ingot-a","action":"TOKEN_CREATED","actor":"admin","actorAddress":"10.0.0.2","repository":null,"path":null,"details":{"token":"deploy-bot"},"success":true,"occurrences":1}"""
        )
    }

    @Test
    fun `writes every field even when its value does not apply`() {
        val fields = DEFAULT_OBJECT_MAPPER.readTree(line().encode()).fieldNames().asSequence().toList()

        assertThat(fields)
            .containsExactly("schema", "version", "time", "node", "action", "actor", "actorAddress", "repository", "path", "details", "success", "occurrences")
    }

    @Test
    fun `writes the details as a JSON object and not as a string holding JSON`() {
        val details = DEFAULT_OBJECT_MAPPER.readTree(line(details = mapOf("replaced" to false)).encode()).get("details")

        assertThat(details.isObject).describedAs("details is an object").isTrue
        assertThat(details.get("replaced").isBoolean).describedAs("nested value keeps its type").isTrue
    }

    @Test
    fun `escapes quotes, backslashes, line feeds and control characters inside a value`() {
        val actor = "mal\"ory\\\nname\u0001"

        val encoded = line(actor = actor).encode()

        assertThat(encoded).describedAs("encoded line").doesNotContain("\n")
        assertThat(DEFAULT_OBJECT_MAPPER.readTree(encoded).get("actor").asText()).isEqualTo(actor)
    }

    @Test
    fun `carries the schema name and version for consumers`() {
        assertThat(AuditSinkLine.SCHEMA).isEqualTo("ingot.audit")
        assertThat(AuditSinkLine.VERSION).isEqualTo(1)
    }

}
