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

package com.reposilite.frontend

import panda.std.Result
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

internal class LazyPlaceholderResolver(private val placeholders: Map<String, String>) {

    init {
        verifyPlaceholders()
    }

    fun createProcessedResource(input: InputStream): ResourceSupplier {
        val temporaryResourcePath = createTempFile("reposilite", "frontend-resource")

        input.use { inputStream ->
            temporaryResourcePath.outputStream().use { outputStream ->
                process(inputStream, outputStream)
            }
        }

        return ResourceSupplier {
            Result.supplyThrowing(IOException::class.java) {
                temporaryResourcePath.inputStream()
            }
        }
    }

    fun process(input: InputStream, output: OutputStream) {
        // the assets are small enough to be processed as a whole, which avoids splitting placeholders across reads
        val content = input.readBytes()
        val text = decodeUtf8OrNull(content)

        // binary assets (images, fonts) are copied as they are
        if (text == null) {
            output.write(content)
            return
        }

        var resolved: String = text
        for ((name, value) in placeholders) {
            resolved = resolved.replace(name, value)
        }

        output.write(resolved.toByteArray(UTF_8))
    }

    private fun decodeUtf8OrNull(content: ByteArray): String? =
        try {
            UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString()
        } catch (exception: CharacterCodingException) {
            null
        }

    private fun verifyPlaceholders() {
        placeholders.keys.forEach { placeholder ->
            placeholder.forEach {
                if (it.code > Byte.MAX_VALUE) {
                    throw UnsupportedOperationException("LazyPlaceholderResolve supports only basic placeholders from 1-byte long symbols")
                }
            }
        }
    }

}