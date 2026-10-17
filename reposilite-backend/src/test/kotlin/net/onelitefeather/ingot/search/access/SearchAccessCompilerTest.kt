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

import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.SourcedRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The compiler's own rules beyond the parity cases: the disabled caller, the term caps and what they count. Each test builds
 * its own subject and repository map.
 */
internal class SearchAccessCompilerTest {

    private val repositories = linkedMapOf("releases" to PUBLIC, "private" to PRIVATE)

    @Test
    fun `a disabled caller sees no row, not even one of a public repository`() {
        val disabled = AccessSubject(
            principalId = 1,
            principalName = "alice",
            kind = PrincipalKind.USER,
            disabled = true,
            levels = listOf(AccessLevel(DecisionSource.USER, 0, listOf(SourcedRule("alice", NodePattern.of("ingot.admin.*"), true)), emptyList())),
            tokenRules = null,
        )

        val access = SearchAccessCompiler.compile(disabled, repositories)

        assertThat(access.matches("releases", "net/example/app/1.0/app-1.0.jar")).describedAs("public row").isFalse
        assertThat(access.matches("private", "net/example/app/1.0/app-1.0.jar")).describedAs("private row").isFalse
    }

    @Test
    fun `a grant set at the term cap of one repository is compiled`() {
        val grants = (0 until SearchAccessCompiler.MAX_TERMS_PER_REPOSITORY).map { readGrant(pathPrefix = "p$it") }

        val access = SearchAccessCompiler.compile(caller(grants), repositories)

        assertThat(access.matches("private", "p199/app/1.0/app-1.0.jar")).describedAs("last granted prefix").isTrue
    }

    @Test
    fun `a grant set above the term cap of one repository is refused`() {
        val grants = (0..SearchAccessCompiler.MAX_TERMS_PER_REPOSITORY).map { readGrant(pathPrefix = "p$it") }

        assertThatThrownBy { SearchAccessCompiler.compile(caller(grants), repositories) }
            .describedAs("refusal of 201 terms in one repository")
            .isInstanceOf(SearchGrantSetTooLargeException::class.java)
            .hasFieldOrPropertyWithValue("limit", SearchAccessCompiler.MAX_TERMS_PER_REPOSITORY)
    }

    @Test
    fun `terms covered by an earlier shorter prefix do not count toward the cap`() {
        val grants = listOf(readGrant(pathPrefix = "net")) + (0 until 300).map { readGrant(pathPrefix = "net/x$it") }

        val access = SearchAccessCompiler.compile(caller(grants), repositories)

        assertThat(access.matches("private", "net/x7/app/1.0/app-1.0.jar")).describedAs("a covered prefix").isTrue
    }

    @Test
    fun `the total cap applies across repositories`() {
        val repositories = (1..6).associate { "repo$it" to PRIVATE }
        val grants = repositories.keys.flatMap { repository ->
            (0 until SearchAccessCompiler.MAX_TERMS_PER_REPOSITORY).map { readGrant(repository = repository, pathPrefix = "p$it") }
        }

        assertThatThrownBy { SearchAccessCompiler.compile(caller(grants), repositories) }
            .describedAs("refusal of 1200 terms in six repositories")
            .isInstanceOf(SearchGrantSetTooLargeException::class.java)
            .hasFieldOrPropertyWithValue("limit", SearchAccessCompiler.MAX_TERMS_TOTAL)
    }

    private fun readGrant(repository: String? = "private", pathPrefix: String) =
        Grant(team = "readers", role = Role.READ, repository = repository, pathPrefix = pathPrefix)

    private fun caller(grants: List<Grant>) = AccessSubject(
        principalId = 1,
        principalName = "alice",
        kind = PrincipalKind.USER,
        disabled = false,
        levels = listOf(AccessLevel(DecisionSource.TEAM, 0, emptyList(), grants)),
        tokenRules = null,
    )

}
