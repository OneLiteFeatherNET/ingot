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
package com.reposilite.status.infrastructure

import com.reposilite.status.FailureFacade
import io.javalin.http.Context
import io.javalin.http.ExceptionHandler

internal class FailureHandler(private val failureFacade: FailureFacade) : ExceptionHandler<Exception> {

    /**
     * Runs for every unhandled request exception. It must never throw: when it does, Javalin reports the handler failure
     * and then the original exception as uncaught, which hides what actually went wrong.
     */
    override fun handle(exception: Exception, context: Context) {
        try {
            failureFacade.throwException(source(context), exception)
        } catch (failure: Exception) {
            System.err.println("Failure handler could not record ${exception.javaClass.name}: $failure")
        }
    }

    private fun source(context: Context): String =
        runCatching { context.req().requestURI }.getOrNull() ?: "unknown request"

}
