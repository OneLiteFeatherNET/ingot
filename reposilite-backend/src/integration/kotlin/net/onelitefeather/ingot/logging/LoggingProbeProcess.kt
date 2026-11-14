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

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Runs one logging probe in a child JVM. The environment of the child is the one given, with every logging variable of the
 * machine removed first, so the probe depends on the test and not on the host.
 *
 * The integration resources hold a copy of `tinylog.properties` without the rolling file writer. The child names the shipped
 * configuration instead, so the default start writes the rolling file exactly as the server does.
 */
internal object LoggingProbeProcess {

    private const val TIMEOUT_SECONDS = 120L

    val LOGGING_VARIABLES = listOf(
        "INGOT_LOG_FILE", "REPOSILITE_LOG_FILE",
        "INGOT_LOG_FORMAT", "REPOSILITE_LOG_FORMAT",
        "INGOT_LOG_LEVEL", "REPOSILITE_LOG_LEVEL",
        "INGOT_LOG_LEVELS", "REPOSILITE_LOG_LEVELS",
        "INGOT_ACCESS_LOG", "REPOSILITE_ACCESS_LOG",
    )

    data class Result(val exitCode: Int, val output: String)

    fun run(
        probe: Class<*>,
        workingDirectory: Path,
        environment: Map<String, String>,
        arguments: List<String> = emptyList(),
        jvmArguments: List<String> = emptyList(),
    ): Result {
        val javaBinary = System.getProperty("java.home") + "/bin/java"
        val command = listOf(
            javaBinary,
            "-Dtinylog.configuration=${shippedTinyLogConfiguration()}",
        ) + jvmArguments + listOf(
            "-cp", System.getProperty("java.class.path"),
            probe.name,
            workingDirectory.toAbsolutePath().toString(),
        ) + arguments
        val builder = ProcessBuilder(command).redirectErrorStream(true).directory(workingDirectory.toFile())
        LOGGING_VARIABLES.forEach { builder.environment().remove(it) }
        builder.environment().putAll(environment)

        val process = builder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "probe did not finish within $TIMEOUT_SECONDS seconds" }
        return Result(process.exitValue(), output)
    }

    /** The `tinylog.properties` of the main resources, found on the class path and not in the integration resources. */
    private fun shippedTinyLogConfiguration(): String =
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { Paths.get(it, "tinylog.properties") }
            .first { Files.isRegularFile(it) && !it.toString().contains("integration") }
            .toAbsolutePath()
            .toString()

}
