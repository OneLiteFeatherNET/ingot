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

package net.onelitefeather.ingot.cdn.purge

import com.reposilite.maven.api.DeleteEvent
import com.reposilite.maven.api.DeployEvent
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event

@Plugin(name = "cdn-purge", tier = PluginTier.COMMUNITY)
class CdnPurgePlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val settings = CdnPurgeSettings.read(System.getenv())
        if (settings.purgeUrl == null) {
            logger.info("CDN purge is off, set INGOT_CDN_PURGE_URL to purge the URLs of changed files")
            return null
        }

        val listener = CdnPurgeListener(settings, HttpPurgeTransport()) { message, exception ->
            logger.error("$message: ${exception.message}")
        }
        event { event: DeployEvent -> listener.purge(event.repository.name, event.gav.toString()) }
        event { event: DeleteEvent -> listener.purge(event.repository.name, event.gav.toString()) }
        logger.info("CDN purge is on, deploys and deletes purge their public URLs")
        return null
    }

}
