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

import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.SourcedRule
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Pure rules of the evaluator. No database, no clock, no shared state: every test builds its own subject. */
internal class PermissionEvaluatorTest {

    /**
     * A machine subject with its rules in one team level. [instanceAdmin] is the node `ingot.admin.*` of that team, and
     * [scopes] is the token narrowing the old scopes became.
     */
    private fun subject(
        grants: List<Grant> = emptyList(),
        instanceAdmin: Boolean = false,
        disabled: Boolean = false,
        scopes: Set<Action>? = null,
    ) = AccessSubject(
        principalId = 1,
        principalName = "bot",
        kind = PrincipalKind.MACHINE,
        disabled = disabled,
        levels = listOf(
            AccessLevel(
                source = DecisionSource.TEAM,
                depth = 0,
                rules = if (instanceAdmin) listOf(SourcedRule("machine:bot", NodePattern.INSTANCE_ADMIN, allowed = true)) else emptyList(),
                grants = grants,
            ),
        ),
        tokenRules = scopes?.let { ScopeRules.of("bot", it) },
    )

    private fun legacy(path: String, role: Role) =
        Grant(team = "machine:bot", role = role, repository = null, pathPrefix = path)

    private fun team(repository: String?, prefix: String?, role: Role) =
        Grant(team = "deployers", role = role, repository = repository, pathPrefix = prefix)

    private fun allowed(subject: AccessSubject, path: String, action: Action) =
        PermissionEvaluator.allows(subject, AccessRequest(path, action))

    @ParameterizedTest(name = "legacy {1} on \"{0}\" for {3} at \"{2}\" is {4}")
    @CsvSource(delimiter = '|', value = [
        "/releases|LEGACY_READ|/releases/com/x.jar|READ|true",
        "/releases|LEGACY_READ|/releases/com/x.jar|WRITE|false",
        "/releases|LEGACY_READ|/releases/com/x.jar|DELETE|false",
        "/releases|LEGACY_WRITE|/releases/com/x.jar|READ|false",
        "/releases|LEGACY_WRITE|/releases/com/x.jar|WRITE|true",
        "/releases|LEGACY_WRITE|/releases/com/x.jar|DELETE|true",
        "/releases/com/example|LEGACY_READ|/releases/com/examples/y.jar|READ|true",
        "/releases/com/example|LEGACY_READ|/releases/com/example/x/1.0/x.jar|READ|true",
        "/releases|LEGACY_READ|/RELEASES/com/x|READ|true",
        "/releases|LEGACY_READ|/releasesX|READ|true",
        "/releases|LEGACY_READ|/snapshots/x|READ|false",
        "/releases/com|LEGACY_READ|/releases|READ|false",
        "/|LEGACY_READ|/anything/at/all|READ|true",
        "/|LEGACY_WRITE|/anything|WRITE|true",
    ])
    fun `legacy grants match routes exactly as the old route check did`(path: String, role: Role, requested: String, action: Action, expected: Boolean) {
        // when / then: the grant decides the request as the legacy Route.hasPermissionTo did
        assertThat(allowed(subject(grants = listOf(legacy(path, role))), requested, action))
            .describedAs("$role on '$path' for $action at '$requested'")
            .isEqualTo(expected)
    }

    @ParameterizedTest(name = "{0} role on {1} for {3} at \"{2}\" is {4}")
    @CsvSource(delimiter = '|', value = [
        "READ|releases|com/example/x.jar|READ|true",
        "READ|releases|com/example/x.jar|WRITE|false",
        "DEPLOY|releases|com/example/x.jar|WRITE|true",
        "DEPLOY|releases|com/example/x.jar|DELETE|false",
        "MAINTAIN|releases|com/example/x.jar|DELETE|true",
        "MAINTAIN|releases|com/example/x.jar|MANAGE_REPOSITORY|false",
        "ADMIN|releases|com/example/x.jar|MANAGE_REPOSITORY|true",
        "ADMIN|releases|com/example/x.jar|INSTANCE_ADMIN|false",
        "DEPLOY|releases|com/example/x.jar|READ|true",
    ])
    fun `new roles are cumulative and apply to a repository`(role: Role, repository: String, requested: String, action: Action, expected: Boolean) {
        // given: a team grant on the whole repository
        val subject = subject(grants = listOf(team(repository, prefix = null, role = role)))

        // when / then: the cumulative role allows exactly its actions
        assertThat(allowed(subject, "/$repository/$requested", action))
            .describedAs("$role on $repository for $action")
            .isEqualTo(expected)
    }

    @ParameterizedTest(name = "prefix \"{0}\" for \"{1}\" is {2}")
    @CsvSource(delimiter = '|', value = [
        "com/example|com/example|true",
        "com/example|com/example/x.jar|true",
        "com/example|com/examples/y.jar|false",
        "com/example|com/exampleX|false",
        "/com/example/|com/example/x.jar|true",
        "com/example|COM/EXAMPLE/x.jar|false",
    ])
    fun `new path prefixes match whole segments and are case-sensitive`(prefix: String, inside: String, expected: Boolean) {
        // given: a DEPLOY grant limited to a path prefix inside the repository
        val subject = subject(grants = listOf(team("releases", prefix, Role.DEPLOY)))

        // when / then
        assertThat(allowed(subject, "/releases/$inside", Action.WRITE))
            .describedAs("prefix '$prefix' for '$inside'")
            .isEqualTo(expected)
    }

    @Test
    fun `a new grant on one repository does not reach another`() {
        // given: a READ grant on releases only
        val subject = subject(grants = listOf(team("releases", null, Role.READ)))

        // then: snapshots stays closed
        assertThat(allowed(subject, "/snapshots/com/x.jar", Action.READ)).describedAs("grant on another repository").isFalse
    }

    @Test
    fun `a grant without a repository applies to every repository`() {
        // given: a READ grant on every repository
        val subject = subject(grants = listOf(team(repository = null, prefix = null, role = Role.READ)))

        // then
        assertThat(allowed(subject, "/anything/x.jar", Action.READ)).describedAs("repository-less grant").isTrue
    }

    @Test
    fun `an instance admin is allowed everything, even without grants`() {
        // given: an instance admin without any grant
        val admin = subject(instanceAdmin = true)

        // then: every action on every path is allowed, as the MANAGER bypass always was
        for (action in Action.entries) {
            assertThat(allowed(admin, "/any/path/x.jar", action)).describedAs("instance admin for $action").isTrue
        }
    }

    @Test
    fun `a disabled principal is denied everything`() {
        // given: a disabled principal with an instance role and a grant
        val disabled = subject(grants = listOf(team("releases", null, Role.ADMIN)), instanceAdmin = true, disabled = true)

        // then: nothing is allowed and nothing is visible
        assertThat(allowed(disabled, "/releases/x.jar", Action.READ)).describedAs("disabled principal reads").isFalse
        assertThat(PermissionEvaluator.canSee(disabled, "/releases")).describedAs("disabled principal sees").isFalse
        assertThat(PermissionEvaluator.isInstanceAdmin(disabled)).describedAs("disabled instance admin").isFalse
    }

    @Test
    fun `scopes narrow a principal and never widen it`() {
        // given: a DEPLOY grant narrowed to READ by the token scopes
        val narrowed = subject(grants = listOf(team("releases", null, Role.DEPLOY)), scopes = setOf(Action.READ))

        // then: the grant allows writing, but the token does not
        assertThat(allowed(narrowed, "/releases/x.jar", Action.READ)).describedAs("scope READ").isTrue
        assertThat(allowed(narrowed, "/releases/x.jar", Action.WRITE)).describedAs("scope excludes WRITE").isFalse
    }

    @Test
    fun `scopes do not turn an instance admin into a full admin`() {
        // given: an instance admin whose token is narrowed to READ, with no grant
        val narrowedAdmin = subject(instanceAdmin = true, scopes = setOf(Action.READ))

        // then: the bypass needs the INSTANCE_ADMIN scope, so only READ remains and there is no grant to give it
        assertThat(PermissionEvaluator.isInstanceAdmin(narrowedAdmin)).describedAs("bypass without the INSTANCE_ADMIN scope").isFalse
        assertThat(allowed(narrowedAdmin, "/releases/x.jar", Action.READ)).describedAs("READ without grants").isFalse
    }

    @Test
    fun `a legacy token without scopes keeps its rights`() {
        // given: a legacy write token, which has no scopes
        val legacyToken = subject(grants = listOf(legacy("/releases", Role.LEGACY_WRITE)), scopes = null)

        // then
        assertThat(allowed(legacyToken, "/releases/x.jar", Action.WRITE)).describedAs("unrestricted legacy write").isTrue
    }

    @ParameterizedTest(name = "legacy route \"{0}\" sees fragment \"{1}\": {2}")
    @CsvSource(delimiter = '|', value = [
        "/releases|/releases|true",
        "/releases|/RELEASES|true",
        "/releases|/releasesX|true",
        "/releases|/rel|true",
        "/releases|/snapshots|false",
        "/releases/com/x|/releases/com/x/y|true",
        "/releases/com|/releases/com/x|true",
        "/releases/com|/RELEASES/COM/X|false",
    ])
    fun `legacy visibility keeps the old asymmetric comparison`(path: String, fragment: String, expected: Boolean) {
        // given: a legacy route
        val subject = subject(grants = listOf(legacy(path, Role.LEGACY_READ)))

        // then: canSee compares the route to the fragment in both directions, the second one case-sensitive
        assertThat(PermissionEvaluator.canSee(subject, fragment))
            .describedAs("visibility of '$fragment' through route '$path'")
            .isEqualTo(expected)
    }

    @Test
    fun `a new grant on a repository makes it visible`() {
        // given: a PRIVATE repository and a team grant on it
        val subject = subject(grants = listOf(team("releases", "com/example", Role.READ)))

        // then
        assertThat(PermissionEvaluator.canSee(subject, "/releases")).describedAs("visibility of granted repository").isTrue
        assertThat(PermissionEvaluator.canSee(subject, "/snapshots")).describedAs("visibility of other repository").isFalse
    }

    @Test
    fun `a HIDDEN repository is listed for principals with a new READ grant`() {
        // given: a team with READ on releases
        val subject = subject(grants = listOf(team("releases", null, Role.READ)))

        // then
        assertThat(PermissionEvaluator.canListHidden(subject, "releases")).describedAs("listing with READ").isTrue
    }

    @Test
    fun `a HIDDEN repository is listed for a path-limited new grant`() {
        // given: a team with DEPLOY on a path inside releases
        val subject = subject(grants = listOf(team("releases", "com/example", Role.DEPLOY)))

        // then: a grant of role READ or higher on the repository is enough, even when limited to a path
        assertThat(PermissionEvaluator.canListHidden(subject, "releases")).describedAs("listing with a path-limited grant").isTrue
    }

    @Test
    fun `a HIDDEN repository is never listed through a legacy grant`() {
        // given: a legacy token that can read a route on releases
        val subject = subject(grants = listOf(legacy("/releases", Role.LEGACY_READ)))

        // then: that is the old behaviour, a legacy grant does not list a HIDDEN repository
        assertThat(PermissionEvaluator.canListHidden(subject, "releases")).describedAs("listing through a legacy grant").isFalse
    }

    @Test
    fun `a HIDDEN repository is listed for instance admins`() {
        // then
        assertThat(PermissionEvaluator.canListHidden(subject(instanceAdmin = true), "releases")).describedAs("listing for an instance admin").isTrue
    }

    /**
     * A user whose own level holds [ownRules], and whose team one level further away holds [grants]. The team grant is the
     * only way the team reaches the repository.
     */
    private fun userWithOwnRules(
        ownRules: List<SourcedRule>,
        grants: List<Grant>,
        teamRules: List<SourcedRule> = emptyList(),
    ) = AccessSubject(
        principalId = 2,
        principalName = "alice",
        kind = PrincipalKind.USER,
        disabled = false,
        levels = listOf(
            AccessLevel(source = DecisionSource.USER, depth = 0, rules = ownRules, grants = emptyList()),
            AccessLevel(source = DecisionSource.TEAM, depth = 1, rules = teamRules, grants = grants),
        ),
        tokenRules = null,
    )

    private val personalDenialOfReleasesRead = SourcedRule("alice", NodePattern.of("ingot.repository.releases.read"), allowed = false)

    @Test
    fun `a personal denial of the repository read hides it even when a team grant reaches it`() {
        // given: a personal denial of releases.read, and a team READ grant on releases
        val alice = userWithOwnRules(listOf(personalDenialOfReleasesRead), listOf(team("releases", null, Role.READ)))

        // then: the read is denied, so the repository must not be visible either
        assertThat(PermissionEvaluator.decide(alice, NodePattern.of("ingot.repository.releases.read")).allowed)
            .describedAs("read decision of releases").isFalse
        assertThat(PermissionEvaluator.canSee(alice, "/releases")).describedAs("visibility of a denied repository").isFalse
    }

    @Test
    fun `a personal denial of the repository read keeps a HIDDEN repository unlisted despite a team grant`() {
        // given: a personal denial of releases.read, and a team READ grant on releases
        val alice = userWithOwnRules(listOf(personalDenialOfReleasesRead), listOf(team("releases", null, Role.READ)))

        // then
        assertThat(PermissionEvaluator.canListHidden(alice, "releases")).describedAs("listing of a denied HIDDEN repository").isFalse
    }

    @Test
    fun `a personal denial of the repository read still shows it for a readable artifact subtree`() {
        // given: a personal denial of releases.read, and an artifact read node on one subtree of releases
        val artifactRead = SourcedRule("alice", NodePattern.of("ingot.artifact.net.onelitefeather.read"), allowed = true)
        val alice = userWithOwnRules(listOf(personalDenialOfReleasesRead, artifactRead), listOf(team("releases", null, Role.READ)))

        // then: the subtree is readable, so the repository is listed as before
        assertThat(PermissionEvaluator.canSee(alice, "/releases")).describedAs("visibility through a readable subtree").isTrue
    }

    @Test
    fun `a read inside an allowed subtree is decided allowed despite a personal denial of the repository read`() {
        // given: a personal denial of releases.read, and a personal allow of releases:com/example.read, the deeper node
        val subtreeRead = SourcedRule("alice", NodePattern.of("ingot.repository.releases.artifact.com.example.read"), allowed = true)
        val alice = userWithOwnRules(listOf(personalDenialOfReleasesRead, subtreeRead), listOf(team("releases", null, Role.READ)))

        // then: the deeper node is more specific, so a read inside the subtree is allowed and the subtree is readable
        assertThat(allowed(alice, "/releases/com/example/app/1.0/app-1.0.jar", Action.READ))
            .describedAs("read inside the allowed subtree").isTrue
        assertThat(allowed(alice, "/releases/org/other/app-1.0.jar", Action.READ))
            .describedAs("read outside the allowed subtree").isFalse
        assertThat(PermissionEvaluator.canSee(alice, "/releases")).describedAs("visibility through the readable subtree").isTrue
    }

    @Test
    fun `a personal denial of the repository read keeps a HIDDEN repository unlisted for a readable subtree`() {
        // given: the same subject; a HIDDEN repository is listed by a node or a grant of the whole repository, not by a subtree
        val subtreeRead = SourcedRule("alice", NodePattern.of("ingot.repository.releases.artifact.com.example.read"), allowed = true)
        val alice = userWithOwnRules(listOf(personalDenialOfReleasesRead, subtreeRead), listOf(team("releases", null, Role.READ)))

        // then
        assertThat(PermissionEvaluator.canListHidden(alice, "releases")).describedAs("listing of a HIDDEN repository by a subtree").isFalse
    }

    @Test
    fun `a personal denial of the repository read hides it when only a team allows a subtree`() {
        // given: a personal denial of releases.read, a team allow of releases:com/example.read and a team READ grant on releases
        val subtreeRead = SourcedRule("deployers", NodePattern.of("ingot.repository.releases.artifact.com.example.read"), allowed = true)
        val alice = userWithOwnRules(
            ownRules = listOf(personalDenialOfReleasesRead),
            grants = listOf(team("releases", null, Role.READ)),
            teamRules = listOf(subtreeRead),
        )

        // then: the personal level decides the read as denied, so the team subtree is never reached and the repository is hidden
        assertThat(allowed(alice, "/releases/com/example/app/1.0/app-1.0.jar", Action.READ))
            .describedAs("read inside the team subtree").isFalse
        assertThat(PermissionEvaluator.canSee(alice, "/releases")).describedAs("visibility of a denied repository").isFalse
        assertThat(PermissionEvaluator.canListHidden(alice, "releases")).describedAs("listing of a denied HIDDEN repository").isFalse
    }

}
