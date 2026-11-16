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

package net.onelitefeather.ingot.identity.login

import net.onelitefeather.ingot.identity.login.BreachCheckMode.OFF
import net.onelitefeather.ingot.identity.login.BreachCheckMode.OFFLINE
import net.onelitefeather.ingot.identity.login.BreachCheckMode.RANGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal class PasswordBreachCheckTest {

    private val password = "correct horse battery staple"

    @TempDir
    lateinit var workingDirectory: Path

    private fun settingsWith(
        mode: BreachCheckMode,
        file: String = "",
        failClosed: Boolean = false,
    ) = LoginSettings(
        passwordPolicy = PasswordPolicySettings(
            breachCheck = BreachCheckSettings(mode = mode, file = file, failClosed = failClosed),
        ),
    )

    private fun checkOf(settings: LoginSettings, range: BreachRangeClient = FakeBreachRange()) =
        PasswordBreachCheck(Reference.reference(settings), range)

    @Test
    fun `the default mode accepts every password and makes no call`() {
        val range = FakeBreachRange(answers = FakeBreachRange.listing(password))

        val verdict = checkOf(settingsWith(OFF), range).verdictOf(password)

        assertThat(verdict).isEqualTo(BreachVerdict.Clear)
        assertThat(range.prefixes).describedAs("the default makes no external call").isEmpty()
    }

    @Test
    fun `range mode sends only the first five hexadecimal characters of the SHA-1`() {
        val range = FakeBreachRange()

        checkOf(settingsWith(RANGE), range).verdictOf(password)

        assertThat(range.prefixes).containsExactly(PasswordBreachCheck.sha1Hex(password).take(5))
        assertThat(range.prefixes.single()).hasSize(5).matches("[0-9A-F]{5}")
        assertThat(range.prefixes.single()).doesNotContain(password)
    }

    @Test
    fun `range mode refuses a password whose suffix the range service lists`() {
        val verdict = checkOf(settingsWith(RANGE), FakeBreachRange(answers = FakeBreachRange.listing(password))).verdictOf(password)

        assertThat(verdict).isEqualTo(BreachVerdict.Breached)
    }

    @Test
    fun `range mode accepts a password when the listed range has the prefix but not its suffix`() {
        val prefix = PasswordBreachCheck.sha1Hex(password).take(5)
        val otherSuffixes = mapOf(prefix to listOf("0000000000000000000000000000000000000:1"))

        val verdict = checkOf(settingsWith(RANGE), FakeBreachRange(answers = otherSuffixes)).verdictOf(password)

        assertThat(verdict).isEqualTo(BreachVerdict.Clear)
    }

    @Test
    fun `range mode reports an unavailable service and carries the fail-closed setting`() {
        val unavailable = FakeBreachRange(unavailable = true)

        assertThat(checkOf(settingsWith(RANGE, failClosed = false), unavailable).verdictOf(password))
            .isEqualTo(BreachVerdict.Unavailable(failClosed = false))
        assertThat(checkOf(settingsWith(RANGE, failClosed = true), unavailable).verdictOf(password))
            .isEqualTo(BreachVerdict.Unavailable(failClosed = true))
    }

    @Test
    fun `offline mode refuses a password whose SHA-1 is in the file and makes no range call`() {
        val file = listFile(PasswordBreachCheck.sha1Hex(password), "0000000000000000000000000000000000000001")
        val range = FakeBreachRange()

        val verdict = checkOf(settingsWith(OFFLINE, file.toString()), range).verdictOf(password)

        assertThat(verdict).isEqualTo(BreachVerdict.Breached)
        assertThat(range.prefixes).isEmpty()
    }

    @Test
    fun `offline mode accepts a password that is not in the file`() {
        val file = listFile("0000000000000000000000000000000000000001")

        val verdict = checkOf(settingsWith(OFFLINE, file.toString())).verdictOf(password)

        assertThat(verdict).isEqualTo(BreachVerdict.Clear)
    }

    @Test
    fun `offline mode accepts the HIBP form with a count after the hash`() {
        val file = listFile("${PasswordBreachCheck.sha1Hex(password)}:17")

        assertThat(checkOf(settingsWith(OFFLINE, file.toString())).verdictOf(password)).isEqualTo(BreachVerdict.Breached)
    }

    @Test
    fun `offline mode fails the startup with the key named when the file is missing`() {
        val missing = workingDirectory.resolve("absent.txt")

        val failure = runCatching { checkOf(settingsWith(OFFLINE, missing.toString())).preload() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure?.message).contains("login.passwordPolicy.breachCheck.file")
    }

    @Test
    fun `offline mode fails the startup with the key named when no file is given`() {
        val failure = runCatching { checkOf(settingsWith(OFFLINE, file = "")).preload() }.exceptionOrNull()

        assertThat(failure?.message).contains("login.passwordPolicy.breachCheck.file")
    }

    @Test
    fun `the default mode starts without reading any file`() {
        val failure = runCatching { checkOf(settingsWith(OFF)).preload() }.exceptionOrNull()

        assertThat(failure).isNull()
    }

    @Test
    fun `the range request carries the prefix only and no identity`() {
        val request = rangeRequest("https://api.pwnedpasswords.com/range/", "ABCDE")

        assertThat(request.uri().toString()).isEqualTo("https://api.pwnedpasswords.com/range/ABCDE")
        assertThat(request.headers().firstValue("Add-Padding")).hasValue("true")
        assertThat(request.headers().firstValue("Authorization")).isEmpty
        assertThat(request.headers().firstValue("Cookie")).isEmpty
        assertThat(request.timeout()).hasValue(Duration.ofSeconds(3))
    }

    private fun listFile(vararg lines: String): Path {
        val file = workingDirectory.resolve("breached-${lines.joinToString("").hashCode()}.txt")
        Files.write(file, lines.toList())
        return file
    }

}
