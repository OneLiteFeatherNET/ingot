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
package net.onelitefeather.ingot.search.access

import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.SourcedRule
import java.util.stream.Stream

/** A named caller of the parity test. A null subject is the anonymous caller. */
internal data class SubjectCase(val name: String, val subject: AccessSubject?) {
    override fun toString(): String = name
}

/** One indexed row: a repository and a normalised path inside it, as the index stores them. */
internal data class FixtureRow(val repository: String, val path: String)

/**
 * The fixed inputs of the parity test (artifact search spec, 5.7): three repositories of each visibility, paths at the edges
 * of the prefixes, and callers that cover every rule kind. The oracle is the evaluator: a row is expected exactly when the
 * evaluator allows READ on it, or the repository is PUBLIC. Paths are the normalised form the index stores, so the in-memory
 * filter and the SQL filter see the same input.
 */
internal object SearchAccessFixtures {

    val repositories: Map<String, RepositoryVisibility> = linkedMapOf(
        "releases" to PUBLIC,
        "hidden" to HIDDEN,
        "private" to PRIVATE,
    )

    val paths: List<String> = listOf(
        "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar",
        "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.pom",
        "net/onelitefeather/mycelium/2.0/mycelium-2.0.jar",
        "net/onelitefeather/mycelium-bom-extra/1.0/mycelium-bom-extra-1.0.jar",
        "net/onelitefeather/maven-metadata.xml",
        "net/onelitefeatherX/lib/1.0/lib-1.0.jar",
        "com/example/app/1.0/app-1.0.jar",
        "com/examples/app/1.0/app-1.0.jar",
        "com/Example/app/1.0/app-1.0.jar",
        "com/example",
        "com/example/",
        "org/other/lib/1.0/lib-1.0.jar",
        "maven-metadata.xml",
    )

    val rows: List<FixtureRow> = repositories.keys.flatMap { repository -> paths.map { FixtureRow(repository, it) } }

    /** The callers of the parity test, one per behaviour the filter must reproduce. */
    @JvmStatic
    fun subjects(): Stream<SubjectCase> = Stream.of(
        SubjectCase("anonymous caller", null),
        SubjectCase("disabled principal with instance admin", caller(level(DecisionSource.USER, rules = listOf(rule("ingot.admin.*"))), disabled = true)),
        SubjectCase("reader of the private repository", caller(level(DecisionSource.TEAM, grants = listOf(grant(Role.READ, repository = "private"))))),
        SubjectCase(
            "team path grant inside a hidden repository",
            caller(level(DecisionSource.TEAM, grants = listOf(grant(Role.READ, repository = "hidden", pathPrefix = "net/onelitefeather")))),
        ),
        SubjectCase(
            "legacy routes with text prefixes",
            caller(
                level(
                    DecisionSource.TEAM,
                    grants = listOf(
                        grant(Role.LEGACY_READ, pathPrefix = "/hidden/com/example"),
                        grant(Role.LEGACY_READ, pathPrefix = "/pri"),
                    ),
                ),
            ),
        ),
        SubjectCase("legacy write route grants no read", caller(level(DecisionSource.TEAM, grants = listOf(grant(Role.LEGACY_WRITE, pathPrefix = "/hidden"))))),
        SubjectCase(
            "artifact read node inside an allowed namespace",
            caller(level(DecisionSource.USER, rules = listOf(rule("ingot.artifact.net.onelitefeather.read")))),
        ),
        SubjectCase(
            "artifact deploy node grants no read",
            caller(level(DecisionSource.USER, rules = listOf(rule("ingot.artifact.net.onelitefeather.deploy")))),
        ),
        SubjectCase(
            "artifact deny inside an allowed namespace",
            caller(
                level(
                    DecisionSource.USER,
                    rules = listOf(
                        rule("ingot.artifact.net.onelitefeather.read"),
                        rule("ingot.artifact.net.onelitefeather:mycelium-bom.read", allowed = false),
                    ),
                ),
            ),
        ),
        SubjectCase(
            "repository denial at a closer level beats a farther team grant",
            caller(
                level(DecisionSource.USER, rules = listOf(rule("ingot.repository.hidden.read", allowed = false))),
                level(DecisionSource.INHERITED_TEAM, depth = 1, grants = listOf(grant(Role.READ, repository = "hidden"))),
            ),
        ),
        SubjectCase(
            "denial ties with a grant of the same specificity",
            caller(
                level(
                    DecisionSource.TEAM,
                    rules = listOf(rule("ingot.repository.hidden.read", allowed = false)),
                    grants = listOf(grant(Role.READ, repository = "hidden")),
                ),
            ),
        ),
        SubjectCase(
            "artifact allow and deny of the same node tie, the denial wins",
            caller(
                level(
                    DecisionSource.TEAM,
                    rules = listOf(
                        rule("ingot.artifact.net.onelitefeather.read", subject = "alice"),
                        rule("ingot.artifact.net.onelitefeather.read", allowed = false, subject = "readers"),
                    ),
                ),
            ),
        ),
        SubjectCase(
            "token narrowed to one artifact",
            caller(
                level(DecisionSource.TEAM, grants = listOf(grant(Role.READ, repository = "private"))),
                tokenRules = listOf(rule("ingot.artifact.net.onelitefeather:mycelium-bom.read", subject = "token")),
            ),
        ),
        SubjectCase(
            "token whose nodes lack read",
            caller(
                level(DecisionSource.TEAM, grants = listOf(grant(Role.ADMIN, repository = "private"))),
                tokenRules = listOf(rule("ingot.repository.*.deploy", subject = "token")),
            ),
        ),
        SubjectCase("instance admin", caller(level(DecisionSource.USER, rules = listOf(rule("ingot.admin.*"))))),
        SubjectCase(
            "instance admin whose token names only the admin node",
            caller(
                level(DecisionSource.USER, rules = listOf(rule("ingot.admin.*"))),
                tokenRules = listOf(rule("ingot.admin.*", subject = "token")),
            ),
        ),
        SubjectCase(
            "instance admin whose token lacks read",
            caller(
                level(DecisionSource.USER, rules = listOf(rule("ingot.admin.*"))),
                tokenRules = listOf(rule("ingot.repository.*.deploy", subject = "token")),
            ),
        ),
    )

    /** What the evaluator decides for [row] (5.7): PUBLIC rows for every live caller, the rest where READ is allowed. */
    fun evaluatorAllows(subject: AccessSubject?, row: FixtureRow): Boolean {
        val caller = subject ?: anonymous
        if (caller.disabled) return false
        return repositories.getValue(row.repository) == PUBLIC ||
            PermissionEvaluator.allows(caller, AccessRequest("/${row.repository}/${row.path}", Action.READ))
    }

    private val anonymous = AccessSubject(
        principalId = null,
        principalName = null,
        kind = PrincipalKind.USER,
        disabled = false,
        levels = emptyList(),
        tokenRules = null,
    )


    private fun rule(node: String, allowed: Boolean = true, subject: String = "alice") =
        SourcedRule(subject, NodePattern.of(node), allowed)

    private fun grant(role: Role, repository: String? = null, pathPrefix: String? = null) =
        Grant(team = "readers", role = role, repository = repository, pathPrefix = pathPrefix)

    private fun level(
        source: DecisionSource,
        depth: Int = 0,
        rules: List<SourcedRule> = emptyList(),
        grants: List<Grant> = emptyList(),
    ) = AccessLevel(source, depth, rules, grants)

    private fun caller(vararg levels: AccessLevel, tokenRules: List<SourcedRule>? = null, disabled: Boolean = false) =
        AccessSubject(
            principalId = 1,
            principalName = "alice",
            kind = PrincipalKind.USER,
            disabled = disabled,
            levels = levels.toList(),
            tokenRules = tokenRules,
        )

}
