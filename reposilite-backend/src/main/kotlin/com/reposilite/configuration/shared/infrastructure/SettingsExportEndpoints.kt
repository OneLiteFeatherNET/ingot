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

package com.reposilite.configuration.shared.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.api.auditActor
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.badRequestError
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.community.routing.Route.POST
import io.javalin.http.Header
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import net.onelitefeather.ingot.settings.export.SettingsExportCodec
import net.onelitefeather.ingot.settings.export.SettingsExportException
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The password protected export and import of the shared settings. The export is made from the saved state, never from the
 * dashboard's draft. The import only decrypts: the dashboard then applies the result to its draft, and the ordinary save stores it.
 */
internal class SettingsExportEndpoints(
    private val sharedConfigurationFacade: SharedConfigurationFacade,
    private val codec: SettingsExportCodec,
    private val clock: Clock,
    private val audit: (AuditEvent) -> Unit,
) : ReposiliteRoutes() {

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME)],
        path = "/api/settings/export",
        methods = [HttpMethod.POST],
        tags = ["Settings"],
        summary = "Export settings",
        description = "Returns the saved settings encrypted with a password (Argon2id and AES-256-GCM). The password is at least 12 characters.",
        responses = [
            OpenApiResponse(status = "200", description = "Encrypted settings file", content = [OpenApiContent(from = Any::class)]),
            OpenApiResponse(status = "400", description = "The password is missing or shorter than 12 characters"),
            OpenApiResponse(status = "403", description = "A token without the settings area (ingot.admin.settings) is used"),
        ]
    )
    private val exportSettings = ReposiliteRoute<Any>("/api/settings/export", POST) {
        managerOnly("settings") {
            val result = export(ctx.body(), auditActor())
            if (result.isOk) {
                ctx.header(Header.CONTENT_DISPOSITION, "attachment; filename=\"ingot-settings-${clock.instant().atZone(ZoneOffset.UTC).format(DATE)}.json\"")
                ctx.header(Header.CACHE_CONTROL, "no-store")
            }
            response = result
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME)],
        path = "/api/settings/import/open",
        methods = [HttpMethod.POST],
        tags = ["Settings"],
        summary = "Open an encrypted settings file",
        description = "Decrypts an export with its password and returns the plain settings for review. Nothing is stored.",
        responses = [
            OpenApiResponse(status = "200", description = "Plain settings, for review"),
            OpenApiResponse(status = "400", description = "The password is wrong or the file was changed"),
            OpenApiResponse(status = "403", description = "A token without the settings area (ingot.admin.settings) is used"),
        ]
    )
    private val openImport = ReposiliteRoute<Any>("/api/settings/import/open", POST) {
        managerOnly("settings") {
            response = open(ctx.body(), auditActor())
        }
    }

    private fun export(body: String, actor: AuditActor): Result<Any, ErrorResponse> {
        val password = passwordOf(body) ?: return badRequest("A password is required to export the settings").asError()
        if (password.length < SettingsExportCodec.MIN_PASSWORD_LENGTH) {
            return badRequest("The export password must be at least ${SettingsExportCodec.MIN_PASSWORD_LENGTH} characters long").asError()
        }

        val plain = sharedConfigurationFacade.plainDocument()
        val file = runCatching { codec.seal(plain, password, clock.instant().toString()) }
            .getOrElse { return badRequest(it.message ?: "The settings cannot be exported").asError() }
        val domains = DEFAULT_OBJECT_MAPPER.readTree(plain).size()
        audit(AuditEvent(action = AuditAction.SETTINGS_EXPORTED, actor = actor, details = mapOf("domains" to domains)))
        return file.asSuccess()
    }

    private fun open(body: String, actor: AuditActor): Result<Any, ErrorResponse> {
        val request = runCatching { DEFAULT_OBJECT_MAPPER.readTree(body) }.getOrNull()
            ?: return badRequestError("Body is not a valid JSON document")
        val password = request.path("password").asText()
        val export = request.path("export")
        if (password.isEmpty() || export.isMissingNode) {
            audit(AuditEvent(action = AuditAction.SETTINGS_IMPORT_REFUSED, actor = actor, details = mapOf("reason" to SettingsExportCodec.REASON_FILE), success = false))
            return badRequest(SettingsExportCodec.REFUSED_MESSAGE).asError()
        }

        return try {
            val plain = codec.open(export, password)
            val document: JsonNode = DEFAULT_OBJECT_MAPPER.readTree(plain)
            audit(AuditEvent(action = AuditAction.SETTINGS_IMPORT_DECRYPTED, actor = actor, details = mapOf("domains" to document.size())))
            document.asSuccess()
        } catch (exception: SettingsExportException) {
            audit(AuditEvent(action = AuditAction.SETTINGS_IMPORT_REFUSED, actor = actor, details = mapOf("reason" to exception.reason), success = false))
            badRequest(exception.message).asError()
        }
    }

    private fun passwordOf(body: String): String? =
        runCatching { DEFAULT_OBJECT_MAPPER.readTree(body).path("password").asText() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    override val routes = routes(exportSettings, openImport)

}
