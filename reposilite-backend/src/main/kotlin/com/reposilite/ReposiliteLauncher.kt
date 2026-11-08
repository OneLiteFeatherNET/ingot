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
import net.onelitefeather.ingot.deploy.UnknownDeployTargetException
import net.onelitefeather.ingot.deploy.toStartupRefusal
import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        createWithParameters(*args)?.launch()
    } catch (exception: UnknownDeployTargetException) {
        // A refused target is an operator error, so it ends the process with one line and no stack trace.
        val refusal = exception.toStartupRefusal()
        System.err.println(refusal.line)
        exitProcess(refusal.exitCode)
    }
}

fun createWithParameters(vararg args: String): Reposilite? {
    val parameters = ReposiliteParameters()

    CommandLine(parameters)
        .setCaseInsensitiveEnumValuesAllowed(true)
        .execute(*args)

    if (parameters.usageHelpRequested || parameters.versionInfoRequested) {
        return null
    }

    if (parameters.configurationRequested != null) {
        generateRequestedConfiguration(parameters)
        return null
    }

    return ReposiliteFactory.createReposilite(parameters)
}
