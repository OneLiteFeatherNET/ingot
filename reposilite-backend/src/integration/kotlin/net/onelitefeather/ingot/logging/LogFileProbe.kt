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
import kotlin.system.exitProcess

/**
 * Child process of [LogFileIntegrationTest]. It creates one instance in the working directory it is given, which is the
 * start-up path of the server without binding a port. The logging environment is the one of the child process, so the
 * test controls the variables that the process reads when it starts.
 */
object LogFileProbe {

    @JvmStatic
    fun main(args: Array<String>) {
        createWithParameters("--working-directory", args[0], "--no-color")
        exitProcess(0)
    }

}
