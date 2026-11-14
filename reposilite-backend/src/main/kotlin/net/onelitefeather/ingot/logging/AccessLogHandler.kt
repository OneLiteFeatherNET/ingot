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

package net.onelitefeather.ingot.logging

import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.AFTER
import io.javalin.community.routing.Route.BEFORE

/**
 * Writes one INFO line per request when the access record is switched on. It is a pair of filters around every request, so it
 * is registered whether or not the external cache bypass is on. The line carries the path without the query string and no
 * header, so a token in a query or an `Authorization` value never reaches it.
 */
internal class AccessLogHandler(
    private val nanoTime: () -> Long,
    private val sink: ((String) -> Unit)? = null,
) : ReposiliteRoutes() {

    private val startRoute = ReposiliteRoute<Unit>("/*", BEFORE) {
        ctx.attribute(START_ATTRIBUTE, nanoTime())
    }

    private val recordRoute = ReposiliteRoute<Unit>("/*", AFTER) {
        val started = ctx.attribute<Long>(START_ATTRIBUTE)
        if (started != null) {
            val durationMillis = (nanoTime() - started) / NANOS_PER_MILLI
            val line = AccessRecord.format(
                method = ctx.method().name,
                path = ctx.path(),
                status = ctx.status().code,
                durationMillis = durationMillis,
                bytes = ctx.res().getHeader("Content-Length"),
                client = ctx.ip(),
                user = ctx.attribute<String>(USER_ATTRIBUTE),
            )
            if (sink != null) sink.invoke(line) else logger.info(line)
        }
    }

    override val routes = routes(startRoute, recordRoute)

    companion object {

        /** The request attribute that holds the start time, read by the record filter. */
        private const val START_ATTRIBUTE = "ingot.accessLog.start"

        /** The request attribute that holds the name of the authenticated token. Set by the identity of the request, when it is resolved. */
        const val USER_ATTRIBUTE = "ingot.accessLog.user"

        private const val NANOS_PER_MILLI = 1_000_000L

    }

}

/** The layout of one access line: `access`, then key=value pairs in a fixed order. A missing value is written as `-`. */
internal object AccessRecord {

    fun format(
        method: String,
        path: String,
        status: Int,
        durationMillis: Long,
        bytes: String?,
        client: String,
        user: String?,
    ): String =
        "access method=$method path=$path status=$status duration_ms=$durationMillis bytes=${bytes.orDash()} client=$client user=${user.orDash()}"

    private fun String?.orDash(): String =
        if (isNullOrBlank()) "-" else this

}
