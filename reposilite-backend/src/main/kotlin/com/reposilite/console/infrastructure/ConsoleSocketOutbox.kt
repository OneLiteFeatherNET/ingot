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

import java.util.concurrent.ThreadFactory

/** The number of lines a console WebSocket may hold while its client reads slower than the server logs. */
internal const val CONSOLE_SOCKET_QUEUE_CAPACITY = 1000

/** Not implemented yet: the red tests run against this stub. */
internal class ConsoleSocketOutbox(
    send: (String) -> Unit,
    onFailure: (Exception) -> Unit,
    capacity: Int = CONSOLE_SOCKET_QUEUE_CAPACITY,
    threadFactory: ThreadFactory = ThreadFactory { Thread(it) },
) {
    fun start(): Unit = TODO("not implemented yet")

    fun offer(line: String): Boolean = TODO("not implemented yet")

    fun put(line: String): Unit = TODO("not implemented yet")

    fun close(): Unit = TODO("not implemented yet")
}
