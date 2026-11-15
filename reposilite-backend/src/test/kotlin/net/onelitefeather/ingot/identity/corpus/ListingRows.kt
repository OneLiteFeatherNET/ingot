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

private const val ADMIN_ALL = "ingot.admin.*"
private const val READ_RELEASES = "ingot.repository.releases.read"
private const val ARTIFACT_NET_READ = "ingot.artifact.net.read"

/** The listing predicates: what the repository list, the hidden repositories and the artifact listing show. */
internal fun listingRows(): List<CorpusRow> = canSeeRows() + canListHiddenRows() + artifactRuleRows() + subtreeRows()

private fun canSeeRows(): List<CorpusRow> = listOf(
    canSeeRow(
        "can-see.repository-read-grant", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) }, "/releases", true,
    ),
    canSeeRow(
        "can-see.other-repository", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) }, "/snapshots", false,
    ),
    canSeeRow(
        "can-see.artifact-subtree-alone", "own allow ingot.artifact.net.onelitefeather.read",
        { subjectOf(own(listOf(allow("ingot.artifact.net.onelitefeather.read")))) }, "/releases", true,
        note = "a readable subtree makes its repository visible",
    ),
    canSeeRow(
        "can-see.legacy-path-scoped-route", "team legacy route /releases/com",
        { subjectOf(team(grants = listOf(legacy("/releases/com", Role.LEGACY_READ)))) }, "/releases", true,
        note = "the legacy check is a prefix check on the fragment, so a path-scoped route shows the repository",
    ),
    canSeeRow(
        "can-see.path-scoped-grant-shows-repository", "team grant READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) }, "/releases", true,
    ),
    canSeeRow(
        "can-see.personal-denial-ignored-when-grant-reaches", "own deny releases.read, inherited grant READ on releases",
        { subjectOf(own(listOf(deny(READ_RELEASES))), inherited(1, grants = listOf(grant(Role.READ, "releases")))) },
        "/releases", true,
        note = "known surprise: a grant that reaches the repository shows it before any personal denial is consulted, while" +
            " allows of the same subject deny the request",
    ),
    canSeeRow(
        "can-see.token-narrowing-hides", "team grant READ on releases, token lists snapshots.read",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases"))), token = tokenOf(allow("ingot.repository.snapshots.read"))) },
        "/releases", false,
    ),
    canSeeRow(
        "can-see.instance-admin-sees-any-repository", "own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL)))) }, "/nonexistent", true,
    ),
    canSeeRow(
        "can-see.disabled-sees-nothing", "disabled, team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases"))), disabled = true) }, "/releases", false,
    ),
    canSeeRow("can-see.no-grant-sees-nothing", "no rules", { subjectOf(own()) }, "/releases", false),
    canSeeRow(
        "can-see.denial-without-grant-hides", "own deny releases.read",
        { subjectOf(own(listOf(deny(READ_RELEASES)))) }, "/releases", false,
    ),
    canSeeRow(
        "can-see.node-allow-shows-repository", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, "/releases", true,
    ),
    canSeeRow(
        "can-see.fragment-without-slash-misses-legacy-route", "team legacy route /releases/com",
        { subjectOf(team(grants = listOf(legacy("/releases/com", Role.LEGACY_READ)))) }, "releases", false,
        note = "the legacy prefix check needs the leading slash of the fragment",
    ),
)

private fun canListHiddenRows(): List<CorpusRow> = listOf(
    canListHiddenRow(
        "can-list-hidden.instance-admin", "own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL)))) }, "releases", true,
    ),
    canListHiddenRow(
        "can-list-hidden.node-read-allow", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, "releases", true,
    ),
    canListHiddenRow(
        "can-list-hidden.role-read-grant", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) }, "releases", true,
    ),
    canListHiddenRow(
        "can-list-hidden.legacy-route-never-lists", "team legacy route /releases LEGACY_READ",
        { subjectOf(team(grants = listOf(legacy("/releases", Role.LEGACY_READ)))) }, "releases", false,
        note = "legacy grants never list a hidden repository, as before",
    ),
    canListHiddenRow(
        "can-list-hidden.path-scoped-read-lists", "team grant READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) }, "releases", true,
    ),
    canListHiddenRow(
        "can-list-hidden.deploy-role-lists", "team grant DEPLOY on releases",
        { subjectOf(team(grants = listOf(grant(Role.DEPLOY, "releases")))) }, "releases", true,
    ),
    canListHiddenRow(
        "can-list-hidden.other-repository", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) }, "snapshots", false,
    ),
    canListHiddenRow(
        "can-list-hidden.personal-denial-ignored-by-grant", "own deny releases.read, inherited grant READ on releases",
        { subjectOf(own(listOf(deny(READ_RELEASES))), inherited(1, grants = listOf(grant(Role.READ, "releases")))) },
        "releases", true,
        note = "known surprise: the grant loop of the listing does not consult denials",
    ),
    canListHiddenRow(
        "can-list-hidden.token-narrowing", "team grant READ on releases, token lists snapshots.read",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases"))), token = tokenOf(allow("ingot.repository.snapshots.read"))) },
        "releases", false,
    ),
    canListHiddenRow(
        "can-list-hidden.disabled", "disabled, team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases"))), disabled = true) }, "releases", false,
    ),
    canListHiddenRow(
        "can-list-hidden.artifact-subtree-does-not-list", "own allow ingot.artifact.net.onelitefeather.read",
        { subjectOf(own(listOf(allow("ingot.artifact.net.onelitefeather.read")))) }, "releases", false,
        note = "a readable subtree shows the repository but does not list it when hidden",
    ),
)

private fun artifactRuleRows(): List<CorpusRow> = listOf(
    artifactRuleRow(
        "artifact-rule.unqualified-read", "own allow ingot.artifact.net.onelitefeather.read",
        { subjectOf(own(listOf(allow("ingot.artifact.net.onelitefeather.read")))) }, "releases", true,
    ),
    artifactRuleRow(
        "artifact-rule.qualified-own-repository", "own allow releases artifact net.read",
        { subjectOf(own(listOf(allow("ingot.repository.releases.artifact.net.read")))) }, "releases", true,
    ),
    artifactRuleRow(
        "artifact-rule.qualified-other-repository", "own allow releases artifact net.read",
        { subjectOf(own(listOf(allow("ingot.repository.releases.artifact.net.read")))) }, "snapshots", false,
    ),
    artifactRuleRow(
        "artifact-rule.deploy-is-not-read", "own allow ingot.artifact.net.deploy",
        { subjectOf(own(listOf(allow("ingot.artifact.net.deploy")))) }, "releases", false,
    ),
    artifactRuleRow(
        "artifact-rule.wildcard-action-counts", "own allow ingot.artifact.net.*",
        { subjectOf(own(listOf(allow("ingot.artifact.net.*")))) }, "releases", true,
    ),
    artifactRuleRow(
        "artifact-rule.denial-counts", "own deny ingot.artifact.net.read",
        { subjectOf(own(listOf(deny(ARTIFACT_NET_READ)))) }, "releases", true,
        note = "a denying artifact rule counts as a read rule, so the listing takes the filtered path",
    ),
    artifactRuleRow(
        "artifact-rule.repository-node-does-not-count", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, "releases", false,
    ),
    artifactRuleRow(
        "artifact-rule.token-rule-counts", "token lists ingot.artifact.net.read",
        { subjectOf(token = tokenOf(allow(ARTIFACT_NET_READ))) }, "releases", true,
    ),
    artifactRuleRow(
        "artifact-rule.grant-does-not-count", "team grant READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) }, "releases", false,
    ),
    artifactRuleRow(
        "artifact-rule.disabled", "disabled, own allow ingot.artifact.net.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_READ))), disabled = true) }, "releases", false,
    ),
)

private fun subtreeRows(): List<CorpusRow> = listOf(
    subtreeRow(
        "subtrees.single-subtree", "own allow ingot.artifact.net.onelitefeather.read",
        { subjectOf(own(listOf(allow("ingot.artifact.net.onelitefeather.read")))) }, "releases", "net/onelitefeather",
    ),
    subtreeRow(
        "subtrees.whole-repository-group-wildcard", "own allow ingot.artifact.*.read",
        { subjectOf(own(listOf(allow("ingot.artifact.*.read")))) }, "releases", "<root>",
        note = "the groupId * covers every path, so the root of the repository is the subtree",
    ),
    subtreeRow(
        "subtrees.nested-roots-in-rule-order", "own allow net.read, allow net.onelitefeather.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_READ), allow("ingot.artifact.net.onelitefeather.read")))) },
        "releases", "net;net/onelitefeather",
    ),
    subtreeRow(
        "subtrees.denial-at-same-root-removes-it", "own allow net.read, deny net.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_READ), deny(ARTIFACT_NET_READ)))) }, "releases", "<none>",
        note = "the full decision of the root decides it, and a denial at the same node wins the tie",
    ),
    subtreeRow(
        "subtrees.denial-of-subtree-keeps-parent", "own allow net.read, deny net.onelitefeather.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_READ), deny("ingot.artifact.net.onelitefeather.read")))) },
        "releases", "net",
    ),
    subtreeRow(
        "subtrees.other-repository-excluded", "own allow releases artifact net.read",
        { subjectOf(own(listOf(allow("ingot.repository.snapshots.artifact.net.read")))) }, "releases", "<none>",
    ),
    subtreeRow(
        "subtrees.qualified-own-repository-listed", "own allow releases artifact net.read",
        { subjectOf(own(listOf(allow("ingot.repository.releases.artifact.net.read")))) }, "releases", "net",
    ),
    subtreeRow(
        "subtrees.deploy-excluded", "own allow ingot.artifact.net.deploy",
        { subjectOf(own(listOf(allow("ingot.artifact.net.deploy")))) }, "releases", "<none>",
    ),
    subtreeRow(
        "subtrees.repository-node-is-no-subtree", "own allow releases.read",
        { subjectOf(own(listOf(allow(READ_RELEASES)))) }, "releases", "<none>",
    ),
    subtreeRow(
        "subtrees.token-rule-alone-grants-nothing", "token lists ingot.artifact.net.read, owner empty",
        { subjectOf(token = tokenOf(allow(ARTIFACT_NET_READ))) }, "releases", "<none>",
        note = "a token node list only narrows the owner, it cannot allow a subtree the owner does not reach",
    ),
    subtreeRow(
        "subtrees.admin-without-artifact-rule-lists-nothing", "own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL)))) }, "releases", "<none>",
    ),
    subtreeRow(
        "subtrees.disabled-lists-nothing", "disabled, own allow ingot.artifact.net.read",
        { subjectOf(own(listOf(allow(ARTIFACT_NET_READ))), disabled = true) }, "releases", "<none>",
    ),
)
