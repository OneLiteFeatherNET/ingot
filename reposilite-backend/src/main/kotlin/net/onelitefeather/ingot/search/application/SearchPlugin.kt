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
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.infrastructure.SqlMirrorCacheLedger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import com.reposilite.token.AccessTokenFacade
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.search.NoOpArtifactIndexSink
import net.onelitefeather.ingot.search.api.SearchApiEndpoints
import net.onelitefeather.ingot.search.database.DatabaseArtifactIndex
import net.onelitefeather.ingot.search.database.DatabaseArtifactSearchEngine
import net.onelitefeather.ingot.search.database.IndexRowStore
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.outbox.OutboxArtifactIndexSink
import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import net.onelitefeather.ingot.search.outbox.OutboxStore
import net.onelitefeather.ingot.search.outbox.OutboxWorker
import net.onelitefeather.ingot.search.rebuild.RebuildScheduler
import net.onelitefeather.ingot.search.rebuild.RebuildService
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.verify.VerifyScheduler
import net.onelitefeather.ingot.search.verify.VerifyService
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

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

        val search = wire(database, clock)
        // Registered in both states: a disabled instance answers 404 on every route instead of having none.
        event { event: RoutingSetupEvent ->
            event.registerRoutes(SearchApiEndpoints(search, facade<AccessTokenFacade>(), facade<MavenFacade>()))
        }
        return search
    }

    private fun wire(database: Database, clock: Clock): SearchFacade {
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
        val instanceId = UUID.randomUUID().toString()
        val maven = facade<MavenFacade>()
        val ledger = SqlMirrorCacheLedger(database)
        val index = DatabaseArtifactIndex(database)
        val worker = OutboxWorker(
            owner = instanceId,
            outbox = outbox,
            index = index,
            storage = MavenIndexStorage(maven, clock),
            origins = LedgerOriginLookup(ledger),
            state = state,
            clock = clock,
        )

        val runs = RunStore(database, clock)
        val rows = IndexRowStore(database)
        val repositories = MavenRepositoryEntries(maven)
        val claims = SqlScheduledJobClaims(database, clock, instanceId)
        val settings = facade<SharedConfigurationFacade>().getDomainSettings<SearchSettings>()
        val rebuild = RebuildService(
            owner = instanceId,
            runs = runs,
            rows = rows,
            index = index,
            outbox = outbox,
            repositories = repositories,
            ledger = ledger,
            state = state,
            clock = clock,
        )
        val verify = VerifyService(
            owner = instanceId,
            runs = runs,
            rows = rows,
            index = index,
            outbox = outbox,
            repositories = repositories,
            ledger = ledger,
            state = state,
            clock = clock,
            random = Random.Default,
            sampleSize = { settings.get().verifySampleSize },
        )
        // The startup rules read the heartbeat, so they are decided before the first worker tick writes a new one.
        val startup = runCatching { rebuild.startupTrigger(settings.get().gapRebuild) }
            .onFailure { logger.warn("SEARCH | Could not decide the startup rebuild: ${it.message}") }
            .getOrNull()

        val stopped = AtomicBoolean(false)
        event { _: ReposiliteDisposeEvent -> stopped.set(true) }
        schedulePolling(worker, stopped)
        val execute: (() -> Unit) -> Unit = { job -> reposilite().ioService.execute { job() } }
        val rebuildScheduler = RebuildScheduler(
            clock = clock,
            settings = { settings.get() },
            claims = claims,
            rebuild = rebuild,
            startup = startup,
            execute = execute,
            onFailure = { logger.warn("SEARCH | $it") },
        )
        val verifyScheduler = VerifyScheduler(
            clock = clock,
            settings = { settings.get() },
            claims = claims,
            verify = verify,
            runs = runs,
            execute = execute,
            onFailure = { logger.warn("SEARCH | $it") },
        )
        scheduleRuns(rebuildScheduler, verifyScheduler)

        val sink = OutboxArtifactIndexSink(outbox, state) { logger.warn(it) }
        val onListenerFailure: (Throwable) -> Unit = { failure ->
            logger.warn("SEARCH | Index listener failed, the change is not queued: ${failure.message}")
            runCatching { state.increment(SearchStateKey.DROPPED_ENQUEUES) }
        }
        event(DeployIndexListener(sink, onListenerFailure))
        event(DeleteIndexListener(sink, onListenerFailure))

        val search = DatabaseArtifactSearchEngine(database, clock)
        val admin = SearchIndexAdmin(
            runs = runs,
            rebuild = rebuild,
            verify = verify,
            outbox = outbox,
            worker = worker,
            rows = rows,
            state = state,
            repositories = repositories,
            dispatch = execute,
        )
        return SearchFacade(sink, runs::status, search, admin)
    }

    /** Ticks the rebuild and verify schedulers every 30 seconds, as the cleanup scheduler does. The runs themselves use the IO pool. */
    private fun scheduleRuns(rebuild: RebuildScheduler, verify: VerifyScheduler) {
        val tick = reposilite().scheduler.scheduleWithFixedDelay({
            runCatching { rebuild.tick() }.onFailure { logger.warn("SEARCH | Rebuild scheduler tick failed: ${it.message}") }
            runCatching { verify.tick() }.onFailure { logger.warn("SEARCH | Verify scheduler tick failed: ${it.message}") }
        }, SCHEDULER_TICK_SECONDS, SCHEDULER_TICK_SECONDS, SECONDS)
        event { _: ReposiliteDisposeEvent -> tick.cancel(false) }
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
        const val SCHEDULER_TICK_SECONDS = 30L
    }

}
