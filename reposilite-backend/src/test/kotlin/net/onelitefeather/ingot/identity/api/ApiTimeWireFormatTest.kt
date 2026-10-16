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

package net.onelitefeather.ingot.identity.api

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.status.api.StatusSnapshot
import com.reposilite.statistics.api.IntervalRecord
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.api.AccessTokenDto
import net.onelitefeather.ingot.identity.login.api.LoginPrincipalDto
import net.onelitefeather.ingot.identity.login.api.SessionResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// Fixed values rather than the clock: the expectations hold at any time of day.
private val SEEN_AT: Instant = Instant.parse("2026-10-15T12:00:00.123Z")
private val CREATED_ON: LocalDate = LocalDate.of(2026, 10, 4)

/**
 * The wire format of the time fields the frontend reads. Jackson's defaults, as configured in
 * ReposiliteObjectMapper, decide it: an Instant is a decimal number of epoch seconds, a LocalDate
 * is a [year, month, day] array, and the Long timestamps of the status and statistics APIs are
 * epoch milliseconds. The frontend fixtures and the fake API must send the same shapes.
 */
internal class ApiTimeWireFormatTest {

    @Test
    fun `should send the creation time of a principal as epoch seconds`() {
        // given: a principal created at the fixed instant
        val principal = PrincipalDto(
            id = 2, kind = "USER", name = "alice", displayName = "Alice", disabled = false,
            createdAt = SEEN_AT, lastSeenAt = null,
        )

        // when: the principal is serialized
        val node = wire(principal).get("createdAt")

        // then: the Instant is a number of epoch seconds
        assertThat(node.isNumber).describedAs("createdAt is a number").isTrue
        assertThat(node.decimalValue()).describedAs("createdAt in epoch seconds").isEqualByComparingTo(epochSeconds(SEEN_AT))
    }

    @Test
    fun `should send the last use and the expiry of a token as epoch seconds`() {
        // given: a token last used at the fixed instant and expiring at the same instant
        val token = TokenDto(
            name = "deploy", description = "", createdAt = CREATED_ON, expiresAt = SEEN_AT,
            scopes = null, lastUsedAt = SEEN_AT, lastUsedIp = null,
        )

        // when: the token is serialized
        val node = wire(token)

        // then: both Instants are numbers of epoch seconds
        assertThat(node.get("expiresAt").decimalValue()).describedAs("expiresAt in epoch seconds").isEqualByComparingTo(epochSeconds(SEEN_AT))
        assertThat(node.get("lastUsedAt").decimalValue()).describedAs("lastUsedAt in epoch seconds").isEqualByComparingTo(epochSeconds(SEEN_AT))
    }

    @Test
    fun `should send the creation day of a token as a year, month and day array`() {
        // given: a token created on a LocalDate
        val token = TokenDto(
            name = "deploy", description = "", createdAt = CREATED_ON, expiresAt = null,
            scopes = null, lastUsedAt = null, lastUsedIp = null,
        )

        // when: the token is serialized
        val node = wire(token).get("createdAt")

        // then: the LocalDate is a [year, month, day] array
        assertThat(node.isArray).describedAs("createdAt is an array").isTrue
        assertThat(node.map { it.intValue() }).describedAs("createdAt as [year, month, day]").containsExactly(2026, 10, 4)
    }

    @Test
    fun `should send the expiry of a login session as epoch seconds`() {
        // given: a session that expires at the fixed instant
        val session = SessionResponse(
            principal = LoginPrincipalDto(id = 2, kind = "USER", name = "alice", displayName = "Alice"),
            expiresAt = SEEN_AT,
        )

        // when: the session is serialized
        val node = wire(session).get("expiresAt")

        // then: the Instant is a number of epoch seconds
        assertThat(node.decimalValue()).describedAs("expiresAt in epoch seconds").isEqualByComparingTo(epochSeconds(SEEN_AT))
    }

    @Test
    fun `should send the creation day and the expiry of a legacy token in the same shapes`() {
        // given: a legacy access token created on a LocalDate and expiring at the fixed instant
        val legacy = AccessTokenDto(
            identifier = AccessTokenIdentifier(value = 1),
            name = "legacy",
            createdAt = CREATED_ON,
            description = "",
            expiresAt = SEEN_AT,
        )

        // when: the legacy token is serialized
        val node = wire(legacy)

        // then: the creation day is an array and the expiry is epoch seconds
        assertThat(node.get("createdAt").map { it.intValue() }).describedAs("createdAt as [year, month, day]").containsExactly(2026, 10, 4)
        assertThat(node.get("expiresAt").decimalValue()).describedAs("expiresAt in epoch seconds").isEqualByComparingTo(epochSeconds(SEEN_AT))
    }

    @Test
    fun `should send the time of a status snapshot and a statistics interval as epoch milliseconds`() {
        // given: a status snapshot and an interval record at the fixed instant
        val millis = SEEN_AT.toEpochMilli()
        val snapshot = StatusSnapshot(at = millis, memory = 1, threads = 1)
        val interval = IntervalRecord(date = millis, count = 1)

        // when: both are serialized
        // then: the timestamps are epoch milliseconds, unlike the Instant fields above
        assertThat(wire(snapshot).get("at").longValue()).describedAs("status snapshot at").isEqualTo(millis)
        assertThat(wire(interval).get("date").longValue()).describedAs("statistics date").isEqualTo(millis)
    }

    private fun wire(value: Any): JsonNode =
        DEFAULT_OBJECT_MAPPER.readTree(DEFAULT_OBJECT_MAPPER.writeValueAsString(value))

    private fun epochSeconds(instant: Instant): BigDecimal =
        BigDecimal.valueOf(instant.epochSecond).add(BigDecimal.valueOf(instant.nano.toLong(), 9))

}
