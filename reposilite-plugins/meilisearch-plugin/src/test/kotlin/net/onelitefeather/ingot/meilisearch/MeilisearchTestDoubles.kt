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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.backend.BackendLogger
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import net.onelitefeather.ingot.search.http.HttpAnswer
import net.onelitefeather.ingot.search.http.HttpCall
import net.onelitefeather.ingot.search.http.HttpExchange
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand, so that no test reads the system time. */
internal class ManualClock(private var now: Instant = START) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    companion object {
        val START: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }

}

/** A waiter that moves the manual clock by [step] at each pause instead of sleeping. */
internal class ManualWaiter(private val clock: ManualClock, private val step: Duration) : MeilisearchWaiter {

    override fun pause(duration: Duration) {
        clock.advance(if (step.isZero) duration else step)
    }

}

/**
 * An in-process fake of the Meilisearch HTTP surface. It records every call, answers the write requests with task uids, and
 * answers `GET /tasks/{uid}` with the status the test set for the task. It never touches the network.
 */
internal class FakeMeilisearch(@Suppress("unused") private val clock: ManualClock) : HttpExchange {

    val calls = mutableListOf<HttpCall>()
    private val routes = mutableListOf<Pair<(HttpCall) -> Boolean, (HttpCall) -> HttpAnswer>>()
    private val tasks = mutableMapOf<Long, Pair<String, String?>>()
    private val polls = mutableMapOf<Long, Int>()

    fun onDocuments(taskUid: Long) = route({ it.method == "POST" && it.uri.path.endsWith("/documents") }) { accepted(taskUid) }

    fun onDelete(taskUid: Long) = route({ it.method == "DELETE" && it.uri.path.contains("/documents/") }) { accepted(taskUid) }

    fun onTask(uid: Long, status: String, errorCode: String? = null) {
        tasks[uid] = status to errorCode
    }

    fun taskPolls(uid: Long): Int = polls[uid] ?: 0

    /** Answers the request of [method] at exactly [path] with [status] and [body]. */
    fun on(method: String, path: String, status: Int = 200, body: String = "{}") {
        route({ it.method == method && it.uri.path == path }) { HttpAnswer(status, emptyMap(), body) }
    }

    /** Makes every request fail at the transport, as a server that is down does. */
    fun unreachable() {
        down = true
    }

    private var down = false

    private fun route(matches: (HttpCall) -> Boolean, answer: (HttpCall) -> HttpAnswer) {
        routes.add(matches to answer)
    }

    override fun exchange(call: HttpCall): HttpAnswer {
        calls.add(call)
        if (down) throw java.io.IOException("connection refused by the fake")
        val taskUid = TASK_PATH.matchEntire(call.uri.path)?.groupValues?.get(1)?.toLong()
        if (call.method == "GET" && taskUid != null) {
            polls[taskUid] = taskPolls(taskUid) + 1
            return task(taskUid)
        }
        return routes.firstOrNull { (matches, _) -> matches(call) }?.second?.invoke(call)
            ?: HttpAnswer(404, emptyMap(), """{"message":"not routed in the fake","code":"not_found"}""")
    }

    private fun task(uid: Long): HttpAnswer {
        val (status, code) = tasks[uid] ?: return HttpAnswer(404, emptyMap(), "{}")
        val error = code?.let { """,\"error\":{\"code\":\"$it\",\"message\":\"$it\"}""" }.orEmpty()
        return HttpAnswer(200, emptyMap(), """{"uid":$uid,"status":"$status"$error}""".replace("\\", ""))
    }

    private fun accepted(taskUid: Long): HttpAnswer = HttpAnswer(202, emptyMap(), """{"taskUid":$taskUid,"status":"enqueued"}""")

    private companion object {
        val TASK_PATH = Regex(".*/tasks/(\\d+)")
    }

}

/** A context whose environment is the test's own and whose warnings are recorded. The clock is fixed, so no test reads the system time. */
internal class FakeSearchContext(val environment: Map<String, String> = emptyMap()) : SearchBackendContext {

    val warnings = mutableListOf<String>()

    override val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    override val instanceId: String = "replica-test"

    override fun environment(name: String): String? = environment["INGOT_$name"] ?: environment["REPOSILITE_$name"]

    override fun secret(name: String): String? = null

    override fun logger(): BackendLogger = object : BackendLogger {
        override fun info(message: String) = Unit
        override fun warn(message: String) {
            warnings.add(message)
        }
        override fun error(message: String) = Unit
    }

}
