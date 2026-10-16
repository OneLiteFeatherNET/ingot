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

import com.reposilite.ExperimentalLocalSpecificationJunitExtension
import com.reposilite.ExperimentalRemoteSpecificationJunitExtension
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditAction.ARTIFACT_DELETED
import com.reposilite.audit.api.AuditAction.ARTIFACT_DEPLOYED
import com.reposilite.audit.api.AuditAction.LOGIN_FAILED
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.audit.api.AuditEventDto
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.RoutePermission.READ
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest.delete
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ExperimentalRemoteSpecificationJunitExtension::class)
internal class ExperimentalRemoteAuditIntegrationTest : AuditIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteAuditIntegrationTest : AuditIntegrationTest()

@ExtendWith(ExperimentalLocalSpecificationJunitExtension::class)
internal class ExperimentalLocalAuditIntegrationTest : AuditIntegrationTest()

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalAuditIntegrationTest : AuditIntegrationTest()

internal abstract class AuditIntegrationTest : ReposiliteSpecification() {

    private val artifactPath = "com/example/app/1.0/app-1.0.jar"

    @Test
    fun `should record a deployment and a deletion with the deployer, newest first`() {
        // given: a token allowed to deploy
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))

        // when: an artifact is deployed and then deleted
        assertThat(put("$base/releases/$artifactPath").basicAuth(name, secret).body("content").asString().status).isEqualTo(OK.code)
        assertThat(delete("$base/releases/$artifactPath").basicAuth(name, secret).asString().status).isEqualTo(OK.code)

        // then: both events name the deployer, the repository and the path
        val events = auditEvents()
        val deployed = events.single { it.action == ARTIFACT_DEPLOYED }
        val deleted = events.single { it.action == ARTIFACT_DELETED }
        assertThat(deployed.actor).isEqualTo("deployer")
        assertThat(deployed.repository).isEqualTo("releases")
        assertThat(deployed.path).isEqualTo(artifactPath)
        assertThat(deleted.actor).isEqualTo("deployer")
        assertThat(events.indexOf(deleted)).isLessThan(events.indexOf(deployed))
    }

    @Test
    fun `should record the creation of a token with its name and no secret`() {
        // given: a token created through the facade, as the token endpoints do
        useAuth("creator", "creator-secret", listOf(MANAGER))

        // when: the audit log is read
        val created = auditEvents(query = "?action=TOKEN_CREATED").single { it.details?.get("token")?.asText() == "creator" }

        // then: the event names the token and the secret is nowhere in the response
        assertThat(created.action).isEqualTo(TOKEN_CREATED)
        assertThat(created.details.toString()).doesNotContain("creator-secret")
    }

    @Test
    fun `should not show the audit log to callers without the manager permission`() {
        // given: a token that may only read
        val (name, secret) = useAuth("reader", "reader-secret", routes = mapOf("/releases" to READ))

        // when: the audit log is requested anonymously and with the reader token
        val anonymous = get("$base/api/audit").asString()
        val reader = get("$base/api/audit").basicAuth(name, secret).asString()

        // then: an anonymous caller is asked to sign in, and a signed-in reader is refused
        assertThat(anonymous.status).isEqualTo(UNAUTHORIZED.code)
        assertThat(reader.status).isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `should export the audit log as csv with a header row`() {
        // given: at least one audited event
        useAuth("exporter", "exporter-secret", listOf(MANAGER))

        // when: the export is requested by a manager
        val (name, secret) = useDefaultManagementToken()
        val response = get("$base/api/audit/export?action=TOKEN_CREATED").basicAuth(name, secret).asString()

        // then: the response is CSV with the column names first
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.headers.getFirst("Content-Type")).startsWith("text/csv")
        assertThat(response.body.lines().first()).isEqualTo("id,timestamp,actor,actorAddress,action,repository,path,success,occurrences,details")
        assertThat(response.body).contains("TOKEN_CREATED")
    }

    @Test
    fun `should fold repeated failed logins into rows that count their attempts`() {
        // when: the same unknown name is rejected three times
        repeat(3) {
            assertThat(get("$base/api/audit").basicAuth("intruder", "wrong-secret").asString().status).isEqualTo(UNAUTHORIZED.code)
        }

        // then: the failures are recorded under the attempted name, and their count adds up to three
        val failures = auditEvents(query = "?action=LOGIN_FAILED").filter { it.actor == "intruder" }
        assertThat(failures).isNotEmpty()
        assertThat(failures.map { it.action }).containsOnly(LOGIN_FAILED)
        assertThat(failures.sumOf { it.occurrences }).isEqualTo(3)
        assertThat(failures.map { it.success }).containsOnly(false)
    }

    @Test
    fun `should list the events of every action named in a comma separated action filter`() {
        // given: a token creation and a failed login
        useAuth("multi-creator", "multi-creator-secret", listOf(MANAGER))
        assertThat(get("$base/api/audit").basicAuth("multi-intruder", "wrong-secret").asString().status).isEqualTo(UNAUTHORIZED.code)

        // when: both actions are requested in one comma separated parameter
        val actions = auditEvents(query = "?action=TOKEN_CREATED,LOGIN_FAILED").map { it.action }

        // then: events of both actions are listed and no other action
        assertThat(actions).contains(TOKEN_CREATED, LOGIN_FAILED)
        assertThat(actions).containsOnly(TOKEN_CREATED, LOGIN_FAILED)
    }

    @Test
    fun `should list the events of every action named in repeated action parameters`() {
        // given: a token creation and a failed login
        useAuth("repeat-creator", "repeat-creator-secret", listOf(MANAGER))
        assertThat(get("$base/api/audit").basicAuth("repeat-intruder", "wrong-secret").asString().status).isEqualTo(UNAUTHORIZED.code)

        // when: both actions are requested as repeated parameters
        val actions = auditEvents(query = "?action=TOKEN_CREATED&action=LOGIN_FAILED").map { it.action }

        // then: events of both actions are listed and no other action
        assertThat(actions).contains(TOKEN_CREATED, LOGIN_FAILED)
        assertThat(actions).containsOnly(TOKEN_CREATED, LOGIN_FAILED)
    }

    @Test
    fun `should answer 400 naming every unknown action on the list and on the export`() {
        // given: a manager
        val (name, secret) = useDefaultManagementToken()

        // when: both endpoints get a filter with two unknown actions
        val list = get("$base/api/audit?action=NOPE_ONE,TOKEN_CREATED&action=NOPE_TWO").basicAuth(name, secret).asString()
        val export = get("$base/api/audit/export?action=NOPE_ONE,TOKEN_CREATED&action=NOPE_TWO").basicAuth(name, secret).asString()

        // then: both refuse the request and name both unknown actions
        assertThat(list.status).isEqualTo(BAD_REQUEST.code)
        assertThat(list.body).contains("NOPE_ONE").contains("NOPE_TWO")
        assertThat(export.status).isEqualTo(BAD_REQUEST.code)
        assertThat(export.body).contains("NOPE_ONE").contains("NOPE_TWO")
    }

    @Test
    fun `should export the events of every action named in the action filter`() {
        // given: a token creation and a failed login
        useAuth("export-creator", "export-creator-secret", listOf(MANAGER))
        assertThat(get("$base/api/audit").basicAuth("export-intruder", "wrong-secret").asString().status).isEqualTo(UNAUTHORIZED.code)

        // when: the export is requested with a comma separated and a repeated action
        val (name, secret) = useDefaultManagementToken()
        val response = get("$base/api/audit/export?action=TOKEN_CREATED,LOGIN_FAILED&action=TOKEN_CREATED").basicAuth(name, secret).asString()

        // then: rows of both actions are exported
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body).contains("TOKEN_CREATED").contains("LOGIN_FAILED")
    }

    private fun auditEvents(query: String = ""): List<AuditEventDto> {
        val (name, secret) = useDefaultManagementToken()
        return get("$base/api/audit$query")
            .basicAuth(name, secret)
            .asJacksonObject(AuditPageDto::class)
            .body
            .events
    }

}
