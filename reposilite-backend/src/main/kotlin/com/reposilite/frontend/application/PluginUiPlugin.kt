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
package com.reposilite.frontend.application

import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.frontend.infrastructure.PluginStylesheetEndpoint
import com.reposilite.frontend.infrastructure.PluginUiDataGuard
import com.reposilite.frontend.infrastructure.PluginUiEndpoint
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.web.api.RoutingSetupEvent

/**
 * The `plugin-ui` facade: the extension point through which server plugins contribute dashboard pages as data.
 * The switch `INGOT_LOCAL_PLUGINUI` (fallback `REPOSILITE_LOCAL_PLUGINUI`) turns the contributions off.
 * The switch `INGOT_LOCAL_PLUGINSTYLES` (fallback `REPOSILITE_LOCAL_PLUGINSTYLES`) turns the plugin stylesheets off.
 */
@Plugin(name = "plugin-ui", dependencies = ["local-configuration"])
internal class PluginUiPlugin : ReposilitePlugin() {

    override fun initialize(): PluginUiFacade {
        val pluginUi = facade<LocalConfiguration>().pluginUi
        val pluginStyles = facade<LocalConfiguration>().pluginStyles
        val facade = PluginUiFacade(logger = logger, enabled = { pluginUi.get() }, stylesheetsEnabled = { pluginStyles.get() })

        event { event: RoutingSetupEvent ->
            // The manifest is served whatever the switch says, so the dashboard learns the enabled plugins either way.
            event.registerRoutes(
                PluginUiEndpoint(
                    facade = facade,
                    enabledPlugins = { event.reposilite.extensions.getPlugins().keys.sorted() },
                )
            )
            // The stylesheet path answers 404 while its switch is off, so it is registered whatever the switch says.
            event.registerRoutes(PluginStylesheetEndpoint(facade))
            if (pluginUi.get()) {
                event.registerRoutes(PluginUiDataGuard(facade))
            }
        }

        return facade
    }

}
