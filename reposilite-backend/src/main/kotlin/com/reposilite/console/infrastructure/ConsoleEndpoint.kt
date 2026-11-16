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
package com.reposilite.console.infrastructure

import com.reposilite.console.ConfirmationNeeded
import com.reposilite.console.ConsoleFacade
import com.reposilite.console.MAX_COMMAND_LENGTH
import com.reposilite.console.StructuredRun
import com.reposilite.console.api.CommandListing
import com.reposilite.console.api.ConfirmationRequiredResponse
import com.reposilite.console.api.ExecutionResponse
import com.reposilite.console.api.StructuredExecutionResponse
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.console.consoleCommandAudit
import com.reposilite.shared.ErrorResponse
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.SESSION_COOKIE_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.openapi.OpenApiSecurity
import panda.std.asError
import panda.std.asSuccess
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.POST
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse

/** The exit code of a command held for confirmation. It is never run, so it is the code of the 409 answer. */
private const val CONFIRMATION_EXIT_CODE = 3

internal class ConsoleEndpoint(
    private val consoleFacade: ConsoleFacade,
    /** The instance id that a structured answer names as its node. */
    private val nodeId: String,
    /** Emits an audit event for every command that reaches execution from this channel. */
    private val audit: (AuditEvent) -> Unit,
) : ReposiliteRoutes() {

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/console/execute",
        methods = [HttpMethod.POST],
        summary = "Remote command execution",
        description = "Execute command using POST request. The commands are the same as in the console and can be listed using the 'help' command or '/api/console/commands'. " +
            "With 'structured=true' the answer carries an exit code and the output with its streams, and a dangerous command waits for 'confirm=true' (409 until then).",
        tags = ["Cli"],
        headers = [OpenApiParam(name = "Authorization", description = "Name and secret provided as basic auth credentials", required = true)],
        queryParams = [
            OpenApiParam(name = "structured", description = "Answer with exit code, output streams and node instead of the plain status and response lines", required = false),
            OpenApiParam(name = "confirm", description = "Confirms a dangerous command in structured mode", required = false),
        ],
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Status of the executed command (plain), or the structured answer when 'structured=true': exit code, node, and the output lines with their stream (stdout or stderr)",
                content = [OpenApiContent(from = ExecutionResponse::class)]
            ),
            OpenApiResponse(
                status = "400",
                description = "Error message related to the invalid command format (0 < command length < $MAX_COMMAND_LENGTH)",
                content = [OpenApiContent(from = ErrorResponse::class)]
            ),
            OpenApiResponse(
                status = "401",
                description = "Error message related to the unauthorized access",
                content = [OpenApiContent(from = ErrorResponse::class)]
            ),
            OpenApiResponse(
                status = "403",
                description = "Error message when the token has no console area (ingot.admin.console)",
                content = [OpenApiContent(from = ErrorResponse::class)]
            ),
            OpenApiResponse(
                status = "409",
                description = "Structured call of a dangerous command without 'confirm=true'. Nothing ran. Body: error 'confirmationRequired', command, reason",
                content = [OpenApiContent(from = ConfirmationRequiredResponse::class)]
            )
        ]
    )
    private val executeCommand = ReposiliteRoute<Any>("/api/console/execute", POST) {
        logger.info("REMOTE EXECUTION $uri from ${ctx.ip()}")

        authenticated {
            requireArea("console")
                .peek {
                    val structured = ctx.queryParam("structured") == "true"
                    val confirmed = ctx.queryParam("confirm") == "true"
                    consoleFacade
                        .prepareCommand(ctx.body())
                        .peek { logger.info("$name (${ctx.ip()}) requested command: ${it.redactedCommand}") }
                        .peek { prepared ->
                            fun record(exitCode: Int, confirmedCall: Boolean) =
                                audit(
                                    consoleCommandAudit(
                                        actor = AuditActor.named(name, ctx.ip()),
                                        command = prepared.redactedCommand,
                                        channel = "http",
                                        client = ctx.header("User-Agent"),
                                        confirmed = confirmedCall,
                                        exitCode = exitCode,
                                    )
                                )

                            if (!structured) {
                                val plain = prepared.executePlain()
                                record(plain.exitCode, confirmedCall = false)
                                response = plain.response.asSuccess()
                                return@peek
                            }
                            when (val answer = prepared.executeStructured(confirmed)) {
                                is StructuredRun -> {
                                    record(answer.execution.exitCode, confirmedCall = confirmed)
                                    response = StructuredExecutionResponse(
                                        status = answer.execution.status,
                                        exitCode = answer.execution.exitCode,
                                        output = answer.execution.output,
                                        data = answer.execution.data,
                                        node = nodeId,
                                    ).asSuccess()
                                }
                                // The 409 has its own body. Nothing ran, and no response is set, so the body written here stands alone.
                                is ConfirmationNeeded -> {
                                    record(CONFIRMATION_EXIT_CODE, confirmedCall = false)
                                    ctx.status(CONFLICT)
                                        .json(ConfirmationRequiredResponse("confirmationRequired", answer.command, answer.reason))
                                }
                            }
                        }
                        .onError { response = it.asError() }
                }
                .onError { response = it.asError() }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/console/commands",
        methods = [HttpMethod.GET],
        summary = "Console command listing",
        description = "The registered console commands with their aliases, options, parameters, and the 'dangerous' and 'sensitive' flags. Used for help and completion.",
        tags = ["Cli"],
        headers = [OpenApiParam(name = "Authorization", description = "Name and secret provided as basic auth credentials", required = true)],
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Command listing of the given version",
                content = [OpenApiContent(from = CommandListing::class)]
            ),
            OpenApiResponse(
                status = "401",
                description = "Error message related to the unauthorized access",
                content = [OpenApiContent(from = ErrorResponse::class)]
            ),
            OpenApiResponse(
                status = "403",
                description = "Error message when the token has no console area (ingot.admin.console)",
                content = [OpenApiContent(from = ErrorResponse::class)]
            )
        ]
    )
    private val listCommands = ReposiliteRoute<CommandListing>("/api/console/commands", GET) {
        authenticated {
            requireArea("console")
                .peek { response = consoleFacade.listCommands().asSuccess() }
                .onError { response = it.asError() }
        }
    }

    override val routes = routes(executeCommand, listCommands)

}
