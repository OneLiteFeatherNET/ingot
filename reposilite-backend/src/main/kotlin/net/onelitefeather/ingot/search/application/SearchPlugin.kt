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

package net.onelitefeather.ingot.search.application

import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.infrastructure.SqlMirrorCacheLedger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import net.onelitefeather.ingot.search.NoOpArtifactIndexSink
import net.onelitefeather.ingot.search.database.DatabaseArtifactIndex
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.outbox.OutboxArtifactIndexSink
import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import net.onelitefeather.ingot.search.outbox.OutboxStore
import net.onelitefeather.ingot.search.outbox.OutboxWorker
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wires artifact search: the tables, the outbox sink that the write paths call, and the workers that drain the outbox
 * on this replica. The `searchEnabled` local setting switches the feature off; the tables stay in place either way.
 */
@Plugin(
    name = "search",
    dependencies = ["local-configuration", "shared-configuration", "maven"],
    settings = SearchSettings::class,
)
internal class SearchPlugin : ReposilitePlugin() {

    override fun initialize(): Facade {
        val database = reposilite().database
        val clock = Clock.systemUTC()
        SearchSchema.ensure(database, clock)

        val local = facade<LocalConfiguration>()
        if (!local.searchEnabled.get()) {
            logger.info("SEARCH | Artifact search is disabled by the local configuration")
            return SearchFacade(NoOpArtifactIndexSink)
        }
        val engine = local.searchEngine.get()
        if (engine != DATABASE_ENGINE) {
            logger.warn("SEARCH | Unknown search engine '$engine', artifact search stays disabled")
            return SearchFacade(NoOpArtifactIndexSink)
        }

        val state = SearchStateStore(database, clock)
        val outbox = OutboxStore(database, clock)
        val worker = OutboxWorker(
            owner = UUID.randomUUID().toString(),
            outbox = outbox,
            index = DatabaseArtifactIndex(database),
            storage = MavenIndexStorage(facade<MavenFacade>(), clock),
            origins = LedgerOriginLookup(SqlMirrorCacheLedger(database)),
            state = state,
            clock = clock,
        )

        val stopped = AtomicBoolean(false)
        event { _: ReposiliteDisposeEvent -> stopped.set(true) }
        schedulePolling(worker, stopped)

        val sink = OutboxArtifactIndexSink(outbox, state) { logger.warn(it) }
        val onListenerFailure: (Throwable) -> Unit = { failure ->
            logger.warn("SEARCH | Index listener failed, the change is not queued: ${failure.message}")
            runCatching { state.increment(SearchStateKey.DROPPED_ENQUEUES) }
        }
        event(DeployIndexListener(sink, onListenerFailure))
        event(DeleteIndexListener(sink, onListenerFailure))

        return SearchFacade(sink)
    }

    /** Runs one tick after another on the IO pool. The next tick comes after 2 seconds with work, after 10 seconds without. */
    private fun schedulePolling(worker: OutboxWorker, stopped: AtomicBoolean) {
        fun scheduleNext(delay: Duration) {
            if (stopped.get()) return
            reposilite().scheduler.schedule({
                reposilite().ioService.execute {
                    val claimed = try {
                        worker.tick().claimed
                    } catch (failure: Exception) {
                        logger.warn("SEARCH | Outbox tick failed: ${failure.message}")
                        0
                    }
                    scheduleNext(OutboxPolicy.nextPollDelay(claimed))
                }
            }, delay.toMillis(), MILLISECONDS)
        }
        scheduleNext(Duration.ZERO)
    }

    private companion object {
        const val DATABASE_ENGINE = "database"
    }

}
