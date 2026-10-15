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

package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.cleanup.application.CleanupSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

internal class CleanupSettingsTest {

    private fun policy(
        id: String = "weekly",
        keepLatest: Int? = 2,
        olderThanDays: Int? = null,
        notDownloadedForDays: Int? = null,
        groupFilter: String? = null,
        schedule: String = "manual",
    ) = CleanupPolicy(
        id = id,
        enabled = true,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = keepLatest,
        olderThanDays = olderThanDays,
        notDownloadedForDays = notDownloadedForDays,
        groupFilter = groupFilter,
        schedule = schedule,
    )

    @Test
    fun `should have no policies by default`() {
        // given: settings with their defaults
        // when: the default settings are created
        // then: no policy exists, so nothing is ever deleted until an admin configures one
        assertTrue(CleanupSettings().policies.isEmpty())
    }

    @Test
    fun `should default a policy to disabled, manual and repository free`() {
        // given: a policy with only an id
        val policy = CleanupPolicy(id = "defaults", keepLatest = 1)

        // when: the defaults are read
        // then: nothing is switched on and nothing is selected
        assertEquals(false, policy.enabled)
        assertEquals("manual", policy.schedule)
        assertTrue(policy.repositories.isEmpty())
        assertEquals(CleanupScope.RELEASES, policy.scope)
    }

    @Test
    fun `should accept a policy with a count limit`() {
        // given: a policy that keeps two versions
        // when: its violations are read
        // then: it is accepted
        assertTrue(CleanupSettings(listOf(policy(keepLatest = 2))).violations().isEmpty())
    }

    @Test
    fun `should accept a policy with an age limit only`() {
        // given: a policy with only an age limit
        // when: its violations are read
        // then: it is accepted
        assertTrue(CleanupSettings(listOf(policy(keepLatest = null, olderThanDays = 30))).violations().isEmpty())
    }

    @Test
    fun `should reject a policy without any criterion because it would delete everything`() {
        // given: a policy with no count and no age limit
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(keepLatest = null))).violations()

        // then: the violation names the policy
        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("weekly"), violations.single())
    }

    @Test
    fun `should reject a count of zero`() {
        // given: a policy that keeps zero versions
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(keepLatest = 0))).violations()

        // then: the value is rejected
        assertTrue(violations.any { it.contains("keepLatest") }, violations.toString())
    }

    @Test
    fun `should reject a negative age limit`() {
        // given: a policy with a negative age limit
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(olderThanDays = -3))).violations()

        // then: the value is rejected
        assertTrue(violations.any { it.contains("olderThanDays") }, violations.toString())
    }

    @Test
    fun `should reject an id that contains a slash`() {
        // given: an id that would break the URL of the policy
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(id = "a/b"))).violations()

        // then: the id is rejected
        assertTrue(violations.any { it.contains("a/b") }, violations.toString())
    }

    @Test
    fun `should reject an empty id`() {
        // given: a policy without an id
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(id = ""))).violations()

        // then: the empty id is rejected
        assertTrue(violations.isNotEmpty())
    }

    @Test
    fun `should reject two policies with the same id`() {
        // given: two policies sharing an id
        // when: the settings are checked
        val violations = CleanupSettings(listOf(policy(id = "dup"), policy(id = "dup"))).violations()

        // then: the duplicate is reported
        assertTrue(violations.any { it.contains("dup") && it.contains("more than once") }, violations.toString())
    }

    @Test
    fun `should reject a group filter that is not a regular expression`() {
        // given: a group filter with an unclosed group
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(groupFilter = "net/(example"))).violations()

        // then: the filter is rejected
        assertTrue(violations.any { it.contains("groupFilter") }, violations.toString())
    }

    @Test
    fun `should accept a group filter that is a regular expression`() {
        // given: a group filter that matches a whole group
        // when: its violations are read
        // then: the filter is accepted
        assertTrue(CleanupSettings(listOf(policy(groupFilter = "net/onelitefeather/.*"))).violations().isEmpty())
    }

    @Test
    fun `should accept manual and daily times as schedules`() {
        // given: a manual policy and a daily one
        val manual = policy(id = "manual", schedule = "manual")
        val daily = policy(id = "daily", schedule = "04:30")

        // when: their violations are read
        // then: both are accepted and the daily time is read as 04:30
        assertTrue(CleanupSettings(listOf(manual, daily)).violations().isEmpty())
        assertEquals(LocalTime.of(4, 30), daily.scheduledTime())
        assertNull(manual.scheduledTime())
    }

    @Test
    fun `should reject a schedule that is neither manual nor a time of day`() {
        // given: a schedule of 25:00
        // when: its violations are read
        val violations = CleanupSettings(listOf(policy(schedule = "25:00"))).violations()

        // then: the schedule is rejected
        assertTrue(violations.any { it.contains("schedule") }, violations.toString())
    }

}
