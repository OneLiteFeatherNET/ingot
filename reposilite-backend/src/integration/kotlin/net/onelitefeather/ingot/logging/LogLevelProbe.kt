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

import com.reposilite.createWithParameters
import com.reposilite.web.LogLevelWebProbe
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Child process of [LogLevelIntegrationTest]. It starts one instance in the working directory given as the first argument,
 * with the remaining arguments as command-line options, then writes one line per level and per logging route.
 */
object LogLevelProbe {

    @JvmStatic
    fun main(args: Array<String>) {
        val instance = requireNotNull(createWithParameters("--working-directory", args[0], "--no-color", *args.drop(1).toTypedArray()))
        instance.logger.debug("probe debug line")
        instance.logger.info("probe info line")
        LoggerFactory.getLogger(LogLevelProbe::class.java).info("probe slf4j info line")
        LogLevelWebProbe.logInfo()
        exitProcess(0)
    }

}
