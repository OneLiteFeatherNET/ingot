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

import com.reposilite.audit.decision.DecisionLog
import com.reposilite.audit.decision.DecisionLogMode
import com.reposilite.audit.decision.DecisionLogUnavailableException
import com.reposilite.audit.decision.DecisionOutcome
import com.reposilite.audit.decision.DecisionRecord
import com.reposilite.audit.decision.DecisionRecorder
import com.reposilite.audit.decision.DecisionSink
import com.reposilite.audit.decision.RecordOutcome
import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.Candidate
import net.onelitefeather.ingot.identity.evaluation.Decision
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.PrincipalKind
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

/**
 * What the chain hands to the decision log. Every test builds its own fakes, so no test reads state of another one, and
 * nothing waits on a thread: the chain writes to the log before it returns.
 */
internal class AuthorizationDecisionLogTest {

    /** A built-in stage with one fixed answer that counts how often the chain asks it. */
    private class FixedStage(private val answer: AuthorizerDecision) : Authorizer {
        var asked = 0

        override fun decide(request: AuthorizationRequest): AuthorizerDecision {
            asked++
            return answer
        }
    }

    /** Keeps every record it is given. It throws [failure] when set, and answers [outcome] otherwise. */
    private class FakeLog(
        override val enabled: Boolean = true,
        private val failure: RuntimeException? = null,
        private val outcome: RecordOutcome = RecordOutcome.STORED,
    ) : DecisionLog {
        val records = mutableListOf<DecisionRecord>()

        override fun record(record: DecisionRecord): RecordOutcome {
            failure?.let { throw it }
            records += record
            return outcome
        }
    }

    /** Counts the identifiers it hands out. A record is built only when one is needed, so the count is the record count. */
    private class IdentifierCounter {
        var built = 0

        fun next(): String {
            built++
            return "decision-$built"
        }
    }

    private val clock = MutableClock(Instant.parse("2026-11-16T10:00:00Z"))
    private val readPath = "/releases/com/example/app/1.0/app-1.0.jar"

    private fun subject(disabled: Boolean = false) = AccessSubject(
        principalId = 7,
        principalName = "deploy-bot",
        kind = PrincipalKind.MACHINE,
        disabled = disabled,
        levels = emptyList(),
        tokenRules = null,
        credentialName = "deploy-token",
    )

    private fun access(action: Action) = AuthorizationRequest.Access(subject(), AccessRequest(readPath, action))

    private fun denial(trace: List<Candidate> = emptyList()) = Decision(
        source = DecisionSource.TEAM,
        subject = "deploy-bot",
        node = "ingot.repository.releases.read",
        allowed = false,
        depth = 0,
        trace = trace,
        decidingNode = "ingot.repository.releases.read",
        decidingKind = "team",
    )

    private fun allowance() = Decision(
        source = DecisionSource.TEAM,
        subject = "deploy-bot",
        node = "ingot.repository.releases.read",
        allowed = true,
        depth = 0,
        trace = emptyList(),
    )

    private fun trail(log: DecisionLog, ids: IdentifierCounter = IdentifierCounter()) =
        DecisionTrail(log = log, clock = clock, newId = ids::next)

    private fun chain(stage: FixedStage, decisions: DecisionTrail = DecisionTrail.OFF) =
        AuthorizationChain(builtIn = stage, decisions = decisions)

    @Test
    fun `a denied read writes one denial record with the decision fields`() {
        val log = FakeLog()
        val candidate = Candidate(DecisionSource.TEAM, "deploy-bot", 0, "ingot.repository.releases.read", false, true, "deny")
        val stage = FixedStage(AuthorizerDecision.Deny(denial(listOf(candidate))))

        val outcome = chain(stage, trail(log)).decide(access(Action.READ))

        assertThat(outcome.allowed).isFalse()
        assertThat(log.records).hasSize(1)
        val record = log.records.single()
        assertThat(record.decisionId).isEqualTo("decision-1")
        assertThat(record.time).isEqualTo(clock.instant())
        assertThat(record.principal).isEqualTo("deploy-bot")
        assertThat(record.credentialId).isEqualTo("deploy-token")
        assertThat(record.action).isEqualTo("read")
        assertThat(record.resource).isEqualTo(readPath)
        assertThat(record.rawResource).isNull()
        assertThat(record.outcome).isEqualTo(DecisionOutcome.DENY)
        assertThat(record.write).isFalse()
        assertThat(record.stage).isEqualTo("built-in")
        assertThat(record.revision).isNull()
        assertThat(record.obligations).isEmpty()
    }

    @Test
    fun `an allowed read writes no record`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        val outcome = chain(stage, trail(log)).decide(access(Action.READ))

        assertThat(outcome.allowed).isTrue()
        assertThat(log.records).isEmpty()
    }

    @Test
    fun `an allowed write writes one allow record`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        chain(stage, trail(log)).decide(access(Action.WRITE))

        val record = log.records.single()
        assertThat(record.outcome).isEqualTo(DecisionOutcome.ALLOW)
        assertThat(record.write).isTrue()
        assertThat(record.action).isEqualTo("write")
    }

    @Test
    fun `a denied write writes one write record`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Deny(denial()))

        chain(stage, trail(log)).decide(access(Action.DELETE))

        val record = log.records.single()
        assertThat(record.outcome).isEqualTo(DecisionOutcome.DENY)
        assertThat(record.write).isTrue()
        assertThat(record.action).isEqualTo("delete")
    }

    @Test
    fun `a refused admin operation writes one denial record`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Deny(denial()))

        val outcome = chain(stage, trail(log)).decide(AuthorizationRequest.AdminArea(subject(), area = "idp"))

        assertThat(outcome.allowed).isFalse()
        val record = log.records.single()
        assertThat(record.outcome).isEqualTo(DecisionOutcome.DENY)
        assertThat(record.write).isFalse()
        assertThat(record.action).isEqualTo("admin:idp")
        assertThat(record.resource).isEqualTo("idp")
    }

    @Test
    fun `an allowed admin check writes no record`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        chain(stage, trail(log)).decide(AuthorizationRequest.AdminArea(subject(), area = "idp"))

        assertThat(log.records).isEmpty()
    }

    @Test
    fun `a disabled subject is recorded with the stage disabled and no stage is asked`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))
        val request = AuthorizationRequest.Access(subject(disabled = true), AccessRequest(readPath, Action.READ))

        val outcome = chain(stage, trail(log)).decide(request)

        assertThat(outcome.allowed).isFalse()
        assertThat(stage.asked).isZero()
        assertThat(log.records.single().stage).isEqualTo("disabled")
    }

    @Test
    fun `a request that no stage decides is recorded with the stage default`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.NoOpinion())

        chain(stage, trail(log)).decide(access(Action.READ))

        val record = log.records.single()
        assertThat(record.stage).isEqualTo("default")
        assertThat(record.outcome).isEqualTo(DecisionOutcome.DENY)
    }

    @Test
    fun `the matched rule identifiers are the matched candidates only`() {
        val log = FakeLog()
        val matched = Candidate(DecisionSource.TEAM, "deploy-bot", 0, "ingot.repository.releases.read", false, true, "deny")
        val unmatched = Candidate(DecisionSource.USER, "deploy-bot", 1, "ingot.repository.releases.write", true, false, "no match")
        val stage = FixedStage(AuthorizerDecision.Deny(denial(listOf(matched, unmatched))))

        chain(stage, trail(log)).decide(access(Action.READ))

        assertThat(log.records.single().ruleIds).containsExactly("team:ingot.repository.releases.read")
    }

    @Test
    fun `off writes no record and builds no identifier on the decision path`() {
        val log = FakeLog(enabled = false)
        val ids = IdentifierCounter()
        val stage = FixedStage(AuthorizerDecision.Deny(denial()))

        val withOff = chain(stage, trail(log, ids)).decide(access(Action.WRITE))
        val withoutLog = chain(FixedStage(AuthorizerDecision.Deny(denial()))).decide(access(Action.WRITE))

        assertThat(log.records).isEmpty()
        assertThat(ids.built).isZero()
        assertThat(withOff).isEqualTo(withoutLog)
    }

    @Test
    fun `a log that throws does not change the answer`() {
        val failing = FakeLog(failure = IllegalStateException("database is down"))
        val stage = FixedStage(AuthorizerDecision.Deny(denial()))

        val outcome = chain(stage, trail(failing)).decide(access(Action.WRITE))

        assertThat(outcome).isEqualTo(chain(FixedStage(AuthorizerDecision.Deny(denial()))).decide(access(Action.WRITE)))
    }

    @Test
    fun `a probe of a denial writes no record`() {
        val log = FakeLog()
        val stage = FixedStage(AuthorizerDecision.Deny(denial()))

        val outcome = chain(stage, trail(log)).probe(access(Action.READ))

        assertThat(outcome.allowed).isFalse()
        assertThat(log.records).isEmpty()
    }

    @Test
    fun `a probe of an allowed write writes no record and builds no identifier`() {
        val log = FakeLog()
        val ids = IdentifierCounter()
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        chain(stage, trail(log, ids)).probe(access(Action.WRITE))

        assertThat(log.records).isEmpty()
        assertThat(ids.built).isZero()
    }

    @Test
    fun `a probe answers exactly as a decision of the same request does`() {
        val probed = chain(FixedStage(AuthorizerDecision.Deny(denial())), trail(FakeLog())).probe(access(Action.WRITE))
        val decided = chain(FixedStage(AuthorizerDecision.Deny(denial()))).decide(access(Action.WRITE))

        assertThat(probed).isEqualTo(decided)
    }

    @Test
    fun `a denied probe followed by a denied request records only the request`() {
        val log = FakeLog()
        val decisions = trail(log)

        chain(FixedStage(AuthorizerDecision.Deny(denial())), decisions).probe(access(Action.READ))
        chain(FixedStage(AuthorizerDecision.Deny(denial())), decisions).decide(access(Action.READ))

        val record = log.records.single()
        assertThat(record.outcome).isEqualTo(DecisionOutcome.DENY)
        assertThat(record.decisionId).isEqualTo("decision-1")
    }

    @Test
    fun `a denied probe followed by an allowed write records only the write`() {
        val log = FakeLog()
        val decisions = trail(log)

        chain(FixedStage(AuthorizerDecision.Deny(denial())), decisions).probe(access(Action.READ))
        chain(FixedStage(AuthorizerDecision.Allow(allowance())), decisions).decide(access(Action.WRITE))

        val record = log.records.single()
        assertThat(record.outcome).isEqualTo(DecisionOutcome.ALLOW)
        assertThat(record.write).isTrue()
    }

    @Test
    fun `a failed store in sync refuses an allowed write and applies no decision`() {
        val failed = FakeLog(outcome = RecordOutcome.FAILED)
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        val refusal = catchThrowable { chain(stage, trail(failed)).decide(access(Action.WRITE)) }

        assertThat(refusal).describedAs("refusal of the write").isInstanceOf(DecisionLogUnavailableException::class.java)
        assertThat(failed.records).describedAs("records offered to the log").hasSize(1)
    }

    @Test
    fun `a failed store in sync refuses a denial as well`() {
        val failed = FakeLog(outcome = RecordOutcome.FAILED)
        val stage = FixedStage(AuthorizerDecision.Deny(denial()))

        val refusal = catchThrowable { chain(stage, trail(failed)).decide(access(Action.READ)) }

        assertThat(refusal).describedAs("refusal of the denial").isInstanceOf(DecisionLogUnavailableException::class.java)
    }

    @Test
    fun `a sync recorder whose sink fails refuses the write`() {
        val recorder = DecisionRecorder(
            mode = DecisionLogMode.SYNC,
            sink = DecisionSink { throw IllegalStateException("database is down") },
            logger = InMemoryLogger().logger,
            clock = clock,
        )
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        val refusal = catchThrowable { chain(stage, trail(recorder)).decide(access(Action.WRITE)) }

        recorder.close()
        assertThat(refusal).describedAs("refusal of the write").isInstanceOf(DecisionLogUnavailableException::class.java)
    }

    @Test
    fun `a failed store in async answers the write as before and the writer attempts the record`() {
        val attempted = CountDownLatch(1)
        val recorder = DecisionRecorder(
            mode = DecisionLogMode.ASYNC,
            sink = DecisionSink {
                attempted.countDown()
                throw IllegalStateException("database is down")
            },
            logger = InMemoryLogger().logger,
            clock = clock,
        )
        val stage = FixedStage(AuthorizerDecision.Allow(allowance()))

        val outcome = chain(stage, trail(recorder)).decide(access(Action.WRITE))
        val attemptedInTime = attempted.await(5, SECONDS)
        recorder.close()

        assertThat(outcome.allowed).describedAs("the write is still allowed").isTrue()
        assertThat(attemptedInTime).describedAs("the writer attempted the record within the bounded wait").isTrue()
    }
}
