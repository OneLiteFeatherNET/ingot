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

package net.onelitefeather.ingot.deploy

import com.reposilite.Reposilite
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.plugin.api.EventListener
import com.reposilite.plugin.api.Priorities
import com.reposilite.web.api.RoutingSetupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The routes an instance registers for the targets `all` and `server` are the routes of main. The expected lists were
 * captured from main before the worker target restricted any registration, one entry per method and path.
 */
internal abstract class RegisteredRoutesSnapshotTest(
    private val target: String,
) : ReposiliteSpecification() {

    private var registered: List<String> = emptyList()

    protected abstract val expected: List<String>

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.target = target
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        // Runs after every plugin has added its routes, so it sees the complete set the server registers.
        reposilite.extensions.registerEvent(object : EventListener<RoutingSetupEvent> {
            override fun priority(): Double = Priorities.LOW

            override fun onCall(event: RoutingSetupEvent) {
                registered = WorkerRoutes.restrict(event.getRoutes(), reposilite.parameters.deployTarget)
                    .flatMap { it.toDslRoutes() }
                    .map { "${it.method.name} ${it.path}" }
                    .distinct()
                    .sorted()
            }
        })
    }

    @Test
    fun `should register the same routes as main on the target`() {
        assertThat(registered)
            .describedAs("registered routes on target $target")
            .containsExactlyElementsOf(expected.sorted())
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RegisteredRoutesAllSnapshotTest : RegisteredRoutesSnapshotTest("all") {

    override val expected: List<String> = listOf(
        "AFTER /{repository}/<*>",
        "BEFORE /<*>",
        "BEFORE /api/*",
        "BEFORE /api/plugins/*",
        "DELETE /api/admin/read-only",
        "DELETE /api/identity/me/factors/{id}",
        "DELETE /api/identity/me/tokens/{name}",
        "DELETE /api/identity/principals/{name}",
        "DELETE /api/identity/principals/{name}/instance-admin",
        "DELETE /api/identity/principals/{name}/tokens/{tokenName}",
        "DELETE /api/identity/teams/{name}",
        "DELETE /api/identity/teams/{name}/instance-admin",
        "DELETE /api/status/failures",
        "DELETE /api/tokens/{name}",
        "DELETE /{repository}/<gav>",
        "GET /",
        "GET /access",
        "GET /access/<path>",
        "GET /account",
        "GET /analytics",
        "GET /api/admin/read-only",
        "GET /api/admin/support/availability",
        "GET /api/admin/support/bundle",
        "GET /api/admin/support/system-info",
        "GET /api/audit",
        "GET /api/audit/export",
        "GET /api/auth/me",
        "GET /api/auth/oidc/{id}/callback",
        "GET /api/auth/oidc/{id}/login",
        "GET /api/auth/providers",
        "GET /api/auth/setup",
        "GET /api/badge/latest/{repository}/<gav>",
        "GET /api/cleanup/runs",
        "GET /api/frontend/plugin-styles/{plugin}/{file}",
        "GET /api/frontend/plugin-ui",
        "GET /api/frontend/settings",
        "GET /api/identity/check",
        "GET /api/identity/me/access",
        "GET /api/identity/me/factors",
        "GET /api/identity/me/tokens",
        "GET /api/identity/nodes/catalog",
        "GET /api/identity/nodes/templates",
        "GET /api/identity/principals",
        "GET /api/identity/principals/{name}",
        "GET /api/identity/principals/{name}/nodes",
        "GET /api/identity/principals/{name}/tokens",
        "GET /api/identity/repositories/{repository}/access",
        "GET /api/identity/teams",
        "GET /api/identity/teams/{name}",
        "GET /api/identity/teams/{name}/nodes",
        "GET /api/identity/teams/{name}/parents",
        "GET /api/maven/details",
        "GET /api/maven/details/{repository}",
        "GET /api/maven/details/{repository}/<gav>",
        "GET /api/maven/latest/details/{repository}/<gav>",
        "GET /api/maven/latest/file/{repository}/<gav>",
        "GET /api/maven/latest/version/{repository}/<gav>",
        "GET /api/maven/repositories",
        "GET /api/maven/repositories/{repository}/members",
        "GET /api/maven/search",
        "GET /api/maven/search/files",
        "GET /api/maven/search/index",
        "GET /api/maven/storage",
        "GET /api/maven/versions/{repository}/<gav>",
        "GET /api/settings/domain/{name}",
        "GET /api/settings/domains",
        "GET /api/settings/encryption",
        "GET /api/settings/schema/{name}",
        "GET /api/statistics/resolved/all",
        "GET /api/statistics/resolved/phrase/{limit}/{repository}/<gav>",
        "GET /api/statistics/resolved/unique",
        "GET /api/status/failures",
        "GET /api/status/health",
        "GET /api/status/instance",
        "GET /api/status/ready",
        "GET /api/status/snapshots",
        "GET /api/tokens",
        "GET /api/tokens/{name}",
        "GET /assets/<path>",
        "GET /audit",
        "GET /browse",
        "GET /browse/<path>",
        "GET /console",
        "GET /dashboard",
        "GET /favicon.png",
        "GET /index.html",
        "GET /javadoc/{repository}/<gav>",
        "GET /javadoc/{repository}/<gav>/raw/<resource>",
        "GET /maintenance",
        "GET /search",
        "GET /settings",
        "GET /settings/<path>",
        "GET /tokens",
        "GET /tokens/{name}",
        "GET /{repository}/<gav>",
        "HEAD /",
        "HEAD /access",
        "HEAD /access/<path>",
        "HEAD /account",
        "HEAD /analytics",
        "HEAD /audit",
        "HEAD /browse",
        "HEAD /browse/<path>",
        "HEAD /console",
        "HEAD /dashboard",
        "HEAD /index.html",
        "HEAD /maintenance",
        "HEAD /search",
        "HEAD /settings",
        "HEAD /settings/<path>",
        "HEAD /tokens",
        "HEAD /tokens/{name}",
        "HEAD /{repository}/<gav>",
        "PATCH /api/identity/principals/{name}",
        "PATCH /api/identity/teams/{name}",
        "PATCH /api/tokens/{name}",
        "POST /api/admin/read-only",
        "POST /api/auth/login",
        "POST /api/auth/logout",
        "POST /api/auth/mfa/verify",
        "POST /api/auth/providers/ldap/test",
        "POST /api/auth/providers/oidc/{id}/test",
        "POST /api/auth/refresh",
        "POST /api/auth/setup",
        "POST /api/cleanup/policies/{id}/dry-run",
        "POST /api/cleanup/policies/{id}/run",
        "POST /api/console/execute",
        "POST /api/database/snapshots",
        "POST /api/identity/me/factors/totp",
        "POST /api/identity/me/factors/totp/confirm",
        "POST /api/identity/me/password",
        "POST /api/identity/me/recovery-codes",
        "POST /api/identity/me/tokens",
        "POST /api/identity/me/tokens/{name}/rotate",
        "POST /api/identity/principals",
        "POST /api/identity/principals/{name}/password",
        "POST /api/identity/principals/{name}/tokens",
        "POST /api/identity/teams",
        "POST /api/maven/generate/pom/{repository}/<gav>",
        "POST /api/maven/search/index/rebuild",
        "POST /api/maven/search/index/runs/{id}/cancel",
        "POST /api/maven/search/index/verify",
        "POST /api/maven/storage/refresh",
        "POST /api/settings/export",
        "POST /api/settings/import/open",
        "POST /api/tokens/{name}/secret",
        "POST /{repository}/<gav>",
        "PUT /api/identity/principals/{name}/instance-admin",
        "PUT /api/identity/principals/{name}/nodes",
        "PUT /api/identity/teams/{name}/grants",
        "PUT /api/identity/teams/{name}/instance-admin",
        "PUT /api/identity/teams/{name}/members",
        "PUT /api/identity/teams/{name}/nodes",
        "PUT /api/identity/teams/{name}/parents",
        "PUT /api/settings/domain/{name}",
        "PUT /api/tokens/{name}",
        "PUT /{repository}/<gav>",
    )

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RegisteredRoutesServerSnapshotTest : RegisteredRoutesSnapshotTest("server") {

    override val expected: List<String> = listOf(
        "AFTER /{repository}/<*>",
        "BEFORE /<*>",
        "BEFORE /api/*",
        "BEFORE /api/plugins/*",
        "DELETE /api/admin/read-only",
        "DELETE /api/identity/me/factors/{id}",
        "DELETE /api/identity/me/tokens/{name}",
        "DELETE /api/identity/principals/{name}",
        "DELETE /api/identity/principals/{name}/instance-admin",
        "DELETE /api/identity/principals/{name}/tokens/{tokenName}",
        "DELETE /api/identity/teams/{name}",
        "DELETE /api/identity/teams/{name}/instance-admin",
        "DELETE /api/status/failures",
        "DELETE /api/tokens/{name}",
        "DELETE /{repository}/<gav>",
        "GET /",
        "GET /api/admin/read-only",
        "GET /api/admin/support/availability",
        "GET /api/admin/support/bundle",
        "GET /api/admin/support/system-info",
        "GET /api/audit",
        "GET /api/audit/export",
        "GET /api/auth/me",
        "GET /api/auth/oidc/{id}/callback",
        "GET /api/auth/oidc/{id}/login",
        "GET /api/auth/providers",
        "GET /api/auth/setup",
        "GET /api/badge/latest/{repository}/<gav>",
        "GET /api/cleanup/runs",
        "GET /api/frontend/plugin-styles/{plugin}/{file}",
        "GET /api/frontend/plugin-ui",
        "GET /api/frontend/settings",
        "GET /api/identity/check",
        "GET /api/identity/me/access",
        "GET /api/identity/me/factors",
        "GET /api/identity/me/tokens",
        "GET /api/identity/nodes/catalog",
        "GET /api/identity/nodes/templates",
        "GET /api/identity/principals",
        "GET /api/identity/principals/{name}",
        "GET /api/identity/principals/{name}/nodes",
        "GET /api/identity/principals/{name}/tokens",
        "GET /api/identity/repositories/{repository}/access",
        "GET /api/identity/teams",
        "GET /api/identity/teams/{name}",
        "GET /api/identity/teams/{name}/nodes",
        "GET /api/identity/teams/{name}/parents",
        "GET /api/maven/details",
        "GET /api/maven/details/{repository}",
        "GET /api/maven/details/{repository}/<gav>",
        "GET /api/maven/latest/details/{repository}/<gav>",
        "GET /api/maven/latest/file/{repository}/<gav>",
        "GET /api/maven/latest/version/{repository}/<gav>",
        "GET /api/maven/repositories",
        "GET /api/maven/repositories/{repository}/members",
        "GET /api/maven/search",
        "GET /api/maven/search/files",
        "GET /api/maven/search/index",
        "GET /api/maven/storage",
        "GET /api/maven/versions/{repository}/<gav>",
        "GET /api/settings/domain/{name}",
        "GET /api/settings/domains",
        "GET /api/settings/encryption",
        "GET /api/settings/schema/{name}",
        "GET /api/statistics/resolved/all",
        "GET /api/statistics/resolved/phrase/{limit}/{repository}/<gav>",
        "GET /api/statistics/resolved/unique",
        "GET /api/status/failures",
        "GET /api/status/health",
        "GET /api/status/instance",
        "GET /api/status/ready",
        "GET /api/status/snapshots",
        "GET /api/tokens",
        "GET /api/tokens/{name}",
        "GET /favicon.png",
        "GET /index.html",
        "GET /javadoc/{repository}/<gav>",
        "GET /javadoc/{repository}/<gav>/raw/<resource>",
        "GET /{repository}/<gav>",
        "HEAD /{repository}/<gav>",
        "PATCH /api/identity/principals/{name}",
        "PATCH /api/identity/teams/{name}",
        "PATCH /api/tokens/{name}",
        "POST /api/admin/read-only",
        "POST /api/auth/login",
        "POST /api/auth/logout",
        "POST /api/auth/mfa/verify",
        "POST /api/auth/providers/ldap/test",
        "POST /api/auth/providers/oidc/{id}/test",
        "POST /api/auth/refresh",
        "POST /api/auth/setup",
        "POST /api/cleanup/policies/{id}/dry-run",
        "POST /api/cleanup/policies/{id}/run",
        "POST /api/console/execute",
        "POST /api/database/snapshots",
        "POST /api/identity/me/factors/totp",
        "POST /api/identity/me/factors/totp/confirm",
        "POST /api/identity/me/password",
        "POST /api/identity/me/recovery-codes",
        "POST /api/identity/me/tokens",
        "POST /api/identity/me/tokens/{name}/rotate",
        "POST /api/identity/principals",
        "POST /api/identity/principals/{name}/password",
        "POST /api/identity/principals/{name}/tokens",
        "POST /api/identity/teams",
        "POST /api/maven/generate/pom/{repository}/<gav>",
        "POST /api/maven/search/index/rebuild",
        "POST /api/maven/search/index/runs/{id}/cancel",
        "POST /api/maven/search/index/verify",
        "POST /api/maven/storage/refresh",
        "POST /api/settings/export",
        "POST /api/settings/import/open",
        "POST /api/tokens/{name}/secret",
        "POST /{repository}/<gav>",
        "PUT /api/identity/principals/{name}/instance-admin",
        "PUT /api/identity/principals/{name}/nodes",
        "PUT /api/identity/teams/{name}/grants",
        "PUT /api/identity/teams/{name}/instance-admin",
        "PUT /api/identity/teams/{name}/members",
        "PUT /api/identity/teams/{name}/nodes",
        "PUT /api/identity/teams/{name}/parents",
        "PUT /api/settings/domain/{name}",
        "PUT /api/tokens/{name}",
        "PUT /{repository}/<gav>",
    )

}
