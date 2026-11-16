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
import com.reposilite.audit.api.AuditEvent
import java.time.Instant

enum class DecisionOutcome { ALLOW, DENY }

/**
 * One authorization decision that is kept for incident reviews. It holds the names of the credential and the rule,
 * never the token secret: there is no field that could carry one.
 *
 * [rawResource] is set only when the request path differs from the canonical [resource]. [revision] stays null until
 * the policy revision exists, and [obligations] stay empty until obligations are introduced.
 */
data class DecisionRecord(
    val decisionId: String,
    val time: Instant,
    val principal: String,
    val credentialId: String?,
    val address: String?,
    val action: String,
    val resource: String,
    val rawResource: String?,
    val outcome: DecisionOutcome,
    val write: Boolean,
    val stage: String,
    val ruleIds: List<String>,
    val revision: Long?,
    val obligations: List<String> = emptyList(),
) {

    /**
     * The audit event that stores this record. Writes map to [AuditAction.AUTHORIZATION_WRITE], other denials to
     * [AuditAction.AUTHORIZATION_DENIED]. An allowed read has no record, so mapping one is refused.
     */
    fun toAuditEvent(): AuditEvent {
        require(write || outcome == DecisionOutcome.DENY) { "An allowed read is never recorded" }

        val action = if (write) AuditAction.AUTHORIZATION_WRITE else AuditAction.AUTHORIZATION_DENIED
        return AuditEvent(
            action = action,
            actor = AuditActor.named(principal, address),
            details = buildMap {
                put("decisionId", decisionId)
                put("decidedAt", time.toString())
                put("credentialId", credentialId)
                put("action", this@DecisionRecord.action)
                put("resource", resource)
                rawResource?.let { put("rawResource", it) }
                put("outcome", outcome.name)
                put("stage", stage)
                put("ruleIds", ruleIds)
                put("revision", revision)
                put("obligations", obligations)
            },
            success = outcome == DecisionOutcome.ALLOW,
        )
    }

}
