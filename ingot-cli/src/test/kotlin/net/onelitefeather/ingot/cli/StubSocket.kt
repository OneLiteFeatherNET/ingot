/*
 * Copyright (c) 2023 dzikoysk
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
package net.onelitefeather.ingot.cli

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for the console socket, in process on an ephemeral port. It speaks just enough of RFC 6455 for one client at a
 * time: the upgrade, unfragmented text frames and the close frame. Each text frame the client sends is recorded and handed to
 * the test's [handler], which answers through the [Peer]. Nothing here waits by time.
 */
internal class StubSocket(private val handler: (frame: String, peer: Peer) -> Unit) : AutoCloseable {

    /** The server side of one connection. */
    class Peer internal constructor(private val output: OutputStream) {
        fun send(text: String) = synchronized(output) {
            val payload = text.toByteArray(StandardCharsets.UTF_8)
            output.write(0x81)
            writeLength(payload.size)
            output.write(payload)
            output.flush()
        }

        fun close(code: Int) = synchronized(output) {
            output.write(0x88)
            writeLength(2)
            output.write(byteArrayOf((code shr 8).toByte(), code.toByte()))
            output.flush()
        }

        private fun writeLength(length: Int) {
            when {
                length < 126 -> output.write(length)
                else -> {
                    output.write(126)
                    output.write(length shr 8)
                    output.write(length and 0xff)
                }
            }
        }
    }

    val frames = CopyOnWriteArrayList<String>()
    val paths = CopyOnWriteArrayList<String>()
    private val server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
    private val acceptor = Thread({ acceptAll() }, "stub-socket").apply { isDaemon = true; start() }

    val url: String
        get() = "http://127.0.0.1:${server.localPort}"

    private fun acceptAll() {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: return
            Thread({ serve(socket) }, "stub-socket-peer").apply { isDaemon = true; start() }
        }
    }

    private fun serve(socket: Socket) = socket.use {
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val headers = generateSequence { readLine(input) }.takeWhile { it.isNotEmpty() }.toList()
        paths.add(headers.first().split(' ')[1])
        val key = headers.first { it.lowercase().startsWith("sec-websocket-key:") }.substringAfter(':').trim()
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.US_ASCII))
        )
        output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.flush()
        val peer = Peer(output)
        while (true) {
            val first = runCatching { input.readUnsignedByte() }.getOrNull() ?: return
            val second = input.readUnsignedByte()
            var length = (second and 0x7f).toLong()
            if (length == 126L) length = input.readUnsignedShort().toLong()
            if (length == 127L) length = input.readLong()
            val mask = ByteArray(4).also { input.readFully(it) }
            val payload = ByteArray(length.toInt()).also { input.readFully(it) }
            for (index in payload.indices) payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
            when (first and 0x0f) {
                0x1 -> {
                    val text = payload.toString(StandardCharsets.UTF_8)
                    frames.add(text)
                    handler(text, peer)
                }
                0x8 -> {
                    runCatching { peer.close(1000) }
                    return
                }
            }
        }
    }

    private fun readLine(input: DataInputStream): String {
        val line = StringBuilder()
        while (true) {
            val next = input.readUnsignedByte()
            if (next == '\n'.code) return line.toString().trimEnd('\r')
            line.append(next.toChar())
        }
    }

    override fun close() {
        server.close()
        acceptor.join()
    }

}
