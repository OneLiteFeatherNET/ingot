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

package net.onelitefeather.ingot.elasticsearch

/**
 * Removes the configured secrets and the user information of every URL from a text before the text leaves the plugin, in a
 * status, a problem body or a log line. A cluster may echo the credentials it was sent, so the text is never trusted.
 * Each secret is replaced by `***`, longest first, and blank secrets are ignored, because replacing an empty value would
 * change every character.
 */
internal class ElasticsearchRedactor(secrets: Collection<String>) {

    private val values: List<String> = secrets.filter { it.isNotBlank() }.sortedByDescending { it.length }

    fun redact(text: String): String {
        val withoutSecrets = values.fold(text) { shown, secret -> shown.replace(secret, MASK) }
        return withoutSecrets.replace(USER_INFORMATION, "://$MASK@")
    }

    private companion object {
        const val MASK = "***"
        val USER_INFORMATION = Regex("://[^/\\s]*@")
    }

}
