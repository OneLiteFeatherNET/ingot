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

package net.onelitefeather.ingot.web

/** Skeleton: the trusted proxy list is not honoured yet. */
class TrustedProxies private constructor() {

    val enabled: Boolean
        get() = false

    fun resolve(peer: String, forwardedValue: String?): String = forwardedValue ?: peer

    companion object {

        const val ENVIRONMENT_VARIABLE = "INGOT_TRUSTED_PROXIES"
        const val SYSTEM_PROPERTY = "ingot.trustedProxies"

        val NONE = TrustedProxies()

        fun parse(raw: String?): TrustedProxies = NONE

        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
            properties: java.util.Properties = System.getProperties(),
        ): TrustedProxies = NONE

        fun startupWarning(trustedProxies: TrustedProxies, forwardedHeader: String?): String? = null

    }

}
