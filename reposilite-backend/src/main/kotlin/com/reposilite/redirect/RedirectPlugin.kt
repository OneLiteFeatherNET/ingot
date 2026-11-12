@file:OptIn(net.onelitefeather.ingot.format.ExperimentalIngotFormatApi::class)

package com.reposilite.redirect

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.storage.api.Location
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.HEAD
import net.onelitefeather.ingot.format.RepositoryRequest
import net.onelitefeather.ingot.format.RepositoryRouteDispatcher
import net.onelitefeather.ingot.repository.RepositoryRegistry

@Plugin(name = "redirect", dependencies = ["local-configuration", "frontend", "maven"])
class RedirectPlugin : ReposilitePlugin() {
    private val redirectTo: String? = System.getProperty("reposilite.redirect.default-repository", "")

    override fun initialize(): Facade? {
        if (redirectTo.isNullOrEmpty()) {
            return null
        }

        // The repository routes of the target format, the same ones that serve /{repository}/<gav>
        val repositoryRouteDispatcher = facade<RepositoryRouteDispatcher>()

        logger.info("")
        logger.info("--- Redirect")

        val redirectedRoutes = facade<RepositoryRegistry>().repository(redirectTo)
            ?.storageProvider
            ?.getFiles(Location.of("/"))
            ?.orNull()
            ?.map {
                logger.info("Redirecting /${it.getSimpleName()}/<gav> to /$redirectTo/${it.getSimpleName()}/<gav>")

                ReposiliteRoute<Unit>("/${it.getSimpleName()}/<gav>", HEAD, GET) {
                    accessed {
                        repositoryRouteDispatcher.dispatch(
                            RepositoryRequest(
                                ctx = ctx,
                                identifier = this?.identifier,
                                repository = redirectTo,
                                path = it.resolve(requireParameter("gav")),
                                by = getSessionIdentifier(),
                            )
                        )
                    }
                }
            }
            ?: emptyList()

        event { event: RoutingSetupEvent ->
            event.register(redirectedRoutes)
        }

        return null
    }

}
