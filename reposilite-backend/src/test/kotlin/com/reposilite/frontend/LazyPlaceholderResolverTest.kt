package com.reposilite.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class LazyPlaceholderResolverTest {

    private val bufferSize = 8192
    private val placeholder =  "{{PLACEHOLDER}}"
    private val defaultResolver = LazyPlaceholderResolver(mapOf(placeholder to "Reposilite"))

    @Test
    fun `should support only 1-byte long symbols in placeholders`() {
        assertThrows<UnsupportedOperationException> {
            LazyPlaceholderResolver(mapOf("🎃" to "Reposilite"))
        }
    }

    @Test
    fun `should resolve placeholder`() {
        val input = placeholder.byteInputStream()
        val output = ByteArrayOutputStream()

        defaultResolver.process(input, output)
        val result = output.toByteArray().decodeToString()

        assertThat(result).contains("Reposilite")
    }

    @Test
    fun `should resolve full length placeholder`() {
        val placeholderBytes = placeholder.toByteArray()
        val input = (ByteArray(bufferSize - placeholderBytes.size + 1) + placeholderBytes + ByteArray(bufferSize - 1)).inputStream()
        val output = ByteArrayOutputStream()

        defaultResolver.process(input, output)
        val result = output.toByteArray().decodeToString()

        assertThat(result).contains("Reposilite")
    }

    @Test
    fun `should resolve one byte length placeholder`() {
        val placeholderBytes = placeholder.toByteArray()
        val input = (ByteArray(bufferSize - 1) + placeholderBytes + ByteArray(bufferSize - 1)).inputStream()
        val output = ByteArrayOutputStream()

        defaultResolver.process(input, output)
        val result = output.toByteArray().decodeToString()

        assertThat(result).contains("Reposilite")
    }

    @Test
    fun `should not write zero bytes when a short read ends with a placeholder prefix`() {
        val content = "a".repeat(1023) + "{" + "b".repeat(100)
        // the first read fills the 1024 byte buffer, the following reads return only a few bytes
        val input = ShortReadInputStream(content.toByteArray()) { readIndex -> if (readIndex == 0) 1024 else 7 }

        val result = resolve(defaultResolver, input)

        assertThat(result.decodeToString()).isEqualTo(content)
    }

    @Test
    fun `should replace placeholder split across short reads`() {
        val content = "xxx$placeholder yyy"
        val input = ShortReadInputStream(content.toByteArray()) { 7 }

        val result = resolve(defaultResolver, input)

        assertThat(result.decodeToString()).isEqualTo("xxxReposilite yyy")
    }

    @ParameterizedTest
    @ValueSource(ints = [1024, 1025, 2048])
    fun `should replace placeholder at the 1024 byte boundary for content of length`(length: Int) {
        val start = minOf(1017, length - placeholder.length)
        val content = "a".repeat(start) + placeholder + "a".repeat(length - start - placeholder.length)

        val result = resolve(defaultResolver, content.toByteArray().inputStream())

        assertThat(result.decodeToString()).isEqualTo(content.replace(placeholder, "Reposilite"))
    }

    @Test
    fun `should pass invalid UTF-8 content through unchanged`() {
        val binary = ByteArray(2048) { 0xFF.toByte() } + placeholder.toByteArray()

        val result = resolve(defaultResolver, binary.inputStream())

        assertThat(result).isEqualTo(binary)
    }

    @Test
    fun `should resolve every placeholder in a bundle larger than 64 KB without zero bytes`() {
        val snippet = "const a=`\${n}`;let b={{REPOSILITE.TITLE}};var h=m&&m[0];\n"
        val bundle = snippet.repeat(1200)
        assertThat(bundle.length).isGreaterThan(64 * 1024)
        val resolver = LazyPlaceholderResolver(mapOf("{{REPOSILITE.TITLE}}" to "Reposilite"))
        val input = ShortReadInputStream(bundle.toByteArray()) { 1000 }

        val result = resolve(resolver, input).decodeToString()

        assertThat(result).doesNotContain("\u0000")
        assertThat(result).doesNotContain("{{REPOSILITE.TITLE}}")
        assertThat(result).isEqualTo(bundle.replace("{{REPOSILITE.TITLE}}", "Reposilite"))
    }

    @Test
    fun `should replace the longest placeholder even when a shorter key sorts last`() {
        val resolver = LazyPlaceholderResolver(mapOf("{{Z}}" to "z", "{{LONG_PLACEHOLDER}}" to "long"))
        val content = "a".repeat(1020) + "{{LONG_PLACEHOLDER}}" + "b".repeat(2000)

        val result = resolve(resolver, content.toByteArray().inputStream())

        assertThat(result.decodeToString()).isEqualTo("a".repeat(1020) + "long" + "b".repeat(2000))
    }

    private fun resolve(resolver: LazyPlaceholderResolver, input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        resolver.process(input, output)
        return output.toByteArray()
    }

}

/**
 * An [InputStream] whose reads may return fewer bytes than requested, as streams from jar entries
 * and sockets do. [maxBytesPerRead] receives the zero-based index of each read.
 */
private class ShortReadInputStream(
    bytes: ByteArray,
    private val maxBytesPerRead: (readIndex: Int) -> Int,
) : InputStream() {

    private val delegate = ByteArrayInputStream(bytes)
    private var readIndex = 0

    override fun read(): Int = delegate.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val limit = minOf(length, maxBytesPerRead(readIndex++).coerceAtLeast(1))
        return delegate.read(buffer, offset, limit)
    }

}
