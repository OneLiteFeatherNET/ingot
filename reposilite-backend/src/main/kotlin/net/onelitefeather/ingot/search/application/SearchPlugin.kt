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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.application

import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import net.onelitefeather.ingot.repository.RepositoryRegistry
import com.reposilite.maven.infrastructure.SqlMirrorCacheLedger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposilitePostInitializeEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import net.onelitefeather.ingot.readonly.ReadOnlyFacade
import com.reposilite.plugin.reposilite
import com.reposilite.status.FailureFacade
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import com.reposilite.token.AccessTokenFacade
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.repository.index.NoOpArtifactIndexSink
import net.onelitefeather.ingot.search.access.AccessCheckedSearchEngine
import net.onelitefeather.ingot.search.api.SearchApiEndpoints
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendRegistrationEvent
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendRegistry
import net.onelitefeather.ingot.search.backend.BackendResolution
import net.onelitefeather.ingot.search.backend.ClusterBackendAgreement
import net.onelitefeather.ingot.search.backend.CoreSearchBackendContext
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.DatabaseSearchBackendFactory
import net.onelitefeather.ingot.search.backend.SearchBackendHolder
import net.onelitefeather.ingot.search.backend.SearchBackendState
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
import net.onelitefeather.ingot.search.TokenSweep
import net.onelitefeather.ingot.search.verify.VerifyScheduler
import net.onelitefeather.ingot.search.verify.VerifyService
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Wires artifact search. The tables, the outbox sink that the write paths call and the deploy and delete listeners are set up
 * when the plugin initialises, because they only touch the database. The backend itself is chosen after every plugin has
 * initialised (search backend registration, D3): the registration event collects the factories of the plugins, the configured
 * backend is opened off the request threads, and the outbox poller, the schedulers and the routes start from there.
 *
 * The `searchEnabled` local setting switches the feature off; the tables stay in place either way.
 */
@Plugin(
    name = "search",
    dependencies = ["local-configuration", "shared-configuration", "maven", "failure", "read-only"],
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
            event.registerRoutes(SearchApiEndpoints(search, facade<AccessTokenFacade>(), facade<RepositoryRegistry>()))
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
        val state = SearchStateStore(database, clock)
        val outbox = OutboxStore(database, clock)
        val runs = RunStore(database, clock)
        val sink = OutboxArtifactIndexSink(outbox, state) { logger.warn(it) }
        val onListenerFailure: (Throwable) -> Unit = { failure ->
            logger.warn("SEARCH | Index listener failed, the change is not queued: ${failure.message}")
            runCatching { state.increment(SearchStateKey.DROPPED_ENQUEUES) }
        }
        event(DeployIndexListener(sink, onListenerFailure))
        event(DeleteIndexListener(sink, onListenerFailure))

        val wiring = SearchWiring(
            database = database,
            clock = clock,
            engine = engine,
            instanceId = UUID.randomUUID().toString(),
            state = state,
            outbox = outbox,
            runs = runs,
            registry = ArtifactSearchBackendRegistry(builtIn = DatabaseSearchBackendFactory(database, clock)) { logger.warn(it) },
        )
        val started = AtomicReference<Started?>(null)
        event { _: ReposilitePostInitializeEvent -> started.set(startBackend(wiring)) }
        return SearchFacade(
            sink = sink,
            runStatus = runs::status,
            enabled = true,
            live = { started.get()?.live },
            backend = { started.get()?.runtime?.status() ?: BackendStatus.starting(engine) },
        )
    }

    /**
     * Runs after every plugin has initialised: emits the registration event, chooses the factory of the configured id, creates
     * its backend, and starts the outbox poller and the schedulers. The first tick runs on the IO pool and is awaited for a short
     * time, so that a database backend is open before the first request; a slower open continues in the background.
     */
    private fun startBackend(wiring: SearchWiring): Started {
        reposilite().extensions.emitEvent(ArtifactSearchBackendRegistrationEvent(wiring.registry))
        val context = CoreSearchBackendContext(wiring.clock, wiring.instanceId, wiring.engine) { level, message ->
            when (level) {
                "error" -> logger.error(message)
                "warn" -> logger.warn(message)
                else -> logger.info(message)
            }
        }
        val (resolution, backend) = resolveAndCreate(wiring, context)
        val holder = SearchBackendHolder(resolution, wiring.clock, { logger.info(it) }, createBackend = { checkNotNull(backend) })
        val cluster = backend?.let { ClusterBackendAgreement(wiring.state, wiring.runs, wiring.clock, wiring.instanceId, wiring.engine) }
        val runtime = SearchBackendRuntime(holder, cluster, wiring.clock, wiring.engine) {
            wiring.state.get(SearchStateKey.ACCESS_MISMATCHES)?.toLongOrNull() ?: 0L
        }
        val live = backend?.let { startLive(wiring, it, runtime) }

        reposilite().ioService.submit(Runnable { runtime.tick() }).let { first ->
            runCatching { first.get(FIRST_TICK_WAIT_SECONDS, SECONDS) }.onFailure {
                logger.warn("SEARCH | The search backend did not finish its first start in time, it continues in the background")
            }
        }
        val stopped = AtomicBoolean(false)
        event { _: ReposiliteDisposeEvent -> stopped.set(true) }
        backend?.let { installed -> event { _: ReposiliteDisposeEvent -> runCatching { installed.close() } } }
        val tick = reposilite().scheduler.scheduleWithFixedDelay({
            reposilite().ioService.execute {
                runCatching { runtime.tick() }.onFailure { logger.warn("SEARCH | Backend tick failed: ${it.message}") }
            }
        }, BACKEND_TICK_SECONDS, BACKEND_TICK_SECONDS, SECONDS)
        event { _: ReposiliteDisposeEvent -> tick.cancel(false) }
        if (live != null) {
            schedulePolling(live.worker, runtime, stopped, live.paused)
            scheduleRuns(live.rebuild, live.verify, live.sweep, runtime)
        }
        return Started(runtime, live?.handle)
    }

    /** The backend of the configured id and the reason when there is none: a refused, missing or failing factory. */
    private fun resolveAndCreate(wiring: SearchWiring, context: CoreSearchBackendContext): Pair<BackendResolution, ArtifactSearchBackend?> {
        val resolution = wiring.registry.resolve(wiring.engine)
        if (resolution !is BackendResolution.Installed) return resolution to null
        return runCatching { resolution.factory.create(context) }.fold(
            onSuccess = { resolution to it },
            onFailure = {
                BackendResolution.Unavailable(SearchBackendState.OPEN_FAILED, it.message ?: it.javaClass.simpleName) to null
            },
        )
    }

    /** Everything that only an installed backend can run: the engine and administration, the outbox worker and the schedulers. */
    private fun startLive(wiring: SearchWiring, backend: ArtifactSearchBackend, runtime: SearchBackendRuntime): LiveParts {
        val clock = wiring.clock
        val state = wiring.state
        val outbox = wiring.outbox
        val runs = wiring.runs
        val repositoryRegistry = facade<RepositoryRegistry>()
        val readOnlyFacade = facade<ReadOnlyFacade>()
        val ledger = SqlMirrorCacheLedger(wiring.database)
        val worker = OutboxWorker(
            owner = wiring.instanceId,
            outbox = outbox,
            index = backend,
            storage = MavenIndexStorage(repositoryRegistry, clock),
            origins = LedgerOriginLookup(ledger),
            state = state,
            clock = clock,
        )
        val repositories = MavenRepositoryEntries(repositoryRegistry, facade<FailureFacade>())
        val claims = SqlScheduledJobClaims(wiring.database, clock, wiring.instanceId)
        val settings = facade<SharedConfigurationFacade>().getDomainSettings<SearchSettings>()
        val rebuild = RebuildService(
            owner = wiring.instanceId,
            runs = runs,
            backend = backend,
            outbox = outbox,
            repositories = repositories,
            ledger = ledger,
            state = state,
            clock = clock,
        )
        val verify = VerifyService(
            owner = wiring.instanceId,
            runs = runs,
            backend = backend,
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

        val execute: (() -> Unit) -> Unit = { job -> reposilite().ioService.execute { job() } }
        val rebuildScheduler = RebuildScheduler(
            clock = clock,
            settings = { settings.get() },
            claims = claims,
            rebuild = rebuild,
            startup = startup,
            execute = execute,
            onFailure = { logger.warn("SEARCH | $it") },
            readOnly = { readOnlyFacade.isReadOnly() },
        )
        val verifyScheduler = VerifyScheduler(
            clock = clock,
            settings = { settings.get() },
            claims = claims,
            verify = verify,
            runs = runs,
            execute = execute,
            onFailure = { logger.warn("SEARCH | $it") },
            readOnly = { readOnlyFacade.isReadOnly() },
        )
        val tokenSweep = TokenSweep(
            clock = clock,
            batchSize = TOKEN_SWEEP_BATCH,
            interval = TOKEN_SWEEP_INTERVAL,
            keyRows = backend::keyRows,
            readOnly = { readOnlyFacade.isReadOnly() },
            execute = execute,
            onFailure = { logger.warn("SEARCH | $it") },
        )

        val paused = AtomicBoolean(false)
        val checked = AccessCheckedSearchEngine(backend) { repository, location ->
            logger.error("SEARCH | The search backend returned a row the caller may not see, it was dropped: $repository $location")
            runCatching { state.increment(SearchStateKey.ACCESS_MISMATCHES) }
        }
        val admin = SearchIndexAdmin(
            runs = runs,
            rebuild = rebuild,
            verify = verify,
            outbox = outbox,
            worker = worker,
            backend = backend,
            state = state,
            repositories = repositories,
            dispatch = execute,
            paused = paused,
        )
        return LiveParts(
            handle = LiveSearchBackend(checked, admin),
            worker = worker,
            rebuild = rebuildScheduler,
            verify = verifyScheduler,
            sweep = tokenSweep,
            paused = paused,
        )
    }

    /** Ticks the rebuild, verify and token sweep schedulers every 30 seconds, while the backend is available. */
    private fun scheduleRuns(rebuild: RebuildScheduler, verify: VerifyScheduler, sweep: TokenSweep, runtime: SearchBackendRuntime) {
        val tick = reposilite().scheduler.scheduleWithFixedDelay({
            if (runtime.available()) {
                runCatching { rebuild.tick() }.onFailure { logger.warn("SEARCH | Rebuild scheduler tick failed: ${it.message}") }
                runCatching { verify.tick() }.onFailure { logger.warn("SEARCH | Verify scheduler tick failed: ${it.message}") }
                runCatching { sweep.tick() }.onFailure { logger.warn("SEARCH | Token sweep tick failed: ${it.message}") }
            }
        }, SCHEDULER_TICK_SECONDS, SCHEDULER_TICK_SECONDS, SECONDS)
        event { _: ReposiliteDisposeEvent -> tick.cancel(false) }
    }

    /**
     * Runs one tick after another on the IO pool. The next tick comes after 2 seconds with work, after 10 seconds without.
     * A tick claims nothing while [paused] is set, or while the runtime does not let this replica drain; a failed drain of a
     * down backend backs off.
     */
    private fun schedulePolling(worker: OutboxWorker, runtime: SearchBackendRuntime, stopped: AtomicBoolean, paused: AtomicBoolean) {
        fun scheduleNext(delay: Duration) {
            if (stopped.get()) return
            reposilite().scheduler.schedule({
                reposilite().ioService.execute {
                    val claimed = try {
                        if (paused.get() || !runtime.drainAllowed()) 0 else worker.tick().claimed
                    } catch (failure: Exception) {
                        logger.warn("SEARCH | Outbox tick failed: ${failure.message}")
                        runtime.drainFailed()
                        0
                    }
                    scheduleNext(OutboxPolicy.nextPollDelay(claimed))
                }
            }, delay.toMillis(), MILLISECONDS)
        }
        scheduleNext(Duration.ZERO)
    }

    /** What the wiring of the plugin keeps from initialisation: the database and the stores, and the registry of the factories. */
    private class SearchWiring(
        val database: Database,
        val clock: Clock,
        val engine: String,
        val instanceId: String,
        val state: SearchStateStore,
        val outbox: OutboxStore,
        val runs: RunStore,
        val registry: ArtifactSearchBackendRegistry,
    )

    /** The parts that exist once a backend is installed: the poller's worker, the schedulers and the pause flag of the administration. */
    private class LiveParts(
        val handle: LiveSearchBackend,
        val worker: OutboxWorker,
        val rebuild: RebuildScheduler,
        val verify: VerifyScheduler,
        val sweep: TokenSweep,
        val paused: AtomicBoolean,
    )

    /** The result of the start: the runtime for the routes and the poller, and the live backend when one is installed. */
    private class Started(val runtime: SearchBackendRuntime, val live: LiveSearchBackend?)

    private companion object {
        const val SCHEDULER_TICK_SECONDS = 30L
        const val BACKEND_TICK_SECONDS = 5L
        const val FIRST_TICK_WAIT_SECONDS = 10L
        const val TOKEN_SWEEP_BATCH = 1000
        val TOKEN_SWEEP_INTERVAL: Duration = Duration.ofHours(6)
    }

}
