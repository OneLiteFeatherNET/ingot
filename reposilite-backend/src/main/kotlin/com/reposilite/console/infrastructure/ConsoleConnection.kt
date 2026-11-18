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

/**
 * One console socket as [ConsoleWebSocketHandler] sees it. The handler keys its sessions by the connection, so two
 * instances that stand for the same socket must be equal. Production wraps a Javalin context, tests pass a fake.
 */
internal interface ConsoleConnection {

    /** The address the client connected from, after the forwarded header and the trusted proxies are applied. */
    val host: String

    /** Sends one text frame as it is. */
    fun send(text: String)

    /** Sends [value] as one JSON text frame. */
    fun sendJson(value: Any)

    /** Drops the connection without a close handshake. */
    fun disconnect()

}
