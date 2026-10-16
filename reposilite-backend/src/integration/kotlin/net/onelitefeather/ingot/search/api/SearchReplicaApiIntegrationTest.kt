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

package net.onelitefeather.ingot.search.api

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.Reposilite
import com.reposilite.ReposiliteFactory
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.local.infrastructure.LOCAL_CONFIGURATION_FILE
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.token.AccessTokenSecurityProvider
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import net.dzikoysk.cdn.KCdnFactory
import net.dzikoysk.cdn.source.Source
import net.onelitefeather.ingot.search.application.SearchFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.ReferenceUtils

/**
 * Two server instances on one database (artifact search spec, 10.5). The instance of [ReposiliteSpecification] is the first
 * replica; [replica] boots a second one on the same H2 file, in a working directory of its own. Both read the index from the
 * database, so they answer alike, and the runs that one replica holds stop the other.
 */
@ExtendWith(SharedH2DatabaseJunitExtension::class)
internal class SearchReplicaApiIntegrationTest : ReposiliteSpecification() {

    private var replicaInstance: Reposilite? = null

    private val replica: Reposilite
        get() = replicaInstance ?: startReplica().also { replicaInstance = it }

    private val replicaBase: String
        get() = "http://localhost:${replica.parameters.port}"

    private val firstFacade: SearchFacade
        get() = useFacade()

    private val secondFacade: SearchFacade
        get() = replica.extensions.facade()

    private val manager: Pair<String, String>
        get() = useDefaultManagementToken()

    @AfterEach
    fun shutdownReplica() {
        replicaInstance?.shutdown()
    }

    @Test
    fun `two replicas answer the same search result from one index`() {
        deployFiles()

        drain(firstFacade)
        drain(secondFacade)

        val first = search(base)
        val second = search(replicaBase)
        assertThat(first.get("items").size()).isEqualTo(3)
        assertThat(second.get("items")).describedAs("the items of the second replica").isEqualTo(first.get("items"))
    }

    @Test
    fun `a file deployed once is indexed once whichever replica drains the outbox`() {
        deployFiles()

        drain(secondFacade)

        assertThat(indexRows(base)).describedAs("rows seen by the first replica").isEqualTo(3)
        assertThat(indexRows(replicaBase)).describedAs("rows seen by the second replica").isEqualTo(3)
        assertThat(indexPending(base)).describedAs("pending entries").isEqualTo(0)
    }

    @Test
    fun `entries claimed by one replica are not claimed again by the other`() {
        deployFiles(count = TEN)

        val claimedByFirst = drain(firstFacade)
        val claimedBySecond = secondFacade.admin!!.drainOutbox().claimed

        assertThat(claimedByFirst).describedAs("entries claimed by the first replica").isEqualTo(TEN)
        assertThat(claimedBySecond).describedAs("entries claimed by the second replica").isEqualTo(0)
        assertThat(indexRows(replicaBase)).describedAs("rows after both replicas ticked").isEqualTo(TEN.toLong())
    }

    @Test
    fun `a rebuild live on one replica refuses a rebuild and a verify on the other`() {
        firstFacade.admin!!.dispatch = { }
        assertThat(post("$base/api/maven/search/index/rebuild").basicAuth(manager.first, manager.second).body("{}").asString().status)
            .describedAs("the first replica starts the rebuild")
            .isEqualTo(202)

        val rebuild = post("$replicaBase/api/maven/search/index/rebuild").basicAuth(manager.first, manager.second).body("{}").asString()
        val verify = post("$replicaBase/api/maven/search/index/verify").basicAuth(manager.first, manager.second).asString()

        assertThat(rebuild.status).describedAs("rebuild on the second replica").isEqualTo(409)
        assertThat(verify.status).describedAs("verify on the second replica").isEqualTo(409)
    }

    @Test
    fun `a verify live on one replica refuses a verify on the other`() {
        firstFacade.admin!!.dispatch = { }
        assertThat(post("$base/api/maven/search/index/verify").basicAuth(manager.first, manager.second).asString().status)
            .describedAs("the first replica starts the verify run")
            .isEqualTo(202)

        val second = post("$replicaBase/api/maven/search/index/verify").basicAuth(manager.first, manager.second).asString()

        assertThat(second.status).describedAs("verify on the second replica").isEqualTo(409)
    }

    /**
     * Pauses the background pollers of both replicas, then deploys [count] files through the first replica. Their outbox
     * entries wait for an explicit tick.
     */
    private fun deployFiles(count: Int = 3) {
        firstFacade.admin!!.pollingPaused = true
        secondFacade.admin!!.pollingPaused = true
        (1..count).forEach { index ->
            useDocument("releases", "com/example/lib$index/1.0.0", "lib$index-1.0.0.jar", store = true)
        }
    }

    /**
     * Ticks the outbox of one replica until it claims nothing, and returns the entries it claimed. The bound turns a stuck
     * entry into a failure, not a hang.
     */
    private fun drain(facade: SearchFacade): Int {
        var claimed = 0
        var ticks = 0
        while (true) {
            val tick = facade.admin!!.drainOutbox().claimed
            if (tick == 0) return claimed
            claimed += tick
            check(++ticks < MAX_DRAIN_TICKS) { "the outbox did not drain in $MAX_DRAIN_TICKS ticks" }
        }
    }

    private fun search(root: String): JsonNode =
        get("$root/api/maven/search?groupId=com.example").asJacksonObject(JsonNode::class).body

    private fun indexRows(root: String): Long =
        get("$root/api/maven/search/index").basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class).body.get("rows").asLong()

    private fun indexPending(root: String): Long =
        get("$root/api/maven/search/index").basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class).body.get("outbox").get("pending").asLong()

    /**
     * Boots a second instance on the database of the first one and on its working directory, so that both read the same
     * shared configuration file and the same repository storage, as replicas of one deployment do. It starts the way
     * [ReposiliteSpecification] starts its own instance, on port 0, and it stops after the test.
     */
    private fun startReplica(): Reposilite {
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("manager:manager-secret")
        parameters.workingDirectoryName = reposiliteWorkingDirectory.absolutePath
        parameters.testEnv = true
        parameters.port = 0
        parameters.database = _database
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()

        val localConfiguration = LocalConfiguration().also {
            ReferenceUtils.setValue(it.webThreadPool, 6)
            ReferenceUtils.setValue(it.ioThreadPool, 4)
        }
        KCdnFactory.createStandard().render(localConfiguration, Source.of(reposiliteWorkingDirectory.resolve(LOCAL_CONFIGURATION_FILE)))

        val logger = PrintStreamLogger(System.out, System.err, Channel.ALL, false)
        val instance = ReposiliteFactory.createReposilite(parameters, logger)
        instance.journalist.setVisibleThreshold(Channel.WARN)
        assertOk(instance.launch())
        return instance
    }

    private companion object {
        const val MAX_DRAIN_TICKS = 100
        const val TEN = 10
    }

}
