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

package com.reposilite.configuration.local

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8

/**
 * A setting that cannot be read is reported with its name. The messages are read from an in-memory logger, and the standard error
 * fallback is read from an injected stream, so the test touches neither the console nor the process streams.
 */
internal class LocalSettingsErrorReportingTest {

    private fun applyProperty(
        key: String,
        value: String,
        localConfiguration: LocalConfiguration = LocalConfiguration(),
        journalist: Journalist? = InMemoryLogger(),
        errorOutput: PrintStream = PrintStream(ByteArrayOutputStream(), true, UTF_8),
    ): LocalConfiguration {
        LocalConfigurationFactory.applyCustomProperties(
            journalist = journalist,
            localConfiguration = localConfiguration,
            properties = mapOf(key to value),
            errorOutput = errorOutput,
        )
        return localConfiguration
    }

    private fun messagesOf(journalist: Journalist, channel: Channel): List<String> =
        (journalist as InMemoryLogger).messages
            .filter { it.key == channel }
            .map { it.value }

    @Test
    fun `an int setting that is not a number logs one error naming the setting and the type`() {
        // given: a journal that collects the messages of the factory
        val journalist = InMemoryLogger()

        // when: the port is set to a value that is not a number
        assertThatThrownBy { applyProperty("PORT", "abc", journalist = journalist) }
            .describedAs("the conversion error still stops the start")
            .isInstanceOf(NumberFormatException::class.java)

        // then: exactly one error names the setting and the expected type
        val errors = messagesOf(journalist, Channel.ERROR)
        assertThat(errors).describedAs("error lines for PORT").hasSize(1)
        assertThat(errors.single()).describedAs("error text").contains("PORT").contains("int")
    }

    @Test
    fun `a long setting that is not a number logs one error naming the setting and the type`() {
        // given: a journal that collects the messages of the factory
        val journalist = InMemoryLogger()

        // when: the idle timeout is set to a value that is not a number
        assertThatThrownBy { applyProperty("IDLETIMEOUT", "abc", journalist = journalist) }
            .describedAs("the conversion error still stops the start")
            .isInstanceOf(NumberFormatException::class.java)

        // then: exactly one error names the setting and the expected type
        val errors = messagesOf(journalist, Channel.ERROR)
        assertThat(errors).describedAs("error lines for IDLETIMEOUT").hasSize(1)
        assertThat(errors.single()).describedAs("error text").contains("IDLETIMEOUT").contains("long")
    }

    @Test
    fun `a boolean setting with yes logs one warning naming the setting without the value`() {
        // given: a journal that collects the messages of the factory
        val journalist = InMemoryLogger()

        // when: a boolean setting is set to yes
        val configuration = applyProperty("DEBUGENABLED", "yes", journalist = journalist)

        // then: one warning names the setting and does not repeat the value
        val warnings = messagesOf(journalist, Channel.WARN)
        assertThat(warnings).describedAs("warning lines for DEBUGENABLED").hasSize(1)
        assertThat(warnings.single())
            .describedAs("warning text")
            .contains("DEBUGENABLED")
            .contains("true or false")
            .doesNotContain("yes")

        // and: the setting keeps the value it has always had
        assertThat(configuration.debugEnabled.get()).describedAs("DEBUGENABLED after yes").isFalse
    }

    @Test
    fun `a boolean setting with upper case TRUE logs no warning`() {
        // given: a journal that collects the messages of the factory
        val journalist = InMemoryLogger()

        // when: a boolean setting is set to TRUE
        val configuration = applyProperty("DEBUGENABLED", "TRUE", journalist = journalist)

        // then: nothing is warned and the setting is true
        assertThat(messagesOf(journalist, Channel.WARN)).describedAs("warning lines for TRUE").isEmpty()
        assertThat(configuration.debugEnabled.get()).describedAs("DEBUGENABLED after TRUE").isTrue
    }

    @Test
    fun `a valid int setting logs no error and no warning`() {
        // given: a journal that collects the messages of the factory
        val journalist = InMemoryLogger()

        // when: the port is set to a valid number
        val configuration = applyProperty("PORT", "8181", journalist = journalist)

        // then: nothing is reported and the port is set
        assertThat(messagesOf(journalist, Channel.ERROR)).describedAs("error lines for a valid PORT").isEmpty()
        assertThat(messagesOf(journalist, Channel.WARN)).describedAs("warning lines for a valid PORT").isEmpty()
        assertThat(configuration.port.get()).describedAs("PORT after 8181").isEqualTo(8181)
    }

    @Test
    fun `an int setting that is not a number writes its error to the error stream when no journalist exists`() {
        // given: an error stream that collects what is written to it
        val errorBuffer = ByteArrayOutputStream()
        val errorOutput = PrintStream(errorBuffer, true, UTF_8)

        // when: the port is set to a value that is not a number before any journalist exists
        assertThatThrownBy { applyProperty("PORT", "abc", journalist = null, errorOutput = errorOutput) }
            .describedAs("the conversion error still stops the start")
            .isInstanceOf(NumberFormatException::class.java)

        // then: the error is written to the stream, naming the setting and the type
        val written = errorBuffer.toString(UTF_8)
        assertThat(written).describedAs("error stream output").contains("ERROR").contains("PORT").contains("int")
    }

}
