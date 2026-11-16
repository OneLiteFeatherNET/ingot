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

package com.reposilite.audit.decision

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

internal class DecisionRecordTest {

    private val decidedAt = Instant.parse("2026-11-15T10:00:00Z")

    private fun record(
        outcome: DecisionOutcome = DecisionOutcome.DENY,
        write: Boolean = false,
        rawResource: String? = null,
        ruleIds: List<String> = emptyList(),
    ) = DecisionRecord(
        decisionId = "3f2c9a1e-0000-4000-8000-000000000001",
        time = decidedAt,
        principal = "deploy-bot",
        credentialId = "deploy-bot",
        address = "203.0.113.7",
        action = "deploy",
        resource = "com/example/app/1.0/app-1.0.jar",
        rawResource = rawResource,
        outcome = outcome,
        write = write,
        stage = "built-in",
        ruleIds = ruleIds,
        revision = null,
    )

    @Test
    fun `a denied read maps to the denied action with success false`() {
        val event = record(outcome = DecisionOutcome.DENY, write = false).toAuditEvent()

        assertThat(event.action).describedAs("action").isEqualTo(AuditAction.AUTHORIZATION_DENIED)
        assertThat(event.success).describedAs("success").isFalse()
    }

    @Test
    fun `an allowed write maps to the write action with success true`() {
        val event = record(outcome = DecisionOutcome.ALLOW, write = true).toAuditEvent()

        assertThat(event.action).describedAs("action").isEqualTo(AuditAction.AUTHORIZATION_WRITE)
        assertThat(event.success).describedAs("success").isTrue()
    }

    @Test
    fun `a refused write maps to the write action with success false`() {
        val event = record(outcome = DecisionOutcome.DENY, write = true).toAuditEvent()

        assertThat(event.action).describedAs("action").isEqualTo(AuditAction.AUTHORIZATION_WRITE)
        assertThat(event.success).describedAs("success").isFalse()
    }

    @Test
    fun `an allowed read cannot be mapped because it is never recorded`() {
        assertThatThrownBy { record(outcome = DecisionOutcome.ALLOW, write = false).toAuditEvent() }
            .describedAs("mapping of an allowed read")
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `the actor is the principal with the client address`() {
        val event = record().toAuditEvent()

        assertThat(event.actor).isEqualTo(AuditActor.named("deploy-bot", "203.0.113.7"))
    }

    @Test
    fun `the details name the decision context`() {
        val details = record(ruleIds = listOf("ingot.repository.releases.deploy")).toAuditEvent().details

        assertThat(details.keys).describedAs("detail keys").containsExactlyInAnyOrder(
            "decisionId", "decidedAt", "credentialId", "action", "resource",
            "outcome", "stage", "ruleIds", "revision", "obligations",
        )
        assertThat(details["decisionId"]).isEqualTo("3f2c9a1e-0000-4000-8000-000000000001")
        assertThat(details["decidedAt"]).isEqualTo("2026-11-15T10:00:00Z")
        assertThat(details["credentialId"]).isEqualTo("deploy-bot")
        assertThat(details["outcome"]).isEqualTo("DENY")
        assertThat(details["stage"]).isEqualTo("built-in")
        assertThat(details["ruleIds"]).isEqualTo(listOf("ingot.repository.releases.deploy"))
    }

    @Test
    fun `the raw resource is recorded only when it differs from the canonical one`() {
        assertThat(record(rawResource = null).toAuditEvent().details).describedAs("details").doesNotContainKey("rawResource")

        val differing = record(rawResource = "com//example/app/1.0/../1.0/app-1.0.jar").toAuditEvent().details
        assertThat(differing["rawResource"]).isEqualTo("com//example/app/1.0/../1.0/app-1.0.jar")
    }

    @Test
    fun `the revision and the obligations are empty until the policy revision exists`() {
        val details = record().toAuditEvent().details

        assertThat(details["revision"]).describedAs("revision").isNull()
        assertThat(details["obligations"]).describedAs("obligations").isEqualTo(emptyList<String>())
    }

    @Test
    fun `the details never carry a secret field`() {
        val keys = record().toAuditEvent().details.keys

        assertThat(keys).describedAs("detail keys").doesNotContain("secret", "token", "tokenSecret", "password")
    }

}
