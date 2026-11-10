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

package com.reposilite.maven

import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.frontend.FrontendFacade
import com.reposilite.maven.infrastructure.MavenEndpoints
import com.reposilite.maven.infrastructure.MavenRepositoryRequestHandler
import net.onelitefeather.ingot.format.ExperimentalIngotFormatApi
import net.onelitefeather.ingot.format.FormatContext
import net.onelitefeather.ingot.format.FormatMount
import net.onelitefeather.ingot.format.PathRules
import net.onelitefeather.ingot.format.RepositoryFormat
import net.onelitefeather.ingot.format.RepositoryFormats
import net.onelitefeather.ingot.format.RepositoryRequestHandler

/**
 * The Maven 2 layout, the format of every repository that names none. Found through the service file of the format SPI, so it
 * is a public class with a public constructor.
 */
@ExperimentalIngotFormatApi
class Maven2Format : RepositoryFormat {

    override val id: String = RepositoryFormats.DEFAULT_ID

    override val pathRules: PathRules = MavenPathRules

    override val mount: FormatMount = FormatMount.RepositoryPath

    override fun handler(context: FormatContext): RepositoryRequestHandler =
        MavenRepositoryRequestHandler(
            MavenEndpoints(
                mavenFacade = context.facade(MavenFacade::class.java),
                frontendFacade = context.facade(FrontendFacade::class.java),
                compressionStrategy = context.facade(LocalConfiguration::class.java).compressionStrategy.get(),
            )
        )

}
