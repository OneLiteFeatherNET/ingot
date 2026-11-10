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
package com.reposilite

import com.reposilite.configuration.generateRequestedConfiguration
import com.reposilite.configuration.local.LocalConfigurationFileException
import net.onelitefeather.ingot.backup.runDatabaseSnapshot
import net.onelitefeather.ingot.deploy.UnknownDeployTargetException
import net.onelitefeather.ingot.deploy.toStartupRefusal
import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val parameters = parseParameters(*args)
        if (parameters.isSnapshotRun()) {
            // The snapshot runs before any instance is built, so it starts no plugin, no port and no scheduler
            exitProcess(runDatabaseSnapshot(parameters))
        }
        createFromParameters(parameters)?.launch()
    } catch (exception: UnknownDeployTargetException) {
        // A refused target is an operator error, so it ends the process with one line and no stack trace.
        val refusal = exception.toStartupRefusal()
        System.err.println(refusal.line)
        exitProcess(refusal.exitCode)
    } catch (exception: LocalConfigurationFileException) {
        // An unreadable secret file is an operator error too: one line naming the variable and the path, and the same exit code.
        System.err.println("Ingot did not start: ${exception.message}")
        exitProcess(1)
    }
}

fun createWithParameters(vararg args: String): Reposilite? =
    createFromParameters(parseParameters(*args))

fun parseParameters(vararg args: String): ReposiliteParameters {
    val parameters = ReposiliteParameters()

    CommandLine(parameters)
        .setCaseInsensitiveEnumValuesAllowed(true)
        .execute(*args)

    return parameters
}

/**
 * Builds the instance for the given parameters. The informational flags and a snapshot run build nothing and return null.
 * A snapshot run is performed by [runDatabaseSnapshot] from [main].
 */
fun createFromParameters(parameters: ReposiliteParameters): Reposilite? {
    if (parameters.usageHelpRequested || parameters.versionInfoRequested || parameters.isSnapshotRun()) {
        return null
    }

    if (parameters.configurationRequested != null) {
        generateRequestedConfiguration(parameters)
        return null
    }

    return ReposiliteFactory.createReposilite(parameters)
}

private fun ReposiliteParameters.isSnapshotRun(): Boolean =
    snapshotDatabaseRequested && !usageHelpRequested && !versionInfoRequested
