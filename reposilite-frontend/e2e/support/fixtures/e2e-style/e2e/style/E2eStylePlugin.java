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
package e2e.style;

import com.reposilite.frontend.api.PluginUiFacade;
import com.reposilite.plugin.api.Facade;
import com.reposilite.plugin.api.Plugin;
import com.reposilite.plugin.api.ReposilitePlugin;

/**
 * The e2e fixture plugin: it ships dashboard.css and nothing else. The browser suite compiles it against the shaded
 * server jar (support/plugin-fixture.js). It is never part of a release.
 */
@Plugin(name = "e2e-style", dependencies = {"plugin-ui"})
public class E2eStylePlugin extends ReposilitePlugin {

    @Override
    public Facade initialize() {
        PluginUiFacade pluginUi = extensions().facade(PluginUiFacade.class);
        pluginUi.forPlugin(this).registerStylesheet("dashboard.css");
        return null;
    }

}
