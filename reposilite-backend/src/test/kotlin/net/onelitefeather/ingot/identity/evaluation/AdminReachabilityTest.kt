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
package net.onelitefeather.ingot.identity.evaluation

import net.onelitefeather.ingot.identity.model.NodeRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The last-admin rule (identity model, section 7.4 and decision D12): a usable admin is an enabled principal with an
 * unexpired token whose node list still allows `ingot.admin.*`, and that is an admin through its nodes or its teams.
 * Pure: every test states its own input and the clock is a value.
 */
internal class AdminReachabilityTest {

    private val now: Instant = Instant.parse("2026-10-15T10:00:00Z")
    private val admin = NodeRule("ingot.admin.*", allowed = true)

    private fun principal(id: Int, disabled: Boolean = false) = AdminReachability.Principal(id, disabled)

    private fun token(id: Int, principalId: Int, expiresAt: Instant? = null, nodes: List<NodeRule>? = null) =
        AdminReachability.Token(id, principalId, expiresAt, nodes)

    private fun input(
        principals: List<AdminReachability.Principal> = emptyList(),
        principalRules: Map<Int, List<NodeRule>> = emptyMap(),
        teamRules: Map<Int, List<NodeRule>> = emptyMap(),
        parents: Map<Int, Set<Int>> = emptyMap(),
        memberships: List<AdminReachability.Membership> = emptyList(),
        tokens: List<AdminReachability.Token> = emptyList(),
    ) = AdminReachability.Input(principals, principalRules, teamRules, parents, memberships, tokens)

    @Test
    fun `a principal with the admin node and a token is a usable admin`() {
        val data = input(
            principals = listOf(principal(1)),
            principalRules = mapOf(1 to listOf(admin)),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).containsExactly(1)
    }

    @Test
    fun `a principal without a token is not usable`() {
        val data = input(principals = listOf(principal(1)), principalRules = mapOf(1 to listOf(admin)))

        assertThat(AdminReachability.usableAdmins(data, now)).describedAs("usable admins").isEmpty()
    }

    @Test
    fun `a principal whose token has expired is not usable`() {
        val data = input(
            principals = listOf(principal(1)),
            principalRules = mapOf(1 to listOf(admin)),
            tokens = listOf(token(10, 1, expiresAt = Instant.parse("2026-01-01T00:00:00Z"))),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).describedAs("usable admins").isEmpty()
    }

    @Test
    fun `a disabled principal is not usable`() {
        val data = input(
            principals = listOf(principal(1, disabled = true)),
            principalRules = mapOf(1 to listOf(admin)),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).describedAs("usable admins").isEmpty()
    }

    @Test
    fun `a member of a team with the admin node is a usable admin`() {
        val data = input(
            principals = listOf(principal(1)),
            teamRules = mapOf(7 to listOf(admin)),
            memberships = listOf(AdminReachability.Membership(teamId = 7, principalId = 1)),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).containsExactly(1)
    }

    @Test
    fun `a member of a team that inherits the admin node is a usable admin`() {
        val data = input(
            principals = listOf(principal(1)),
            teamRules = mapOf(2 to listOf(admin)),
            parents = mapOf(1 to setOf(2)),
            memberships = listOf(AdminReachability.Membership(teamId = 1, principalId = 1)),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).containsExactly(1)
    }

    @Test
    fun `a token that narrows away the admin node makes no usable admin`() {
        val data = input(
            principals = listOf(principal(1)),
            principalRules = mapOf(1 to listOf(admin)),
            tokens = listOf(token(10, 1, nodes = listOf(NodeRule("ingot.repository.*.read", allowed = true)))),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).describedAs("usable admins").isEmpty()
    }

    @Test
    fun `the star node counts as the admin node`() {
        val data = input(
            principals = listOf(principal(1)),
            principalRules = mapOf(1 to listOf(NodeRule("*", allowed = true))),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).containsExactly(1)
    }

    @Test
    fun `a single admin area is not the instance admin`() {
        val data = input(
            principals = listOf(principal(1)),
            principalRules = mapOf(1 to listOf(NodeRule("ingot.admin.users", allowed = true))),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).describedAs("usable admins").isEmpty()
    }

    @Test
    fun `a team without the admin node makes its members no admins`() {
        val data = input(
            principals = listOf(principal(1)),
            teamRules = mapOf(7 to listOf(NodeRule("ingot.repository.*.read", allowed = true))),
            memberships = listOf(AdminReachability.Membership(teamId = 7, principalId = 1)),
            tokens = listOf(token(10, 1)),
        )

        assertThat(AdminReachability.usableAdmins(data, now)).describedAs("usable admins").isEmpty()
    }
}
