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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The harness: a row cannot be dropped silently. [LEDGER] lists every row identifier the corpus must hold. A missing identifier
 * fails with its name, and so does a recorded row that the ledger does not list. A row is added or removed in the same change
 * as its ledger entry, so a deletion shows up in the diff of the ledger.
 */
internal class DecisionCorpusCoverageTest {

    @Test
    fun `every ledger row is recorded in the corpus`() {
        val recorded = DecisionCorpus.rows.map { it.id }.toSet()
        val missing = LEDGER.filterNot { it in recorded }

        assertThat(missing).describedAs("rows of the ledger missing from the corpus").isEmpty()
    }

    @Test
    fun `every recorded row is listed in the ledger`() {
        val listed = LEDGER.toSet()
        val unlisted = DecisionCorpus.rows.map { it.id }.filterNot { it in listed }

        assertThat(unlisted).describedAs("rows of the corpus missing from the ledger").isEmpty()
    }

    @Test
    fun `row identifiers are unique`() {
        val duplicated = DecisionCorpus.rows.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys

        assertThat(duplicated).describedAs("row identifiers recorded twice").isEmpty()
    }

    @Test
    fun `every decision entry point has at least one row`() {
        val covered = DecisionCorpus.rows.map { it.entryPoint }.toSet()
        val uncovered = EntryPoint.entries.filterNot { it in covered }

        assertThat(uncovered).describedAs("entry points without a row").isEmpty()
    }

    @Test
    fun `the ledger holds no identifier twice`() {
        val duplicated = LEDGER.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertThat(duplicated).describedAs("ledger identifiers listed twice").isEmpty()
    }

    companion object {
        /** The identifiers of every row the corpus holds, sorted. Keep in step with the row definitions. */
        internal val LEDGER: List<String> = listOf(
            "admin-area.area-denied-without-instance-admin",
            "admin-area.area-from-wildcard",
            "admin-area.disabled-has-no-area",
            "admin-area.inherited-area-allow",
            "admin-area.instance-admin-from-wildcard",
            "admin-area.instance-admin-ignores-area-denial",
            "admin-area.instance-admin-needs-star-admin",
            "admin-area.other-area-not-granted",
            "admin-area.role-grant-is-not-an-area",
            "admin-area.star-allows-instance-admin",
            "admin-area.token-narrowing-removes-area",
            "artifact-rule.denial-counts",
            "artifact-rule.deploy-is-not-read",
            "artifact-rule.disabled",
            "artifact-rule.grant-does-not-count",
            "artifact-rule.qualified-other-repository",
            "artifact-rule.qualified-own-repository",
            "artifact-rule.repository-node-does-not-count",
            "artifact-rule.token-rule-counts",
            "artifact-rule.unqualified-read",
            "artifact-rule.wildcard-action-counts",
            "can-list-hidden.artifact-subtree-does-not-list",
            "can-list-hidden.deploy-role-lists",
            "can-list-hidden.disabled",
            "can-list-hidden.instance-admin",
            "can-list-hidden.legacy-route-never-lists",
            "can-list-hidden.node-read-allow",
            "can-list-hidden.other-repository",
            "can-list-hidden.path-scoped-read-lists",
            "can-list-hidden.personal-denial-wins-over-grant",
            "can-list-hidden.role-read-grant",
            "can-list-hidden.token-narrowing",
            "can-see.artifact-subtree-alone",
            "can-see.denial-without-grant-hides",
            "can-see.disabled-sees-nothing",
            "can-see.fragment-without-slash-misses-legacy-route",
            "can-see.instance-admin-sees-any-repository",
            "can-see.legacy-path-scoped-route",
            "can-see.node-allow-shows-repository",
            "can-see.no-grant-sees-nothing",
            "can-see.other-repository",
            "can-see.path-scoped-grant-shows-repository",
            "can-see.personal-denial-wins-over-reaching-grant",
            "can-see.repository-read-grant",
            "can-see.token-narrowing-hides",
            "decide-path.artifact-subtree",
            "decide-path.deploy-not-covered-by-read-artifact",
            "decide-path.disabled-subject",
            "decide-path.encoded-slash-raw",
            "decide-path.grant-prefix-bounded",
            "decide-path.grant-prefix-bounded-denied",
            "decide-path.manage-with-admin-role",
            "decide-path.unknown-action-refused",
            "instance-admin.area-is-not-instance-admin",
            "instance-admin.disabled-never-admin",
            "instance-admin.legacy-root-route-is-not-admin",
            "instance-admin.own-rule",
            "instance-admin.personal-denial-beats-inherited-allow",
            "instance-admin.role-grant-is-not-admin",
            "instance-admin.star-allow-is-admin",
            "instance-admin.star-deny-loses-to-admin-allow",
            "instance-admin.team-rule-at-distance-zero",
            "instance-admin.token-with-admin-node-keeps-admin",
            "instance-admin.token-without-admin-node-removes-admin",
            "node.artifact.action-wildcard-covers-read",
            "node.artifact.qualified-decides-repository-subtree",
            "node.artifact.qualified-rule-does-not-decide-unqualified-node",
            "node.artifact.qualified-subtree-under-repository-grant",
            "node.artifact.repository-wide-grant-does-not-decide-unqualified-node",
            "node.artifact.sibling-group-not-covered",
            "node.artifact.subtree-decided-as-node",
            "node.artifact.wider-node-not-covered",
            "node.repository.legacy-path-route-does-not-decide-repository",
            "node.repository.legacy-route-tie-denial-wins",
            "node.repository.literal-allow",
            "node.repository.literal-beats-wildcard-denial",
            "node.repository.other-action-default-deny",
            "node.repository.other-repository-default-deny",
            "node.repository.role-grant-beats-wildcard-denial",
            "node.repository.role-grant-tie-denial-wins",
            "node.repository.star-allow-makes-instance-admin",
            "node.repository.star-deny-denies-repository",
            "node.repository.star-deny-overridden-by-instance-admin",
            "node.repository.tie-denial-wins",
            "node.repository.wildcard-action-allow",
            "node.repository.wildcard-both-allow",
            "node.repository.wildcard-repository-allow",
            "path.dot-segment-escape.normalized",
            "path.dot-segment-escape.raw",
            "path.dot-segment.normalized",
            "path.dot-segment.raw",
            "path.double-slash.normalized",
            "path.double-slash.raw",
            "path.encoded-dot.normalized",
            "path.encoded-dot.raw",
            "path.encoded-slash.normalized",
            "path.encoded-slash.raw",
            "path.mixed-case-artifact.normalized",
            "path.mixed-case-artifact.raw",
            "path.mixed-case-repository.raw",
            "path.mixed-case-repository.with-repository-node",
            "path.repository-root-not-covered-by-artifact",
            "path.trailing-slash.normalized",
            "precedence.inherited-allow-when-nearer-levels-silent",
            "precedence.nearer-denial-beats-farther-allow",
            "precedence.personal-literal-allow-beats-team-wildcard-denial",
            "precedence.personal-wildcard-denial-beats-team-allow",
            "precedence.team-allow-when-personal-silent",
            "precedence.token-denial-narrows-owner-allow",
            "precedence.token-list-cannot-widen-owner",
            "precedence.token-list-narrows-personal-allow",
            "request.admin-role-is-not-instance-admin",
            "request.admin-role-manages-repository",
            "request.deploy-role-excludes-delete",
            "request.deploy-role-includes-read",
            "request.disabled-denies-everything",
            "request.disabled-denies-instance-admin",
            "request.grant-without-repository-covers-every-repository",
            "request.instance-admin-ignores-artifact-denial",
            "request.instance-admin-ignores-repository-denial",
            "request.instance-admin-narrowed-by-token",
            "request.instance-admin-token-with-admin-node-reads",
            "request.instance-admin-token-without-admin-node-loses-shortcut",
            "request.legacy-read-excludes-write",
            "request.legacy-root-covers-every-path",
            "request.legacy-route-matches-case-insensitively",
            "request.legacy-route-matches-name-prefix",
            "request.legacy-route-not-covering-its-parent",
            "request.legacy-route-prefix-is-unbounded",
            "request.legacy-write-excludes-read",
            "request.legacy-write-includes-delete",
            "request.maintain-role-includes-delete",
            "request.path-grant-covers-its-directory",
            "request.path-grant-covers-nested-path",
            "request.path-grant-is-segment-bounded",
            "request.path-grant-other-repository",
            "request.path-grant-prefix-is-case-sensitive",
            "request.repository-grant-covers-every-path",
            "request.token-cannot-widen-owner",
            "request.token-denial-narrows-owner",
            "request.token-list-denies-unlisted-repository",
            "request.token-narrows-owner-grant",
            "request.token-without-node-list-narrows-nothing",
            "subtrees.admin-without-artifact-rule-lists-nothing",
            "subtrees.denial-at-same-root-removes-it",
            "subtrees.denial-of-subtree-keeps-parent",
            "subtrees.deploy-excluded",
            "subtrees.disabled-lists-nothing",
            "subtrees.nested-roots-in-rule-order",
            "subtrees.other-repository-excluded",
            "subtrees.qualified-own-repository-listed",
            "subtrees.repository-node-is-no-subtree",
            "subtrees.single-subtree",
            "subtrees.token-rule-alone-grants-nothing",
            "subtrees.whole-repository-group-wildcard",
        )
    }
}
