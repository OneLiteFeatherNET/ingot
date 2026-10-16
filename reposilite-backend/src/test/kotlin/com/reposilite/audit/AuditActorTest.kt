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

package com.reposilite.audit

import com.reposilite.audit.api.AuditActor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AuditActorTest {

    @Test
    fun `reads a session identifier of an authenticated caller as name and address`() {
        assertThat(AuditActor.fromSessionIdentifier("deploy-bot@10.0.0.4"))
            .isEqualTo(AuditActor(name = "deploy-bot", address = "10.0.0.4"))
    }

    @Test
    fun `splits at the last at-sign so that the address never contains one`() {
        assertThat(AuditActor.fromSessionIdentifier("team@ci@2001:db8::1"))
            .isEqualTo(AuditActor(name = "team@ci", address = "2001:db8::1"))
    }

    @Test
    fun `reads a bare address as an anonymous caller`() {
        assertThat(AuditActor.fromSessionIdentifier("203.0.113.9"))
            .isEqualTo(AuditActor(name = AuditActor.ANONYMOUS, address = "203.0.113.9"))
    }

    @Test
    fun `treats a blank address as unknown`() {
        assertThat(AuditActor.named("ci", " ").address).isNull()
        assertThat(AuditActor.anonymous("").address).isNull()
    }

}
