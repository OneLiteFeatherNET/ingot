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

package com.reposilite.configuration.shared.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.SettingsSecrets
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.SharedSettingsReference
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.badRequestError
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.PUT
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess
import panda.std.reactive.Reference
import java.io.InputStream

/**
 * @param hideSecrets when enabled, the settings API replaces every secret with a placeholder on read and keeps the stored
 * value when the placeholder is sent back, see [SettingsSecrets]
 */
internal class SettingsEndpoints(
    private val sharedConfigurationFacade: SharedConfigurationFacade,
    private val hideSecrets: Reference<Boolean>,
) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/settings/domains",
        methods = [HttpMethod.GET],
        tags = ["Settings"],
        summary = "List configurations",
        responses = [
            OpenApiResponse(status = "200", description = "Returns list of configuration names"),
            OpenApiResponse(status = "401", description = "Returns 401 if token without moderation permission has been used to access this resource")
        ]
    )
    private val listConfigurations = ReposiliteRoute<Collection<String>>("/api/settings/domains", GET) {
        managerOnly {
            response = sharedConfigurationFacade.getDomainNames().asSuccess()
        }
    }

    @OpenApi(
        path = "/api/settings/schema/{name}",
        methods = [HttpMethod.GET],
        tags = ["Settings"],
        summary = "Get schema",
        pathParams = [OpenApiParam(name = "name", description = "Name of schema to get", required = true)],
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Returns dto representing configuration schema",
                content = [OpenApiContent(from = String::class)]
            ),
            OpenApiResponse(
                status = "401",
                description = "Returns 401 if token without moderation permission has been used to access this resource"
            ),
            OpenApiResponse(status = "404", description = "Returns 404 if non-existing configuration schema is requested")
        ]
    )
    private val getSchema = ReposiliteRoute<InputStream>("/api/settings/schema/{name}", GET) {
        managerOnly {
            response = sharedConfigurationFacade.getSettingsReference<SharedSettings>(requireParameter("name"))
                ?.schema
                ?.get()
                ?.asSuccess()
        }
    }

    @OpenApi(
        path = "/api/settings/domain/{name}",
        methods = [HttpMethod.GET],
        tags = ["Settings"],
        summary = "Find configuration",
        pathParams = [OpenApiParam(name = "name", description = "Name of configuration to fetch", required = true)],
        responses = [
            OpenApiResponse(status = "200", description = "Returns dto representing configuration"),
            OpenApiResponse(status = "401", description = "Returns 401 if token without moderation permission has been used to access this resource"),
            OpenApiResponse(status = "404", description = "Returns 404 if non-existing configuration is requested")
        ]
    )
    private val getConfiguration = ReposiliteRoute<Any>("/api/settings/domain/{name}", GET) {
        managerOnly {
            response = sharedConfigurationFacade.getSettingsReference<SharedSettings>(requireParameter("name"))
                ?.let { reference ->
                    val settings = reference.get()
                    if (hideSecrets.get()) {
                        SettingsSecrets.mask(reference.type, DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(settings))
                    } else {
                        settings
                    }
                }
                ?.asSuccess()
        }
    }

    @OpenApi(
        path = "/api/settings/domain/{name}",
        methods = [HttpMethod.PUT],
        tags = ["Settings"],
        summary = "Update configuration",
        pathParams = [OpenApiParam(name = "name", description = "Name of configuration to update", required = true)],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if configuration has been updated successfully"),
            OpenApiResponse(
                status = "401",
                description = "Returns 401 if token without moderation permission has been used to access this resource"
            ),
            OpenApiResponse(status = "404", description = "Returns 404 if non-existing configuration is requested")
        ]
    )
    private val updateConfiguration = ReposiliteRoute<Any>("/api/settings/domain/{name}", PUT) {
        managerOnly {
            with(requireParameter("name")) {
                response = ctx.body()
                    .takeIf { it.isNotEmpty() }
                    ?.let { sharedConfigurationFacade.getSettingsReference<SharedSettings>(this) }
                    ?.let { reference ->
                        if (hideSecrets.get()) {
                            updateHidingSecrets(this, reference, ctx.body())
                        } else {
                            sharedConfigurationFacade.updateSharedSettings(this, ctx.bodyAsClass(reference.type))
                                ?.mapErr { badRequest(it.toString()) }
                        }
                    }
                    ?: badRequestError("Body is empty")
            }
        }
    }

    /**
     * Restores the placeholders of the incoming document from the stored settings before the update. A placeholder without a stored
     * counterpart is rejected instead of being written as a literal value.
     */
    private fun updateHidingSecrets(
        name: String,
        reference: SharedSettingsReference<SharedSettings>,
        body: String
    ): Result<out Any, ErrorResponse> {
        val incoming = runCatching { DEFAULT_OBJECT_MAPPER.readTree(body) }.getOrNull()
            ?: return badRequest("Body is not a valid JSON document").asError()

        val restored = SettingsSecrets.restore(
            type = reference.type,
            incoming = incoming,
            stored = DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(reference.get())
        )
        if (restored.isErr) {
            return badRequest(restored.error).asError()
        }

        val settings = runCatching { DEFAULT_OBJECT_MAPPER.treeToValue(restored.get(), reference.type) as SharedSettings }
            .getOrElse { return badRequest("Body does not match the settings of '$name': ${it.message}").asError() }

        return sharedConfigurationFacade.updateSharedSettings(name, settings)
            ?.mapErr { badRequest(it.toString()) }
            ?: badRequestError("Unknown settings domain '$name'")
    }

    override val routes = routes(listConfigurations, getConfiguration, updateConfiguration, getSchema)

}
