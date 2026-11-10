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

package net.onelitefeather.ingot.readonly

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.ReposiliteRunner
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The toggle on an instance whose state is stored, not forced by the environment. Each test starts its own instance.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalReadOnlyToggleIntegrationTest : ReposiliteSpecification() {

    private val manager = ReposiliteRunner.DEFAULT_TOKEN

    @Test
    fun `should report the stored state as off before any change`() {
        // when: the state is read by a principal with the maintenance area
        val response = readState()

        // then: the state is off and its source is the store
        assertThat(response.status).`as`("status of the read").isEqualTo(200)
        assertThat(response.body.get("enabled").asBoolean()).`as`("enabled").isFalse()
        assertThat(response.body.get("source").asText()).`as`("source").isEqualTo("stored")
    }

    @Test
    fun `should turn the state on for a principal with the maintenance area`() {
        // when: the manager turns read-only on
        val response = toggle("POST")

        // then: the answer carries the new state, and a read shows the same state
        assertThat(response.status).`as`("status of the enable").isEqualTo(200)
        assertThat(response.body.get("enabled").asBoolean()).`as`("enabled in the answer").isTrue()
        assertThat(readState().body.get("enabled").asBoolean()).`as`("enabled on the next read").isTrue()
    }

    @Test
    fun `should turn the state off again while the instance is read-only`() {
        // given: read-only is on
        toggle("POST")

        // when: the manager turns it off, which the toggle route must still answer
        val response = toggle("DELETE")

        // then: the state is off again
        assertThat(response.status).`as`("status of the disable").isEqualTo(200)
        assertThat(response.body.get("enabled").asBoolean()).`as`("enabled in the answer").isFalse()
    }

    @Test
    fun `should refuse a toggle from a principal without the maintenance area`() {
        // given: a token without any admin area
        val (name, secret) = useAuth("reader-token", "reader-secret")

        // when: that token tries to turn read-only on
        val response = Unirest.post("$base/api/admin/read-only")
            .basicAuth(name, secret)
            .asString()

        // then: the request is forbidden and the state does not change
        assertThat(response.status).`as`("status of the forbidden toggle").isEqualTo(403)
        assertThat(readState().body.get("enabled").asBoolean()).`as`("enabled after the refusal").isFalse()
    }

    @Test
    fun `should write one audit event for a stored change and none for a repeated one`() {
        // when: read-only is turned on twice
        toggle("POST")
        toggle("POST")

        // then: the audit log holds one read-only event, because the second call changed nothing
        val audit = Unirest.get("$base/api/audit")
            .basicAuth(manager.first, manager.second)
            .asString()
        assertThat(audit.status).`as`("status of the audit read").isEqualTo(200)
        assertThat(audit.body.split("READ_ONLY_CHANGED").size - 1)
            .`as`("read-only audit events in the log")
            .isEqualTo(1)
    }

    private fun readState(): HttpResponse<JsonNode> =
        Unirest.get("$base/api/admin/read-only")
            .basicAuth(manager.first, manager.second)
            .asObject { DEFAULT_OBJECT_MAPPER.readTree(it.contentAsString) }

    private fun toggle(method: String): HttpResponse<JsonNode> {
        val request = if (method == "POST") Unirest.post("$base/api/admin/read-only") else Unirest.delete("$base/api/admin/read-only")
        return request
            .basicAuth(manager.first, manager.second)
            .asObject { DEFAULT_OBJECT_MAPPER.readTree(it.contentAsString) }
    }

}

/**
 * The toggle on an instance that the environment forces read-only. The toggle must refuse to change what the environment
 * decides, and say which variable does.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ForcedReadOnlyToggleIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    private val manager = ReposiliteRunner.DEFAULT_TOKEN

    @Test
    fun `should report the environment as the source of the forced state`() {
        // when: the state is read
        val response = Unirest.get("$base/api/admin/read-only")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: the state is on and the environment is the source
        assertThat(response.status).`as`("status of the read").isEqualTo(200)
        val json = DEFAULT_OBJECT_MAPPER.readTree(response.body)
        assertThat(json.get("enabled").asBoolean()).`as`("enabled").isTrue()
        assertThat(json.get("source").asText()).`as`("source").isEqualTo("environment")
    }

    @Test
    fun `should answer 409 naming the variable when a toggle would change a forced state`() {
        // when: the manager tries to turn the state off
        val response = Unirest.delete("$base/api/admin/read-only")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: the toggle conflicts and names the variable that decides
        assertThat(response.status).`as`("status of the conflicting toggle").isEqualTo(409)
        assertThat(response.body).`as`("body of the conflicting toggle").contains("INGOT_READ_ONLY")
    }

}
