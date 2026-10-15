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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.journalist.Journalist
import net.onelitefeather.ingot.identity.evaluation.ScopeRules
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.NODE_SUBJECT_TEAM
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.Role
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/**
 * Moves the rights of the previous version into permission nodes (identity model, permission nodes, decision D7), once.
 *
 * - Repository-wide team grants become the node list of their team, per role (READ, DEPLOY, MAINTAIN, ADMIN).
 * - Instance roles become the node `ingot.admin.*` of their principal or team.
 * - Token scopes become the node list of the token. The scopes stay in their column.
 *
 * Path-scoped and legacy grants stay where they are. The whole run is one transaction that starts by claiming the marker,
 * so concurrent starts serialize on it, and a failed check rolls everything back, marker included.
 */
internal class PermissionNodeMigration(
    private val database: Database,
    private val journalist: Journalist,
) {

    companion object {
        const val MIGRATION_NAME: String = "002-permission-nodes"
    }

    fun migrate(): MigrationOutcome =
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(*IDENTITY_TABLES)
            val claimed = IdentityMigrationTable.insertIgnore {
                it[IdentityMigrationTable.name] = MIGRATION_NAME
                it[IdentityMigrationTable.appliedAt] = Instant.now()
            }.insertedCount
            if (claimed == 0) {
                return@transaction MigrationOutcome.ALREADY_APPLIED
            }

            val kept = convertRepositoryGrants()
            val instanceRoles = convertInstanceRoles()
            val tokens = convertTokenScopes()
            verify(kept.size)

            journalist.logger.info(
                "Identity | permission nodes: ${kept.converted} repository grant(s), $instanceRoles instance role(s) " +
                    "and $tokens token scope list(s) converted, ${kept.size} grant(s) kept",
            )
            MigrationOutcome.MIGRATED
        }

    private class PendingGrant(val id: Int, val teamId: Int, val repository: String?, val role: Role)

    private class GrantOutcome(val converted: Int, val size: Int)

    /**
     * Converts the repository-wide grants of the teams and removes their rows. A grant whose repository name is not a valid
     * node stays a grant: the grant rules still apply it, so nothing is lost.
     */
    private fun convertRepositoryGrants(): GrantOutcome {
        var converted = 0
        var kept = 0
        val grants = TeamGrantTable.selectAll()
            .where { TeamGrantTable.pathPrefix.isNull() }
            .map { PendingGrant(it[TeamGrantTable.id].value, it[TeamGrantTable.teamId].value, it[TeamGrantTable.repository], Role.valueOf(it[TeamGrantTable.role])) }
            .filterNot { it.role.legacy }

        grants.forEach { grant ->
            val nodes = nodesOf(grant.repository ?: NodePattern.WILDCARD, grant.role)
            if (nodes.any { NodePattern.problemOf(it) != null }) {
                journalist.logger.warn("Identity | repository '${grant.repository}' cannot be written as a node, its ${grant.role.name} grant is kept")
                kept++
                return@forEach
            }
            nodes.forEach { insertNodeIfAbsent(NODE_SUBJECT_TEAM, grant.teamId, it) }
            TeamGrantTable.deleteWhere { TeamGrantTable.id eq grant.id }
            converted++
        }
        return GrantOutcome(converted, kept)
    }

    /** Converts each instance role into the admin node of its subject, and removes the rows. */
    private fun convertInstanceRoles(): Int {
        val rows = InstanceRoleTable.selectAll().map { it[InstanceRoleTable.principalId]?.value to it[InstanceRoleTable.teamId]?.value }
        rows.forEach { (principalId, teamId) ->
            when {
                principalId != null -> {
                    val kind = PrincipalTable.selectAll().where { PrincipalTable.id eq principalId }.single()[PrincipalTable.kind]
                    insertNodeIfAbsent(kind, principalId, NodePattern.INSTANCE_ADMIN.text)
                }
                teamId != null -> insertNodeIfAbsent(NODE_SUBJECT_TEAM, teamId, NodePattern.INSTANCE_ADMIN.text)
            }
        }
        InstanceRoleTable.deleteAll()
        return rows.size
    }

    /** Writes the node list of every token that has scopes. */
    private fun convertTokenScopes(): Int {
        var converted = 0
        PrincipalTokenTable.selectAll().forEach { row ->
            val scopes: Set<Action> = IdentityStore.decodeScopes(row[PrincipalTokenTable.scopes]) ?: return@forEach
            PrincipalTokenTable.update(where = { PrincipalTokenTable.id eq row[PrincipalTokenTable.id].value }, body = {
                it[PrincipalTokenTable.nodes] = IdentityStore.encodeNodes(ScopeRules.nodesOf(scopes))
            })
            converted++
        }
        return converted
    }

    /**
     * The repository-wide grants that remain are exactly the ones that could not be converted, and no instance role row is
     * left. A mismatch throws, which rolls the whole migration back.
     */
    private fun verify(expectedKept: Int) {
        val remaining = TeamGrantTable.selectAll()
            .where { TeamGrantTable.pathPrefix.isNull() }
            .count { !Role.valueOf(it[TeamGrantTable.role]).legacy }
        check(remaining == expectedKept) { "Identity migration left $remaining repository-wide grant(s), expected $expectedKept" }
        check(InstanceRoleTable.selectAll().count() == 0L) { "Identity migration left instance role rows behind" }
    }

    /** The node texts a repository-wide grant of [role] on [repository] stands for. */
    private fun nodesOf(repository: String, role: Role): List<String> {
        val actions = when (role) {
            Role.READ -> listOf("read")
            Role.DEPLOY -> listOf("read", "deploy")
            Role.MAINTAIN -> listOf("read", "deploy", "delete")
            Role.ADMIN -> listOf("read", "deploy", "delete", "manage")
            Role.LEGACY_READ, Role.LEGACY_WRITE -> emptyList()
        }
        return actions.map { "ingot.repository.$repository.$it" }
    }

    /** Writes one allowed node unless the subject already holds it. */
    private fun insertNodeIfAbsent(subjectKind: String, subjectId: Int, node: String) {
        val exists = PermissionNodeTable.selectAll()
            .where { (PermissionNodeTable.subjectKind eq subjectKind) and (PermissionNodeTable.subjectId eq subjectId) and (PermissionNodeTable.node eq node) }
            .any()
        if (exists) return
        PermissionNodeTable.insert {
            it[PermissionNodeTable.subjectKind] = subjectKind
            it[PermissionNodeTable.subjectId] = subjectId
            it[PermissionNodeTable.node] = node
            it[PermissionNodeTable.allowed] = true
            it[PermissionNodeTable.createdAt] = Instant.now()
        }
    }
}
