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

package com.reposilite.audit.application

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.maven.api.ProxyRefusedEvent
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NamespaceProxyRefusedAuditTest {

    private val refusal = ProxyRefusedEvent(
        repository = "guarded",
        gav = "net/onelitefeather/ingot/1.0/ingot-1.0.jar".toLocation(),
        accessToken = null,
        prefix = "net.onelitefeather",
        skippedMembers = listOf("central"),
    )

    @Test
    fun `should write a failed namespace action naming the repository and the path`() {
        // when: the refusal is mapped to an audit event
        val event = proxyRefusedAuditEvent(refusal, tokenName = null)

        // then: the event is a failed NAMESPACE_PROXY_REFUSED of the group and the coordinate
        assertThat(event.action).describedAs("audit action of a refusal").isEqualTo(AuditAction.NAMESPACE_PROXY_REFUSED)
        assertThat(event.success).describedAs("success of a refusal").isFalse()
        assertThat(event.repository).describedAs("repository of a refusal").isEqualTo("guarded")
        assertThat(event.path).describedAs("path of a refusal").isEqualTo("net/onelitefeather/ingot/1.0/ingot-1.0.jar")
    }

    @Test
    fun `should carry the matched prefix and the skipped member names as details`() {
        // when: the refusal is mapped to an audit event
        val details = proxyRefusedAuditEvent(refusal, tokenName = null).details

        // then: the details name the prefix and the skipped members, and nothing else is written
        assertThat(details)
            .describedAs("details of a refusal")
            .containsOnlyKeys("prefix", "skippedMembers")
            .containsEntry("prefix", "net.onelitefeather")
            .containsEntry("skippedMembers", listOf("central"))
    }

    @Test
    fun `should name the anonymous actor when the request carried no known token`() {
        // when: the refusal is mapped without a token name
        val actor = proxyRefusedAuditEvent(refusal, tokenName = null).actor

        // then: the actor is anonymous
        assertThat(actor.name).describedAs("actor of an anonymous refusal").isEqualTo(AuditActor.ANONYMOUS)
    }

    @Test
    fun `should name the token that made the refused request`() {
        // when: the refusal is mapped with the name of the known token
        val actor = proxyRefusedAuditEvent(refusal, tokenName = "ci-bot").actor

        // then: the actor is the token
        assertThat(actor.name).describedAs("actor of a token refusal").isEqualTo("ci-bot")
    }

}
