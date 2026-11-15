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
package com.reposilite.console.api

import com.reposilite.console.CommandContext

interface ReposiliteCommand {

    val sensitiveInput: Boolean
        get() = false

    /**
     * Whether some invocation of this command hides its input. The command listing shows it, because a listing has no
     * arguments to ask [sensitiveInput] about. Defaults to [sensitiveInput].
     */
    val mayHideInput: Boolean
        get() = sensitiveInput

    /** Whether some form of this command is dangerous. Remote clients see it in the command listing. */
    val dangerous: Boolean
        get() = false

    /**
     * Whether this invocation is dangerous, asked after the command line has been parsed. A structured execution holds a
     * dangerous invocation until it is confirmed. Defaults to [dangerous].
     */
    fun isDangerous(arguments: List<String>): Boolean =
        dangerous

    fun execute(context: CommandContext)

}
