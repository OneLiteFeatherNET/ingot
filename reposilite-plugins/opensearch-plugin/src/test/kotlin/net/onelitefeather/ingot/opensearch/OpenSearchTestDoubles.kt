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

package net.onelitefeather.ingot.opensearch

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import net.onelitefeather.ingot.search.http.HttpAnswer
import net.onelitefeather.ingot.search.http.HttpCall
import net.onelitefeather.ingot.search.http.HttpExchange

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

/** A transport that records every call and answers with [respond]. It never touches the network. */
internal class FakeExchange(
    private val respond: (HttpCall) -> HttpAnswer = { HttpAnswer(200, emptyMap(), "{}") },
) : HttpExchange {

    val calls = mutableListOf<HttpCall>()

    override fun exchange(call: HttpCall): HttpAnswer {
        calls.add(call)
        return respond(call)
    }

}

internal fun testRow(
    repository: String = "releases",
    path: String = "org/example/app/1.0/app-1.0.jar",
    origin: Provenance = Provenance.HOSTED,
): IndexRow = IndexRow(
    file = ArtifactFileRecord(
        repository = repository,
        path = path,
        groupId = "org.example",
        artifactId = "app",
        version = "1.0",
        classifier = null,
        extension = "jar",
        snapshot = false,
    ),
    provenance = origin,
    sizeBytes = 2048,
    lastModified = 1_000,
    sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709",
    sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    indexedAt = 2_000,
)
