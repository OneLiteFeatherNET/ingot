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

package com.reposilite.audit.infrastructure

import com.reposilite.audit.AuditCursor
import com.reposilite.audit.DEFAULT_PAGE_SIZE
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

internal class AuditQueryParserTest {

    private fun parse(vararg pairs: Pair<String, String>) =
        AuditQueryParser.parse { name -> pairs.firstOrNull { it.first == name }?.second }

    @Test
    fun `an empty request asks for the first default page of everything`() {
        val query = parse().get()

        assertThat(query.limit).isEqualTo(DEFAULT_PAGE_SIZE)
        assertThat(query.actor).isNull()
        assertThat(query.action).isNull()
        assertThat(query.after).isNull()
    }

    @Test
    fun `blank parameters count as absent`() {
        val query = parse("actor" to "  ", "q" to "", "from" to " ").get()

        assertThat(query.actor).isNull()
        assertThat(query.text).isNull()
        assertThat(query.from).isNull()
    }

    @Test
    fun `reads every filter`() {
        val query = parse(
            "actor" to "ci",
            "action" to "TOKEN_CREATED",
            "repository" to "releases",
            "q" to "bot",
            "from" to "2026-10-14T00:00:00Z",
            "to" to "2026-10-15T00:00:00Z",
            "limit" to "25",
            "cursor" to AuditCursor(1_000L, 7L).encode(),
        ).get()

        assertThat(query.actor).isEqualTo("ci")
        assertThat(query.action).isEqualTo(TOKEN_CREATED)
        assertThat(query.repository).isEqualTo("releases")
        assertThat(query.text).isEqualTo("bot")
        assertThat(query.from).isEqualTo(Instant.parse("2026-10-14T00:00:00Z").toEpochMilli())
        assertThat(query.to).isEqualTo(Instant.parse("2026-10-15T00:00:00Z").toEpochMilli())
        assertThat(query.limit).isEqualTo(25)
        assertThat(query.after).isEqualTo(AuditCursor(1_000L, 7L))
    }

    @Test
    fun `rejects an action that is not in the enum`() {
        val error = parse("action" to "DELETE_EVERYTHING").error

        assertThat(error).contains("action")
    }

    @ParameterizedTest
    @ValueSource(strings = ["yesterday", "2026-10-15", "15.10.2026"])
    fun `rejects a time that is not an ISO-8601 instant`(value: String) {
        assertThat(parse("from" to value).error).contains("from")
        assertThat(parse("to" to value).error).contains("to")
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "201", "-5", "many"])
    fun `rejects a limit outside the allowed range`(value: String) {
        assertThat(parse("limit" to value).error).contains("limit")
    }

    @Test
    fun `rejects a cursor it did not hand out`() {
        assertThat(parse("cursor" to "not-a-cursor!").error).contains("cursor")
    }

    @Test
    fun `rejects a range whose start is after its end`() {
        val error = parse("from" to "2026-10-15T00:00:00Z", "to" to "2026-10-14T00:00:00Z").error

        assertThat(error).contains("from")
    }

}
