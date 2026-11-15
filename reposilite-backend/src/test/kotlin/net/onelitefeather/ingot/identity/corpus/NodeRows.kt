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
package net.onelitefeather.ingot.identity.corpus

import net.onelitefeather.ingot.identity.model.Role

private const val READ_RELEASES = "ingot.repository.releases.read"
private const val ADMIN_ALL = "ingot.admin.*"
private const val ADMIN_USERS = "ingot.admin.users"
private const val ARTIFACT_NET_ONELITEFEATHER_READ = "ingot.artifact.net.onelitefeather.read"

/** Node rules decided per node, the precedence of levels, and the instance admin rows. */
internal fun nodeRows(): List<CorpusRow> = nodeShapeRows() + precedenceRows() + instanceAdminRows() + adminAreaRows()

private fun nodeShapeRows(): List<CorpusRow> = listOf(
    // Repository nodes: literal, wildcard in each position, specificity ties and role grants.
    decideRow(
        "node.repository.literal-allow", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, READ_RELEASES, "true REPOSITORY ingot.repository.releases.read",
    ),
    decideRow(
        "node.repository.other-repository-default-deny", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, "ingot.repository.snapshots.read", "false - -",
    ),
    decideRow(
        "node.repository.other-action-default-deny", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, "ingot.repository.releases.deploy", "false - -",
    ),
    decideRow(
        "node.repository.wildcard-repository-allow", "own allow *.read",
        { subjectOf(own(listOf(allow("ingot.repository.*.read")))) }, READ_RELEASES,
        "true REPOSITORY ingot.repository.*.read",
    ),
    decideRow(
        "node.repository.wildcard-action-allow", "own allow releases.*",
        { subjectOf(own(listOf(allow("ingot.repository.releases.*")))) }, "ingot.repository.releases.deploy",
        "true REPOSITORY ingot.repository.releases.*",
    ),
    decideRow(
        "node.repository.wildcard-both-allow", "own allow *.*",
        { subjectOf(own(listOf(allow("ingot.repository.*.*")))) }, "ingot.repository.releases.delete",
        "true REPOSITORY ingot.repository.*.*",
    ),
    decideRow(
        "node.repository.literal-beats-wildcard-denial", "own deny *.read, allow releases.read",
        { subjectOf(own(listOf(deny("ingot.repository.*.read"), allow(READ_RELEASES)))) }, READ_RELEASES,
        "true REPOSITORY ingot.repository.releases.read",
    ),
    decideRow(
        "node.repository.tie-denial-wins", "own allow *.read, deny releases.*",
        { subjectOf(own(listOf(allow("ingot.repository.*.read"), deny("ingot.repository.releases.*")))) }, READ_RELEASES,
        "false REPOSITORY ingot.repository.releases.*",
        note = "two nodes of equal specificity: the denial wins the tie",
    ),
    decideRow(
        "node.repository.role-grant-tie-denial-wins", "team deny releases.read and grant READ on releases",
        { subjectOf(team(rules = listOf(deny(READ_RELEASES, by = TEAM)), grants = listOf(grant(Role.READ, "releases")))) },
        READ_RELEASES, "false REPOSITORY ingot.repository.releases.read",
        note = "a role grant counts as a literal repository node, so the denial wins the tie",
    ),
    decideRow(
        "node.repository.role-grant-beats-wildcard-denial", "team deny *.* and grant READ on releases",
        { subjectOf(team(rules = listOf(deny("ingot.repository.*.*", by = TEAM)), grants = listOf(grant(Role.READ, "releases")))) },
        READ_RELEASES, "true GRANT -",
    ),
    decideRow(
        "node.repository.legacy-route-tie-denial-wins", "team deny releases.read and legacy route /releases",
        { subjectOf(team(rules = listOf(deny(READ_RELEASES, by = TEAM)), grants = listOf(legacy("/releases", Role.LEGACY_READ)))) },
        READ_RELEASES, "false REPOSITORY ingot.repository.releases.read",
    ),
    decideRow(
        "node.repository.legacy-path-route-does-not-decide-repository", "team legacy route /releases/com",
        { subjectOf(team(grants = listOf(legacy("/releases/com", Role.LEGACY_READ)))) }, READ_RELEASES, "false - -",
        note = "a path-scoped legacy route covers no repository node",
    ),
    decideRow(
        "node.repository.star-allow-makes-instance-admin", "own allow *",
        { subjectOf(own(listOf(allow("*")))) }, READ_RELEASES, "true INSTANCE_ADMIN *",
        note = "the node * covers the instance admin node, so it also makes the subject an instance admin",
    ),
    decideRow(
        "node.repository.star-deny-denies-repository", "own deny *",
        { subjectOf(own(listOf(deny("*")))) }, READ_RELEASES, "false EVERYTHING *",
    ),
    decideRow(
        "node.repository.star-deny-overridden-by-instance-admin", "own deny *, allow ingot.admin.*",
        { subjectOf(own(listOf(deny("*"), allow(ADMIN_ALL)))) }, READ_RELEASES, "true INSTANCE_ADMIN ingot.admin.*",
        note = "an instance admin is not restricted by a repository denial",
    ),
    // Artifact nodes: subtrees, action wildcards, qualified and unqualified forms, and what grants can and cannot decide.
    decideRow(
        "node.artifact.subtree-decided-as-node", "own allow net.onelitefeather.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_ONELITEFEATHER_READ)))) }, ARTIFACT_NET_ONELITEFEATHER_READ,
        "true ARTIFACT ingot.artifact.net.onelitefeather.read",
    ),
    decideRow(
        "node.artifact.wider-node-not-covered", "own allow net.onelitefeather.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_ONELITEFEATHER_READ)))) }, "ingot.artifact.net.read", "false - -",
    ),
    decideRow(
        "node.artifact.sibling-group-not-covered", "own allow net.onelitefeather.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_ONELITEFEATHER_READ)))) }, "ingot.artifact.net.onelitefeatherX.read", "false - -",
        note = "matching compares whole path segments",
    ),
    decideRow(
        "node.artifact.action-wildcard-covers-read", "own allow net.onelitefeather.*",
        { subjectOf(own(listOf(allow("ingot.artifact.net.onelitefeather.*")))) }, ARTIFACT_NET_ONELITEFEATHER_READ,
        "true ARTIFACT ingot.artifact.net.onelitefeather.*",
    ),
    decideRow(
        "node.artifact.repository-wide-grant-does-not-decide-unqualified-node", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) }, "ingot.artifact.net.read", "false - -",
        note = "a repository grant decides repository nodes only",
    ),
    decideRow(
        "node.artifact.qualified-rule-does-not-decide-unqualified-node", "own allow releases artifact net.onelitefeather.read",
        { subjectOf(own(listOf(allow("ingot.repository.releases.artifact.net.onelitefeather.read")))) },
        ARTIFACT_NET_ONELITEFEATHER_READ, "false - -",
    ),
    decideRow(
        "node.artifact.qualified-decides-repository-subtree", "own allow releases artifact net.onelitefeather.read",
        { subjectOf(own(listOf(allow("ingot.repository.releases.artifact.net.onelitefeather.read")))) },
        "ingot.repository.releases.artifact.net.onelitefeather.read",
        "true ARTIFACT ingot.repository.releases.artifact.net.onelitefeather.read",
    ),
    decideRow(
        "node.artifact.qualified-subtree-under-repository-grant", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) },
        "ingot.repository.releases.artifact.net.read", "true GRANT -",
    ),
)

private fun precedenceRows(): List<CorpusRow> = listOf(
    decideRow(
        "precedence.personal-wildcard-denial-beats-team-allow", "own deny *.read, inherited allow releases.read",
        { subjectOf(own(listOf(deny("ingot.repository.*.read"))), inherited(1, listOf(allow(READ_RELEASES, by = TEAM)))) },
        READ_RELEASES, "false REPOSITORY ingot.repository.*.read",
        note = "known surprise: a personal deny of a wildcard decides before a team allow of the literal node, because the" +
            " nearer level decides first and specificity only orders nodes within one level",
    ),
    decideRow(
        "precedence.personal-literal-allow-beats-team-wildcard-denial", "own allow releases.read, team deny *.read",
        { subjectOf(own(listOf(allow(READ_RELEASES))), team(listOf(deny("ingot.repository.*.read", by = TEAM)))) },
        READ_RELEASES, "true REPOSITORY ingot.repository.releases.read",
    ),
    decideRow(
        "precedence.team-allow-when-personal-silent", "own empty, team allow releases.read",
        { subjectOf(own(), team(listOf(allow(READ_RELEASES, by = TEAM)))) }, READ_RELEASES,
        "true REPOSITORY ingot.repository.releases.read",
    ),
    decideRow(
        "precedence.inherited-allow-when-nearer-levels-silent", "own and team empty, depth 2 allow *.read",
        { subjectOf(own(), team(), inherited(2, listOf(allow("ingot.repository.*.read", by = TEAM)))) }, READ_RELEASES,
        "true REPOSITORY ingot.repository.*.read",
    ),
    decideRow(
        "precedence.nearer-denial-beats-farther-allow", "depth 1 deny releases.read, depth 2 allow releases.read",
        { subjectOf(inherited(1, listOf(deny(READ_RELEASES, by = TEAM))), inherited(2, listOf(allow(READ_RELEASES, by = TEAM)))) },
        READ_RELEASES, "false REPOSITORY ingot.repository.releases.read",
    ),
    decideRow(
        "precedence.token-list-narrows-personal-allow", "own allow releases.read, token lists snapshots.read",
        { subjectOf(own(listOf(allow(READ_RELEASES))), token = tokenOf(allow("ingot.repository.snapshots.read"))) }, READ_RELEASES,
        "false - -",
    ),
    decideRow(
        "precedence.token-list-cannot-widen-owner", "own empty, token lists releases.read",
        { subjectOf(own(), token = tokenOf(allow(READ_RELEASES))) }, READ_RELEASES, "false - -",
    ),
    decideRow(
        "precedence.token-denial-narrows-owner-allow", "own allow *.read, token denies releases.read",
        { subjectOf(own(listOf(allow("ingot.repository.*.read"))), token = tokenOf(deny(READ_RELEASES))) }, READ_RELEASES,
        "false REPOSITORY ingot.repository.releases.read",
    ),
)

private fun instanceAdminRows(): List<CorpusRow> = listOf(
    instanceAdminRow("instance-admin.own-rule", "own allow ingot.admin.*", { subjectOf(own(listOf(allow(ADMIN_ALL)))) }, true),
    instanceAdminRow(
        "instance-admin.personal-denial-beats-inherited-allow", "own deny ingot.admin.*, inherited allow ingot.admin.*",
        { subjectOf(own(listOf(deny(ADMIN_ALL))), inherited(1, listOf(allow(ADMIN_ALL, by = TEAM)))) }, false,
    ),
    instanceAdminRow(
        "instance-admin.team-rule-at-distance-zero", "team allow ingot.admin.*",
        { subjectOf(team(listOf(allow(ADMIN_ALL, by = TEAM)))) }, true,
    ),
    instanceAdminRow(
        "instance-admin.token-without-admin-node-removes-admin", "own allow ingot.admin.*, token lists repository reads",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), token = tokenOf(allow("ingot.repository.*.read"))) }, false,
    ),
    instanceAdminRow(
        "instance-admin.token-with-admin-node-keeps-admin", "own allow ingot.admin.*, token lists ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), token = tokenOf(allow(ADMIN_ALL))) }, true,
    ),
    instanceAdminRow(
        "instance-admin.disabled-never-admin", "disabled, own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), disabled = true) }, false,
    ),
    instanceAdminRow(
        "instance-admin.role-grant-is-not-admin", "team grant ADMIN on releases",
        { subjectOf(team(grants = listOf(grant(Role.ADMIN, "releases")))) }, false,
    ),
    instanceAdminRow(
        "instance-admin.area-is-not-instance-admin", "own allow ingot.admin.users",
        { subjectOf(own(listOf(allow(ADMIN_USERS)))) }, false,
    ),
    instanceAdminRow(
        "instance-admin.star-allow-is-admin", "own allow *",
        { subjectOf(own(listOf(allow("*")))) }, true,
    ),
    instanceAdminRow(
        "instance-admin.star-deny-loses-to-admin-allow", "own deny *, allow ingot.admin.*",
        { subjectOf(own(listOf(deny("*"), allow(ADMIN_ALL)))) }, true,
    ),
    instanceAdminRow(
        "instance-admin.legacy-root-route-is-not-admin", "team legacy route / with LEGACY_WRITE",
        { subjectOf(team(grants = listOf(legacy("/", Role.LEGACY_WRITE)))) }, false,
    ),
)

private fun adminAreaRows(): List<CorpusRow> = listOf(
    adminAreaRow("admin-area.instance-admin-needs-star-admin", "own allow ingot.admin.users", { subjectOf(own(listOf(allow(ADMIN_USERS)))) }, null, false),
    adminAreaRow("admin-area.instance-admin-from-wildcard", "own allow ingot.admin.*", { subjectOf(own(listOf(allow(ADMIN_ALL)))) }, null, true),
    adminAreaRow("admin-area.area-from-wildcard", "own allow ingot.admin.*", { subjectOf(own(listOf(allow(ADMIN_ALL)))) }, "teams", true),
    adminAreaRow(
        "admin-area.instance-admin-ignores-area-denial", "own allow ingot.admin.*, deny ingot.admin.users",
        { subjectOf(own(listOf(allow(ADMIN_ALL), deny(ADMIN_USERS)))) }, "users", true,
        note = "known surprise: an instance admin ignores an area denial, only token narrowing restricts it",
    ),
    adminAreaRow(
        "admin-area.area-denied-without-instance-admin", "own deny ingot.admin.users, team allow ingot.admin.users",
        { subjectOf(own(listOf(deny(ADMIN_USERS))), team(listOf(allow(ADMIN_USERS, by = TEAM)))) }, "users", false,
    ),
    adminAreaRow(
        "admin-area.token-narrowing-removes-area", "own allow ingot.admin.*, token lists repository reads",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), token = tokenOf(allow("ingot.repository.*.read"))) }, "users", false,
    ),
    adminAreaRow(
        "admin-area.inherited-area-allow", "inherited allow ingot.admin.users",
        { subjectOf(inherited(1, listOf(allow(ADMIN_USERS, by = TEAM)))) }, "users", true,
    ),
    adminAreaRow(
        "admin-area.disabled-has-no-area", "disabled, own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), disabled = true) }, "users", false,
    ),
    adminAreaRow(
        "admin-area.other-area-not-granted", "own allow ingot.admin.users",
        { subjectOf(own(listOf(allow(ADMIN_USERS)))) }, "teams", false,
    ),
    adminAreaRow(
        "admin-area.star-allows-instance-admin", "own allow *",
        { subjectOf(own(listOf(allow("*")))) }, null, true,
    ),
    adminAreaRow(
        "admin-area.role-grant-is-not-an-area", "team grant ADMIN on releases",
        { subjectOf(team(grants = listOf(grant(Role.ADMIN, "releases")))) }, "users", false,
    ),
)
