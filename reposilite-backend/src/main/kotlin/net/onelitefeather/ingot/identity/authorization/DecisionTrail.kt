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
import com.reposilite.audit.decision.DecisionOutcome
import com.reposilite.audit.decision.DecisionRecord
import net.onelitefeather.ingot.identity.model.Action
import java.time.Clock
import java.util.UUID

/**
 * Hands the decisions of the chain to a [DecisionLog]. A record is built only when the log is enabled, so the mode `off`
 * builds none. A denial and a write are recorded; an allowed read and an allowed admin check are not. A log that throws
 * never changes the answer of a check.
 */
class DecisionTrail(
    private val log: DecisionLog,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** False when the log is off. The chain checks it before it asks for a decision to be observed. */
    val enabled: Boolean
        get() = log.enabled

    internal fun observe(request: AuthorizationRequest, outcome: AuthorizationOutcome, stage: String) {
        val write = request.isWrite()
        if (outcome.allowed && !write) return

        try {
            log.record(recordOf(request, outcome, stage, write))
        } catch (exception: Exception) {
            // The record is lost, the answer is not: a failing log never fails a check.
        }
    }

    private fun recordOf(request: AuthorizationRequest, outcome: AuthorizationOutcome, stage: String, write: Boolean) =
        DecisionRecord(
            decisionId = newId(),
            time = clock.instant(),
            principal = request.subject.principalName ?: request.subject.principalId?.toString() ?: UNKNOWN_PRINCIPAL,
            credentialId = request.subject.credentialName,
            address = null,
            action = request.actionName(),
            resource = request.resourceName(),
            rawResource = null,
            outcome = if (outcome.allowed) DecisionOutcome.ALLOW else DecisionOutcome.DENY,
            write = write,
            stage = stage,
            ruleIds = outcome.decision.trace
                .filter { it.matched }
                .map { "${it.source.name.lowercase()}:${it.node}" },
            revision = null,
        )

    companion object {

        /** The trail of the mode `off`. It is never enabled, so the chain never builds a record for it. */
        val OFF: DecisionTrail = DecisionTrail(DecisionLog.Off)

        private const val UNKNOWN_PRINCIPAL = "unknown"

    }

}

private fun AuthorizationRequest.isWrite(): Boolean =
    when (this) {
        is AuthorizationRequest.Access -> request.action != Action.READ
        is AuthorizationRequest.Node -> node.action?.let { it != "read" } ?: false
        is AuthorizationRequest.RepositoryPath -> action != "read"
        is AuthorizationRequest.AdminArea -> false
    }

private fun AuthorizationRequest.actionName(): String =
    when (this) {
        is AuthorizationRequest.Access -> request.action.name.lowercase()
        is AuthorizationRequest.Node -> node.text
        is AuthorizationRequest.RepositoryPath -> action
        is AuthorizationRequest.AdminArea -> area?.let { "admin:$it" } ?: "instance-admin"
    }

private fun AuthorizationRequest.resourceName(): String =
    when (this) {
        is AuthorizationRequest.Access -> request.fullPath
        is AuthorizationRequest.Node -> node.text
        is AuthorizationRequest.RepositoryPath -> "$repository/$path"
        is AuthorizationRequest.AdminArea -> area ?: "instance"
    }
