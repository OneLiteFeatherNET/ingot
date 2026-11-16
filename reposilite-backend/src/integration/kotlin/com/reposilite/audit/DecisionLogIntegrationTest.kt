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

@file:Suppress("FunctionName")

package com.reposilite.audit

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.Reposilite
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditAction.AUTHORIZATION_WRITE
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.decision.DecisionLogMode
import com.reposilite.audit.decision.DecisionLogSettings
import com.reposilite.token.RoutePermission.WRITE
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SyncDecisionLogIntegrationTest : DecisionLogIntegrationTest() {

    override fun decisionLogSettings(): DecisionLogSettings =
        DecisionLogSettings(DecisionLogMode.SYNC)

    @Test
    fun `should refuse a write with 503 and store no file when its decision record cannot be stored`() {
        // given: a token allowed to deploy, and a store that fails for every decision record
        val (name, secret) = useAuth("deployer", "deployer-secret", routes = mapOf("/releases" to WRITE))

        // when: the artifact is uploaded
        val upload = put("$base/releases/$artifactPath").basicAuth(name, secret).body("content").asString()

        // then: the upload is refused with 503 and the artifact was never written
        assertThat(upload.status).isEqualTo(SERVICE_UNAVAILABLE.code)
        assertThat(recordAttempted.await(5, SECONDS)).describedAs("the store was attempted").isTrue()
        val (manager, managerSecret) = useDefaultManagementToken()
        assertThat(get("$base/releases/$artifactPath").basicAuth(manager, managerSecret).asString().status).isEqualTo(NOT_FOUND.code)
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AsyncDecisionLogIntegrationTest : DecisionLogIntegrationTest() {

    override fun decisionLogSettings(): DecisionLogSettings =
        DecisionLogSettings(DecisionLogMode.ASYNC)

    @Test
    fun `should answer an upload as before when its decision record cannot be stored`() {
        // given: a token allowed to deploy, and a store that fails for every decision record
        val (name, secret) = useAuth("deployer", "deployer-secret", routes = mapOf("/releases" to WRITE))

        // when: the artifact is uploaded and the writer has attempted the record
        val upload = put("$base/releases/$artifactPath").basicAuth(name, secret).body("content").asString()
        assertThat(recordAttempted.await(5, SECONDS)).describedAs("the writer attempted the record").isTrue()

        // then: the upload succeeds and the artifact is there
        assertThat(upload.status).isEqualTo(OK.code)
        val (manager, managerSecret) = useDefaultManagementToken()
        assertThat(get("$base/releases/$artifactPath").basicAuth(manager, managerSecret).asString().status).isEqualTo(OK.code)
    }

}

/**
 * The decision log of the instance, with a store that fails for every decision record. The failure is raised by an audit
 * listener, the way a database error raises it in the store. The latch tells the test that the store was attempted, so no
 * test waits on the writer thread with a sleep.
 */
internal abstract class DecisionLogIntegrationTest : ReposiliteSpecification() {

    protected val artifactPath = "com/example/app/1.0/app-1.0.jar"

    protected val recordAttempted = CountDownLatch(1)

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: AuditEvent ->
            if (event.action == AUTHORIZATION_WRITE) {
                recordAttempted.countDown()
                throw IllegalStateException("the decision record store is down")
            }
        }
    }

}
