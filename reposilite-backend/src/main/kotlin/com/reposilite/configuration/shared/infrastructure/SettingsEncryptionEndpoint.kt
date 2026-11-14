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

import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.SESSION_COOKIE_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.community.routing.Route.GET
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import panda.std.asSuccess

/**
 * What the settings area needs to know about encryption of the shared settings. It never holds a value or a key.
 *
 * @property mode the value of `INGOT_SETTINGS_ENCRYPTION`, `optional` or `required`
 * @property secretsEncrypted true when a key is active, so new secret values are written sealed
 * @property plaintextSecrets the number of stored secret values that are still plain text
 */
data class SettingsEncryptionState(
    val mode: String,
    val secretsEncrypted: Boolean,
    val plaintextSecrets: Int,
)

/**
 * Reports the encryption state to the settings area only. The dashboard shows a warning from it while secrets are stored in clear.
 */
internal class SettingsEncryptionEndpoint(
    private val sharedConfigurationFacade: SharedConfigurationFacade,
    private val cipher: SettingsSecretCipher,
) : ReposiliteRoutes() {

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/settings/encryption",
        methods = [HttpMethod.GET],
        tags = ["Settings"],
        summary = "Get the settings encryption state",
        description = "Reports whether a settings key is active, the configured mode and the number of stored secret values that are still plain text. No value is returned.",
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Encryption state of the shared settings",
                content = [OpenApiContent(from = SettingsEncryptionState::class)]
            ),
            OpenApiResponse(status = "401", description = "Returns 401 without credentials"),
            OpenApiResponse(status = "403", description = "Returns 403 if a token without the settings area (ingot.admin.settings) is used"),
        ]
    )
    private val findEncryptionState = ReposiliteRoute<SettingsEncryptionState>("/api/settings/encryption", GET) {
        managerOnly("settings") {
            response = SettingsEncryptionState(
                mode = cipher.mode.wireName,
                secretsEncrypted = cipher.sealing,
                plaintextSecrets = sharedConfigurationFacade.plaintextSecretCount(),
            ).asSuccess()
        }
    }

    override val routes = routes(findEncryptionState)

}
