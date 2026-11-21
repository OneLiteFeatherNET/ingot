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

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Colour reaches the output only when stdout is a terminal and `NO_COLOR` is unset or empty (https://no-color.org).
 * Otherwise the ANSI sequences the server puts into its output are stripped.
 */
fun colourEnabled(stdoutIsTerminal: Boolean, env: Map<String, String>): Boolean =
    stdoutIsTerminal && env["NO_COLOR"].isNullOrEmpty()

/** [stream] without colour. The bytes are encoded as the console encodes stdout, so text other than ASCII stays intact. */
fun withoutColour(stream: PrintStream): PrintStream =
    PrintStream(AnsiStrippingOutputStream(stream), true, consoleCharset())

/** [text] without ANSI escape sequences. */
fun stripAnsi(text: String): String {
    if (!text.contains('\u001b')) return text
    val sink = ByteArrayOutputStream()
    AnsiStrippingOutputStream(sink).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
    return sink.toString(StandardCharsets.UTF_8)
}

private fun consoleCharset(): Charset =
    System.getProperty("stdout.encoding")?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charset.defaultCharset()

/** Removes ANSI escape sequences from the bytes written through it. A sequence may span several writes. */
class AnsiStrippingOutputStream(out: OutputStream) : FilterOutputStream(out) {

    private enum class State { TEXT, ESCAPE, CONTROL, COMMAND, COMMAND_ESCAPE }

    private var state = State.TEXT

    override fun write(byte: Int) {
        val b = byte and 0xff
        state = when (state) {
            State.TEXT -> if (b == ESC) State.ESCAPE else State.TEXT.also { out.write(b) }
            // ESC [ starts a control sequence, ESC ] an operating system command; any other byte ends a two byte sequence.
            State.ESCAPE -> when (b) {
                '['.code -> State.CONTROL
                ']'.code -> State.COMMAND
                else -> State.TEXT
            }
            // Parameter and intermediate bytes run until a final byte. A byte outside the sequence ends it and is kept.
            State.CONTROL -> when (b) {
                in 0x20..0x3f -> State.CONTROL
                in 0x40..0x7e -> State.TEXT
                else -> State.TEXT.also { out.write(b) }
            }
            State.COMMAND -> when (b) {
                BEL -> State.TEXT
                ESC -> State.COMMAND_ESCAPE
                else -> State.COMMAND
            }
            State.COMMAND_ESCAPE -> if (b == '\\'.code) State.TEXT else State.COMMAND
        }
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        for (index in offset until offset + length) write(bytes[index].toInt())
    }

    private companion object {
        const val ESC = 0x1b
        const val BEL = 0x07
    }

}
