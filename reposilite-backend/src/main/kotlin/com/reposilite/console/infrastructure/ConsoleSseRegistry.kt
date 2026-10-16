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

package com.reposilite.console.infrastructure

import java.util.concurrent.ConcurrentHashMap

/**
 * The live log connections of the console. Request threads register a connection, close callbacks release it, and the
 * shutdown closes all of them, all at the same time.
 *
 * A connection is released by the single removal of its entry, so a connection is released exactly once however many
 * callers race to release it, and a caller that finds no entry does nothing.
 */
internal class ConsoleSseRegistry<C : Any>(private val onRelease: (SseSession) -> Unit) {

    private val sessions = ConcurrentHashMap<C, SseSession>()

    /**
     * Registers an open connection and returns true. A connection that is already closed when it is registered is
     * released at once and false is returned: its close callback may have run before this registration, found nothing
     * to release, and would otherwise leave its subscriber and ping running.
     */
    fun register(connection: C, session: SseSession, isClosed: () -> Boolean): Boolean {
        sessions[connection] = session
        if (isClosed()) {
            release(connection)
            return false
        }
        return true
    }

    fun release(connection: C) {
        sessions.remove(connection)?.let(onRelease)
    }

    /** The connections at this moment. The list is a copy, so it can be iterated while others register or release. */
    fun snapshot(): List<C> =
        sessions.keys.toList()

}
