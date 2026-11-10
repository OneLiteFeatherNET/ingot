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

package net.onelitefeather.ingot.support

import com.reposilite.audit.api.auditActor
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.http.Header
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess
import java.io.InputStream

/** Whether the system information and the support bundle are offered on this instance. */
data class SupportAvailability(val enabled: Boolean)

/**
 * The support routes. All of them need the console area, and all of them are reads, so read-only mode does not refuse them. With the
 * opt-out on, the two data routes answer 404 before anything is read, and the availability route says so.
 */
internal class SupportEndpoints(
    private val service: SupportBundleService,
    private val setting: SupportBundleSetting,
) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/admin/support/availability",
        methods = [HttpMethod.GET],
        summary = "Tells whether the system information and the support bundle are offered. Note: Requires the console area",
        tags = ["Support"],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = SupportAvailability::class)]),
        ]
    )
    private val getAvailability = ReposiliteRoute<SupportAvailability>("/api/admin/support/availability", GET) {
        managerOnly("console") {
            response = SupportAvailability(enabled = setting.enabled).asSuccess()
        }
    }

    @OpenApi(
        path = "/api/admin/support/system-info",
        methods = [HttpMethod.GET],
        summary = "Returns the system information of this instance, with every secret withheld or masked. Note: Requires the console area",
        tags = ["Support"],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = SystemInformation::class)]),
            OpenApiResponse(status = "401", description = "No credentials were sent"),
            OpenApiResponse(status = "403", description = "The principal has no console area"),
            OpenApiResponse(status = "404", description = "The support bundle is turned off on this instance"),
            OpenApiResponse(status = "500", description = "The final redaction check refused the answer"),
        ]
    )
    private val getSystemInformation = ReposiliteRoute<SystemInformation>("/api/admin/support/system-info", GET) {
        if (!setting.enabled) {
            response = disabledResponse()
        } else {
            managerOnly("console") {
                ctx.header(Header.CACHE_CONTROL, "no-store")
                response = guarded { service.systemInformation() }
            }
        }
    }

    @OpenApi(
        path = "/api/admin/support/bundle",
        methods = [HttpMethod.GET],
        summary = "Downloads a zip with the system information, the configuration, the cluster, the plugins and the cached log of this instance. Note: Requires the console area",
        tags = ["Support"],
        responses = [
            OpenApiResponse(status = "200", description = "The support bundle as application/zip"),
            OpenApiResponse(status = "401", description = "No credentials were sent"),
            OpenApiResponse(status = "403", description = "The principal has no console area"),
            OpenApiResponse(status = "404", description = "The support bundle is turned off on this instance"),
            OpenApiResponse(status = "429", description = "Another bundle is being built"),
            OpenApiResponse(status = "500", description = "The final redaction check refused the bundle, or it exceeds the size cap"),
        ]
    )
    private val getBundle = ReposiliteRoute<InputStream>("/api/admin/support/bundle", GET) {
        if (!setting.enabled) {
            response = disabledResponse()
        } else {
            managerOnly("console") {
                ctx.header(Header.CACHE_CONTROL, "no-store")
                response = guarded { service.bundle(auditActor()) }.map { bundle ->
                    ctx.contentType("application/zip")
                    ctx.header(Header.CONTENT_DISPOSITION, "attachment; filename=\"${bundle.fileName}\"")
                    bundle.bytes.inputStream()
                }
            }
        }
    }

    override val routes = routes(getAvailability, getSystemInformation, getBundle)

}

private fun disabledResponse(): Result<Nothing, ErrorResponse> =
    NOT_FOUND.toErrorResponse("The support bundle is turned off on this instance").asError()

/** Runs [action] and maps its refusals to their answers. The messages never carry a secret value. */
private fun <T> guarded(action: () -> T): Result<T, ErrorResponse> =
    try {
        Result.ok(action())
    } catch (exception: RedactionRefusedException) {
        Result.error(INTERNAL_SERVER_ERROR.toErrorResponse(exception.message))
    } catch (exception: BundleBusyException) {
        Result.error(TOO_MANY_REQUESTS.toErrorResponse(exception.message))
    } catch (exception: BundleTooLargeException) {
        Result.error(INTERNAL_SERVER_ERROR.toErrorResponse(exception.message))
    }
