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
package net.onelitefeather.ingot.identity.authorization

import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.Decision
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.PrincipalKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The contract of the chain with fake stages. Every test builds its own stages and records the calls it receives in a list
 * of its own, so no test depends on another and none reads shared state.
 */
internal class AuthorizationChainTest {

    private class RecordingAuthorizer(
        private val name: String,
        private val answer: AuthorizerDecision,
        private val calls: MutableList<String>,
    ) : Authorizer {
        override fun decide(request: AuthorizationRequest): AuthorizerDecision {
            calls += name
            return answer
        }
    }

    private fun decisionNamed(name: String, allowed: Boolean) =
        Decision(DecisionSource.DEFAULT, subject = name, node = "ingot.repository.releases.read", allowed = allowed, depth = 0, trace = emptyList())

    private fun subject(disabled: Boolean = false) = AccessSubject(
        principalId = 1,
        principalName = "bot",
        kind = PrincipalKind.MACHINE,
        disabled = disabled,
        levels = emptyList(),
        tokenRules = null,
    )

    private fun request(disabled: Boolean = false) =
        AuthorizationRequest.Access(subject(disabled), AccessRequest("/releases/net/x.jar", Action.READ))

    @Test
    fun `an allow of a restriction stage stops the chain before the built-in stage`() {
        val calls = mutableListOf<String>()
        val chain = AuthorizationChain(
            restrictions = listOf(RecordingAuthorizer("restriction", AuthorizerDecision.Allow(decisionNamed("restriction", true)), calls)),
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.Deny(decisionNamed("built-in", false)), calls),
        )

        val outcome = chain.decide(request())

        assertThat(outcome.allowed).describedAs("allowed by the restriction stage").isTrue
        assertThat(calls).describedAs("stages consulted").containsExactly("restriction")
    }

    @Test
    fun `a deny of the built-in stage stops the chain before the binding and plugin stages`() {
        val calls = mutableListOf<String>()
        val chain = AuthorizationChain(
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.Deny(decisionNamed("built-in", false)), calls),
            bindings = listOf(RecordingAuthorizer("binding", AuthorizerDecision.Allow(decisionNamed("binding", true)), calls)),
            plugins = listOf(RecordingAuthorizer("plugin", AuthorizerDecision.Allow(decisionNamed("plugin", true)), calls)),
        )

        val outcome = chain.decide(request())

        assertThat(outcome.allowed).describedAs("denied by the built-in stage").isFalse
        assertThat(calls).describedAs("stages consulted").containsExactly("built-in")
    }

    @Test
    fun `a deny of a restriction stage wins over an allow of a later plugin stage`() {
        val calls = mutableListOf<String>()
        val chain = AuthorizationChain(
            restrictions = listOf(RecordingAuthorizer("restriction", AuthorizerDecision.Deny(decisionNamed("restriction", false)), calls)),
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.Allow(decisionNamed("built-in", true)), calls),
            plugins = listOf(RecordingAuthorizer("plugin", AuthorizerDecision.Allow(decisionNamed("plugin", true)), calls)),
        )

        assertThat(chain.decide(request()).allowed).describedAs("deny wins").isFalse
        assertThat(calls).describedAs("stages consulted").containsExactly("restriction")
    }

    @Test
    fun `a plugin allow is reached when every earlier stage has no opinion`() {
        val calls = mutableListOf<String>()
        val chain = AuthorizationChain(
            restrictions = listOf(RecordingAuthorizer("restriction", AuthorizerDecision.NoOpinion(), calls)),
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.NoOpinion(), calls),
            plugins = listOf(RecordingAuthorizer("plugin", AuthorizerDecision.Allow(decisionNamed("plugin", true)), calls)),
        )

        assertThat(chain.decide(request()).allowed).describedAs("allowed by the plugin stage").isTrue
        assertThat(calls).describedAs("stages consulted in order").containsExactly("restriction", "built-in", "plugin")
    }

    @Test
    fun `no opinion from every stage ends in a deny`() {
        val calls = mutableListOf<String>()
        val chain = AuthorizationChain(
            restrictions = listOf(RecordingAuthorizer("restriction", AuthorizerDecision.NoOpinion(), calls)),
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.NoOpinion(), calls),
            bindings = listOf(RecordingAuthorizer("binding", AuthorizerDecision.NoOpinion(), calls)),
            plugins = listOf(RecordingAuthorizer("plugin", AuthorizerDecision.NoOpinion(), calls)),
        )

        assertThat(chain.decide(request()).allowed).describedAs("default deny").isFalse
        assertThat(calls).describedAs("every stage consulted").containsExactly("restriction", "built-in", "binding", "plugin")
    }

    @Test
    fun `a disabled principal is refused before any stage is consulted`() {
        val calls = mutableListOf<String>()
        val chain = AuthorizationChain(
            restrictions = listOf(RecordingAuthorizer("restriction", AuthorizerDecision.Allow(decisionNamed("restriction", true)), calls)),
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.Allow(decisionNamed("built-in", true)), calls),
            plugins = listOf(RecordingAuthorizer("plugin", AuthorizerDecision.Allow(decisionNamed("plugin", true)), calls)),
        )

        val outcome = chain.decide(request(disabled = true))

        assertThat(outcome.allowed).describedAs("disabled principal allowed").isFalse
        assertThat(calls).describedAs("stages consulted").isEmpty()
    }

    @Test
    fun `empty restriction, binding and plugin stages leave the built-in decision as it is`() {
        val calls = mutableListOf<String>()
        val builtInDecision = decisionNamed("built-in", true)
        val chain = AuthorizationChain(builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.Allow(builtInDecision), calls))

        val outcome = chain.decide(request())

        assertThat(outcome.allowed).describedAs("allowed").isTrue
        assertThat(outcome.decision).describedAs("decision of the built-in stage").isSameAs(builtInDecision)
    }

    @Test
    fun `the trace of the deciding stage is passed through unchanged`() {
        val calls = mutableListOf<String>()
        val deciding = decisionNamed("plugin", false)
        val chain = AuthorizationChain(
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.NoOpinion(decisionNamed("built-in", false)), calls),
            plugins = listOf(RecordingAuthorizer("plugin", AuthorizerDecision.Deny(deciding), calls)),
        )

        val outcome = chain.decide(request())

        assertThat(outcome.decision).describedAs("decision of the deciding stage").isSameAs(deciding)
    }

    @Test
    fun `the default deny keeps the decision of the last stage with no opinion`() {
        val calls = mutableListOf<String>()
        val builtInDecision = decisionNamed("built-in", false)
        val chain = AuthorizationChain(
            restrictions = listOf(RecordingAuthorizer("restriction", AuthorizerDecision.NoOpinion(), calls)),
            builtIn = RecordingAuthorizer("built-in", AuthorizerDecision.NoOpinion(builtInDecision), calls),
        )

        val outcome = chain.decide(request())

        assertThat(outcome.allowed).describedAs("default deny").isFalse
        assertThat(outcome.decision).describedAs("trace of the built-in stage").isSameAs(builtInDecision)
    }
}
